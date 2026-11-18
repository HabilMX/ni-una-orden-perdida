# Lição 17 — A entrega chegou antes do código de rastreio

> **O que será alcançado:** que uma entrega que chega **antes** do embarque correspondente não se perca nem seja enviada fora de hora:
> fica estacionada na base e sai sozinha, uma única vez, assim que o embarque chega.

**Antes de começar é preciso ter:** as lições 15 e 16 concluídas e o ambiente da 00 ligado.

## A novidade de hoje, em uma frase

Na lição 16 tudo chegou em ordem: o pedido foi criado, depois embarcado e por fim entregue. Mas o armazém e a
transportadora não se falam, e às vezes o aviso de entrega **chega antes** do arquivo de embarque. Se o
router o distribui assim, a API e o ERP o rejeitam: não se pode entregar algo que, para eles, nunca saiu. E se ele o
joga fora, o cliente nunca aparece como atendido. Hoje ele é **estacionado** e **liberado** quando já pode sair.

**A imagem:** o estacionamento de um hospital. Chega uma ambulância de transferência, mas o leito do paciente
ainda não está pronto. Não a mandam de volta, nem colocam o paciente em um quarto ocupado: ela fica estacionada. E assim que
alguém avisa «o leito está pronto», a ambulância passa. Ninguém sai a cada cinco minutos para olhar o estacionamento: o
que move a ambulância é o aviso.

---

## 1. Prepare a pasta

```bash
cd lecciones/17-la-entrega-llego-antes-que-la-guia
cp -R inicio mio
```

Hoje o router traz peças novas. Quase todas já estão feitas: o trabalho é a última.

| Peça | Onde | É para escrever? |
|---|---|---|
| A máquina de estados: que evento do ciclo de vida precisa de qual, em cada canal | `mio/orden-router/reglas/maquina-de-estados.yaml` | Não, só leia |
| A decisão: com a máquina e o que está guardado, diz `procesa`, `estaciona` ou `invalido` | `direct:decidir`, com o mapeamento em `mappings/estado/decision/v1/` e os casos de teste de referência | Não, só leia |
| O estacionamento: ao guardar um evento, cada operação nasce `pendiente` ou `estacionada` | `guardar-momento`, em `estado.camel.yaml` | Não, já vem pronto |
| **A varredura**: libera o que está estacionado e já pode sair | `barrido.camel.yaml` | **Sim** |

Abra a máquina de estados. Lê-se assim: no canal `rest`, o evento `entregada` precisa que `embarcada` já esteja
**aceita nesse mesmo canal**. Vai por canal porque cada destino tem a própria ordem: o fato de a API já ter o
embarque não diz nada sobre o ERP.

Abra também um caso de teste de referência da decisão, `cases/entrega-sin-embarque/`: o `entrada.json` e o `esperado.json`.
É a mesma ideia da lição 03: a regra se prova com exemplos, não com fé.

## 2. Faça a previsão

Responda em um papel:

1. Chega a entrega do pedido 4002, e o embarque dele ainda não. O que tem de ficar na base? O que cada destino
   tem de receber?
2. Depois chega o embarque. Quem percebe que a entrega já pode sair? Quando?
3. Se a varredura rodar duas vezes, a entrega poderia sair duas vezes? O que impediria isso?

Veja como está hoje:

```bash
./verifica inicio
```

A entrega é estacionada corretamente, mas a varredura não libera nada: mesmo com a chegada do embarque, a entrega fica
estacionada para sempre. O 4002 nunca aparece como ENTREGADO.

## 3. Escreva a varredura

Abra `mio/orden-router/barrido.camel.yaml`. No alto está `barrido-http`, a porta de entrada para pedir uma varredura à mão com
`POST /barrido`; já está feita. O trabalho é a rota `barrido`, com quatro TODO:

| TODO | O que escrever | Para quê |
|---|---|---|
| 1 | Um `to` com `sql:SELECT id, orden, momento, canal FROM operacion WHERE estado = 'estacionada' ORDER BY id` | Trazer o que está estacionado, na ordem em que chegou |
| 2 | Um `split` sobre `${body}`; dentro, quatro variáveis (`operacion`, `orden`, `momento`, `canal`, de `${body[id]}`, `${body[orden]}`…) e uma chamada a `direct:decidir` | Decidir de novo cada uma com o que há hoje na base |
| 3 | Se `${variable.decision} == 'procesa'`: `UPDATE operacion SET estado = 'pendiente' WHERE id = … AND estado = 'estacionada'` | Passá-la a pendente, **só se continuar estacionada** |
| 4 | Se `${header.CamelSqlUpdateCount} == 1`: para `seda:repartir` com `pattern: InOnly` | Enfileirar a distribuição, **só se foi esta varredura que a mudou** |

Para o «se…» use um `filter`: ele deixa a mensagem passar só quando a condição se cumpre. O TODO 4 vai dentro do
`filter` do TODO 3.

O `AND estado = 'estacionada'` do TODO 3 é o coração da lição. Imagine duas varreduras ao mesmo tempo: as duas
leem a entrega estacionada e as duas decidem «procesa». A primeira a muda: a base responde «mudei 1 linha». A
segunda chega tarde e, como a entrega já não está estacionada, a base responde «mudei 0». Só a que mudou 1 a enfileira. **A
base decide quem ganha**, não a sorte.

E quem chama a varredura? Abra `reparto.camel.yaml` e procure `direct:barrido`, quase no fim: ela é chamada **toda vez
que um canal aceita um evento**, porque isso é a única coisa que pode destravar outro. Não há um relógio que a dispare
de tempos em tempos.

## 4. Quebre um de propósito

Com a varredura concluída, comente em `reparto.camel.yaml` o passo que chama `direct:barrido`. Suba os
serviços, crie o pedido 4002, mande a entrega dele e depois deixe o lote do armazém, que traz o embarque:

```bash
DONDE=mio docker compose -f compose.yaml up -d
curl -s -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' -d @../../datos/ordenes/tramo-2/orden-4002.json
curl -s -XPOST localhost:8080/webhooks/paqueteria -H 'Content-Type: application/json' -d @../../datos/webhooks/entrega-4002.json
cp ../../datos/embarques/lote-bodega-0001.json buzon/
```

O lote traz embarques de outros pedidos que não foram criados hoje; o router os rejeita com 404, e está certo. Espere alguns
segundos e pergunte à base:

```bash
docker compose -f ../../infra/compose.yaml exec estado psql -U router -d estado \
  -c "SELECT momento, canal, estado FROM operacion WHERE orden = '4002' ORDER BY id"
```

Antes, faça a previsão: em que estado está a entrega?

O embarque já está `aceptada` nos três canais, mas a entrega continua `estacionada`. Ninguém avisou que algo
mudou. Agora peça a varredura à mão e pergunte de novo:

```bash
curl -s -XPOST localhost:8080/barrido
```

Agora sim ela sai. É isso que o aviso de «um canal aceitou» faz: avisar na hora certa. Sem ele, alguém teria de pedir a varredura, ou um
relógio teria de olhar a base de tempos em tempos mesmo sem ter acontecido nada. Devolva a chamada e desligue:

```bash
DONDE=mio docker compose -f compose.yaml down
```

## 5. Confira

```bash
./verifica
```

O `verifica` cria o pedido 4002, manda a entrega dele **antes** do embarque, confere que foi estacionada, deixa o embarque na
caixa postal e, no fim, pede uma varredura à mão para conferir que nada sai duas vezes. Está concluída quando aparecem as
dez marcas verdes:

```
✅ Arrancaron el router (con su máquina de estados y el barrido), los tres canales y el sondeo
✅ La 4002 está creada: CREADO en la API y REGISTRADO en el ERP
✅ La entrega de la 4002 llegó antes que su embarque y el router contestó 202 «recibida»
✅ Estado: las tres operaciones de la entrega quedaron estacionadas, sin ningún intento
✅ Destinos: a nadie le llegó la entrega a destiempo (la API sigue en CREADO, el ERP sin entrega, sin 214)
✅ Estado: llegó el embarque, el barrido soltó la entrega y los seis quedaron aceptados; la orden va en «entregada»
✅ API: la 4002 está ENTREGADO, con su guía
✅ ERP: la 4002 está ENTREGADO y su fila de entrega tiene la guía
✅ Socio: el 214 de la 4002 está en su SFTP y el lector X12 no le encontró fallas
✅ Una sola vez: un intento por canal para la entrega y un solo 201 en la API, aunque el barrido corrió otra vez a mano
```

A quarta e a última são as que mais importam. O que está estacionado **não tem tentativas**: não saiu para lugar nenhum. E a
varredura extra não repetiu nada.

**Não roda?** `./verifica solucion` testa a nossa, e
`diff mio/orden-router/barrido.camel.yaml solucion/orden-router/barrido.camel.yaml` mostra em que são diferentes.

| O que aparece | O que fazer |
|---|---|
| `después del embarque, embarque y entrega quedaron` … com a entrega `estacionada` | Falta o TODO 3 ou o 4, ou o `filter` compara `decision` com outra palavra: é `'procesa'` |
| `${body[id]}` sai vazio | O `SELECT` não traz a coluna `id`, ou as variáveis ficaram fora do `split` |
| `Esperaba el barrido a mano en 200, 3 intentos de la entrega y un 201` e há mais | O `UPDATE` não leva `AND estado = 'estacionada'`, ou faltou filtrar por `CamelSqlUpdateCount` |
| Nada é enfileirado embora o UPDATE tenha mudado a linha | O `to` para `seda:repartir` ficou fora do segundo `filter`, ou falta `pattern: InOnly` |
| `El puerto 8080 está ocupado` | Os serviços do passo 4 ficaram no ar: `DONDE=mio docker compose -f compose.yaml down` |

---

## O que isto ensina fora do curso

**A ordem em que as coisas chegam não é a ordem em que aconteceram.** Entre sistemas que não se coordenam, um aviso
pode passar à frente de outro. O padrão que resolve isso se chama *Resequencer*: guardar o que chegou fora de ordem e
liberá-lo na ordem correta.

**A regra de quem vem antes vive em um só lugar.** Está em um YAML que se lê como dados. Acrescentar um evento
novo, uma devolução por exemplo, é acrescentar uma linha, não outra rota.

**Quando dois processos competem pela mesma coisa, que a base decida.** O `UPDATE … WHERE … AND estado = …` é uma forma
de cadeado: só um pode passar a linha de um estado a outro. Essa ideia aparece em quase todo sistema
que distribui trabalho.

## Exercício: a regra como dados

Com a versão própria concluída:

1. Na máquina de estados, o que seria preciso acrescentar para um evento novo, `devuelta`, que precise da entrega?
   Escreva. Seria preciso mexer em alguma rota?
2. Abra os cinco casos de teste de referência da decisão. Para `entrega-con-embarque-pendiente`, por que a decisão é
   estacionar, se o embarque **já chegou**?
3. Na base, depois do `verifica`, conte as tentativas da entrega do pedido 4002:
   `SELECT o.canal, COUNT(i.id) FROM operacion o LEFT JOIN intento i ON i.operacion = o.id WHERE o.orden = '4002' AND o.momento = 'entregada' GROUP BY o.canal`.
   Quantas se esperam por canal, e por que não são duas embora a varredura tenha rodado duas vezes?

---

## O que já funciona

Com esta lição fecha-se a parte 2. Além do que veio da parte 1, um pedido já faz isto:

- é embarcado a partir de um arquivo do armazém, com ou sem lote, sem que o arquivo seja pego pela metade nem duas vezes;
- é guardado antes de responder, com uma linha por canal e um registro de cada tentativa;
- é entregue a partir do aviso da transportadora, que recebe um 202 assim que ficou guardado;
- e se a entrega chega antes do embarque, espera estacionada e sai uma única vez, assim que pode.

O que ainda não faz: reconhecer uma mensagem que chega repetida com outro conteúdo, nem tentar de novo o que ficou em
`no_se_sabe`. Isso é a parte 3.

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [O que chega antes da hora é estacionado, não rejeitado](../decisiones/21-estacionar-lo-que-llega-antes.md)

## Revisão

1. O que quer dizer uma operação estar `estacionada`, e em que se distingue de `pendiente`?
2. O que a máquina de estados diz sobre a entrega, e por que vai por canal?
3. Para que serve o `AND estado = 'estacionada'` no UPDATE da varredura?
4. Por que a varredura confere `CamelSqlUpdateCount` antes de enfileirar?
5. **A pergunta de encerramento:** a varredura é disparada por um acontecimento, o de um canal ter aceitado um evento, e não por um temporizador
   que olha a base a cada minuto. O que se ganha com isso? O que se perderia com o temporizador, e em que caso ele
   faria falta?

**A seguir:** na lição 18 começa a parte 3, «que não falhe». Primeiro, uma pergunta que parece fácil:
quando duas mensagens são a mesma?
