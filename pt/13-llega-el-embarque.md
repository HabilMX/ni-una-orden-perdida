# Lição 13 — Chega o embarque

> **O que será alcançado:** receber o arquivo que o armazém deixa quando um pedido sai, sem pegá-lo pela metade, sem
> pegá-lo duas vezes e sem perder o que chega quebrado. Esse arquivo vira um 856 para o parceiro e o status
> EMBARCADO na API e no ERP.

**Antes de começar é preciso ter:** a lição 12 concluída e o ambiente da 00 ligado. Esta lição usa duas
tabelas novas do ERP, `embarque` e `entrega`. Se o ERP for de antes, recrie-o uma vez:

```bash
docker compose -f infra/compose.yaml up -d --wait --build --force-recreate -V
```

## A novidade de hoje, em uma frase

Até aqui, o pedido **chegava** à integração: a loja chamava o router. Com o embarque é o contrário: o
armazém não chama ninguém, **deixa um arquivo em uma pasta** e vai embora. Alguém tem de ir ver se há algo novo.
Isso se chama sondar (*Polling Consumer*), e hoje é configurado.

**A imagem:** a caixa de correio de um prédio. O carteiro deixa as cartas e não toca a campainha. O zelador desce de vez em quando
para conferir, leva só os envelopes fechados (não a carta que alguém ainda está escrevendo na mesa), separa o que
já entregou e põe de lado o que chegou rasgado. E há **um único zelador**: se fossem três, dois poderiam subir com a
mesma carta.

Esta é a parte 2: o pedido já foi criado; agora ele sai do armazém (hoje), é dividido em vários (14), tem o
estado guardado (15) e chega ao cliente (16 e 17).

---

## 1. Prepare a pasta

```bash
cd lecciones/13-llega-el-embarque
cp -R inicio mio
```

Hoje há um serviço novo, `sondeo-bodega`. Veja a pasta `buzon/` da lição, que no `compose.yaml` é
montada dentro do contêiner da sondagem. Todo o trabalho vai em **um único arquivo**:
`mio/sondeo-bodega/sondeo-bodega.camel.yaml`, e dentro dele, só nos parâmetros do `from`.

Abra também um embarque de exemplo, `datos/embarques/embarque-4001.json`: traz o pedido, a transportadora, o
**código de rastreio** (o número com que a transportadora acompanha o pacote) e quais linhas do pedido saíram.

## 2. Faça a previsão

Responda em um papel antes de rodar qualquer coisa:

1. Na 12 aprendeu-se a rodar três réplicas de um serviço. Se três réplicas conferissem a mesma pasta, o que poderia
   acontecer com um arquivo?
2. O armazém escreve o arquivo aos poucos. O que acontece se a sondagem o pega quando está pela metade?
3. Chega um arquivo quebrado, que não é JSON válido. O que deveria acontecer com ele: ser apagado, ficar onde está ou
   ser separado?

Veja como está hoje:

```bash
./verifica inicio
```

Várias marcas saem em vermelho. Leia-as: cada uma diz que cuidado falta à sondagem. A sondagem de `inicio/` lê
tudo o que há em `buzon/` e não separa nada (`noop: true` deixa cada arquivo onde estava).

## 3. O que já está feito: os canais são reutilizados

Antes de escrever, veja o que não é preciso escrever. Abra `mio/orden-router/orden-router.camel.yaml` e procure
`platform-http:/embarques`. É a porta de entrada por onde a sondagem entrega cada embarque ao router. Mais abaixo está o
mesmo `multicast` da 10, com os mesmos três canais: `direct:canal-api`, `direct:canal-erp` e
`direct:canal-socio`. A única diferença é o **passo**: antes cada canal chamava `/integrar`; agora chama
`/embarque`.

| Canal | Serviço | O que faz com o embarque |
|---|---|---|
| API | int-rest | Conta à API que o pedido foi embarcado, como um evento com a chave dele (`orden-4001-embarcada`) |
| ERP | int-bd | Escreve a linha de `embarque` com o código de rastreio e passa o pedido para EMBARCADO |
| Parceiro | int-edi | Monta um **856**, o aviso de embarque do X12, e o deixa no SFTP com os cuidados da 07 |

E a regra da 11 continua: só as franquias recebem EDI. Por isso hoje os pedidos são das lojas T-501 e
T-502.

## 4. Os quatro cuidados da sondagem

O componente `file` do Camel já sabe sondar. O trabalho é dizer a ele **que cuidados ter**, com quatro parâmetros.
Cada TODO do arquivo diz qual:

| TODO | Parâmetro | O cuidado | Da caixa de correio do prédio |
|---|---|---|---|
| 1 | `include` | Pegar só os arquivos terminados: `embarque-<algo>.json`. O `.tmp` não coincide | Só os envelopes fechados |
| 2 | `readLock` com `changed` | Esperar o arquivo parar de crescer antes de pegá-lo | Esperar o carteiro soltar a carta |
| 3 | `move` e `moveFailed` (e tirar o `noop`) | Separar o que o router recebeu em `.camel/` e o que falhou em `.error/` | O entregue de um lado, o rasgado de outro |
| 4 | `delay` | De quanto em quanto tempo conferir a pasta: a cada 2 segundos, isto é, 2000 milissegundos | De quanto em quanto tempo o zelador desce |

Duas dicas:

- `include` é uma **expressão regular** sobre o nome do arquivo. O ponto de `.json` tem de ir escapado, e em
  YAML entre aspas duplas a barra é escrita duas vezes: `\\.`
- Para o nome do arquivo separado use `${file:name}`: assim `embarque-4001.json` fica como
  `.camel/embarque-4001.json`.

Repare no que **não** se faz: não se escreve um laço, não se lê a pasta à mão, não se apaga nada. Os quatro cuidados são
configuração, e o componente faz o resto.

## 5. Quebre um de propósito

Com a sondagem concluída, tire a linha de `moveFailed` e rode `./verifica`. Antes, faça a previsão: para onde vai o
arquivo quebrado se ninguém diz para onde mandá-lo?

Sai em vermelho `Buzón: el embarque roto no está en .error/`. O arquivo fica em `buzon/`, e no log da sondagem
ele aparece falhando uma vez atrás da outra: sem `moveFailed`, o Camel o deixa onde estava e tenta de novo na
volta seguinte. Um arquivo quebrado que é tentado de novo para sempre não se perde, mas também não avisa: enche o log e
esconde os erros dos demais. Devolva a linha.

## 6. Confira

```bash
./verifica
```

O `verifica` cria os pedidos 4001 e 4002 nos três destinos e depois deixa três coisas na caixa postal: o embarque do
4001, terminado; o do 4002, ainda como `.tmp`; e um quebrado. Está concluída quando aparecem as dez marcas verdes:

```
✅ Arrancaron el router, los tres canales y el sondeo de la bodega
✅ Antes del embarque: la 4001 está CREADO en la API y REGISTRADO en el ERP
✅ API: la 4001 está EMBARCADO con la guía PAQX770004001
✅ API: recibió el embarque de la 4001 una sola vez
✅ ERP: la 4001 está EMBARCADO y su fila de embarque tiene la guía
✅ Socio: el 856 de la 4001 está en su SFTP y el lector X12 no le encontró fallas
✅ Socio: el 856 lleva la guía (REF*CN) y la orden (PRF)
✅ Buzón: el embarque de la 4001 se apartó a .camel/
✅ Buzón: el embarque roto se apartó a .error/ (no se perdió ni se reintenta sin fin)
✅ Buzón: el .tmp que la bodega aún escribía no se tocó (la 4002 sigue CREADO)
```

Veja a prova na própria pasta: `ls -A buzon buzon/.camel buzon/.error`. E abra o 856 que chegou ao parceiro,
`mio/salida/856-4001.edi`: procure o código de rastreio no segmento `REF*CN` e o pedido em `PRF`.

**Não roda?** `./verifica solucion` testa a nossa, e
`diff mio/sondeo-bodega solucion/sondeo-bodega` diz em que elas são diferentes.

| O que aparece | O que fazer |
|---|---|
| `el sondeo tomó el .tmp` | O padrão de `include` não termina em `\\.json`: com `embarque-.*` sozinho, `embarque-4002.json.tmp` também entra. Ou falta o `include` |
| `el embarque de la 4001 no está en .camel/` | Ficou `noop: true`, ou o `move` não leva `${file:name}` |
| `el embarque roto no está en .error/` | Falta `moveFailed` |
| `le faltan las tablas embarque y entrega` | O ERP é de antes desta lição: recrie-o como diz «Antes de começar» |
| `El puerto 8080 está ocupado` | Os serviços de outra lição ficaram rodando: `docker compose … down` na pasta dela |

---

## O que isto ensina fora do curso

**Muitos sistemas não chamam: deixam arquivos.** Armazéns, bancos e sistemas antigos entregam o trabalho como arquivos
em uma pasta ou em um SFTP, na hora em que podem. A integração se adapta ao que o outro sabe fazer, não o
contrário.

**Quem sonda vai sozinho.** A sondagem é pouco trabalho, e o pesado (mapear, chamar os destinos) é feito pelo router
e pelos canais, que podem, sim, crescer em réplicas. Por isso o `compose.yaml` põe `replicas: 1` na sondagem e deixa
o resto crescer do outro lado de uma chamada HTTP.

**Separar é melhor que apagar.** Com `.camel/` e `.error/`, qualquer pessoa pode ver o que foi processado e o que é preciso
conferir, sem abrir um log.

## Exercício: o segundo evento sem rotas novas

Com a versão própria concluída:

1. No router, quantas rotas de canal há para o embarque e quantas para a criação? O que muda entre uma
   chamada e a outra? (Dica: procure `${variable.paso}`.)
2. Troque o `delay` para 10000 e rode `./verifica`. Quanto demora agora para aparecer o EMBARCADO? O que se ganha e o que
   se perde com uma sondagem mais lenta? Volte para 2000.
3. Abra o `compose.yaml` da lição. Que serviço leva `replicas: 1`? O que aconteceria com o embarque do 4001
   se fosse colocado 3? Pense na API, no ERP e no parceiro separadamente: quem notaria e quem não? (Dica: a
   chave da API.)

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [Uma pasta, uma única sondagem](../decisiones/16-un-solo-sondeo-por-carpeta.md)

## Revisão

1. O que é sondar, e por que isso é necessário com o armazém e não com a loja?
2. Para que serve cada um dos quatro cuidados: `include`, `readLock`, `move` e `moveFailed`, e `delay`?
3. Por que o `.tmp` do 4002 não foi pego? O que aconteceria se o armazém escrevesse direto com o nome final?
4. O que faz um arquivo quebrado sem `moveFailed`, e por que isso é um problema mesmo que ele não se perca?
5. **A pergunta de encerramento:** por que a sondagem roda em uma única réplica, enquanto o router e os canais podem
   rodar em várias? O que aconteceria se todas as réplicas sondassem?

**A seguir:** na lição 14, o armazém já não manda um arquivo por pedido: manda um com tudo o que saiu pela
manhã. Um arquivo, vários pedidos. Quantos 856 são esperados?
