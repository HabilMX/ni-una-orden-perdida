# Lição 10 — Todos os canais

> **O que será alcançado:** que o router mande o mesmo pedido, **ao mesmo tempo**, para os três destinos (a API, o ERP e o
> parceiro), e que responda à loja o que cada um disse. E ver com os próprios olhos o que acontece quando um dos
> três está fora do ar.

**Antes de começar é preciso ter:** as lições 05, 07 e 09 concluídas e o ambiente da 00 ligado.

⚠️ **Esta lição acrescenta uma coluna ao ERP** (`correlacion`, para guardar o fio da 09). Se o ambiente já estava
no ar de antes, recrie o ERP uma vez, a partir da raiz do curso:

```bash
docker compose -f infra/compose.yaml up -d --wait --force-recreate -V erp
```

`--force-recreate` o cria de novo e `-V` apaga os dados dele, e assim ele inicia com as tabelas novas. Se isso for esquecido,
o `verifica` avisa e dá este mesmo comando.

## A novidade de hoje, em uma frase

Na 08 o router passava o pedido para **um** serviço. Hoje há quatro serviços e o router distribui **três
cópias** do pedido, uma por destino, todas ao mesmo tempo:

```
                                        ┌→ int-rest → API de pedidos
  tienda → POST /ordenes → [router] ────┼→ int-bd   → ERP
                                        └→ int-edi  → SFTP del socio (850)
```

O int-bd é a lição 05 atrás do router, e o int-edi é a lição 07. Não é preciso escrevê-los de novo: já vêm prontos.

**A imagem:** um aviso que o escritório manda a três filiais. Ele não espera a primeira responder para mandá-lo à
segunda: manda às três ao mesmo tempo e depois reúne as três respostas.

Este padrão tem nome nos EIP: **Multicast**. Um mesmo pedido vai para vários destinos fixos.

---

## 1. Prepare a pasta

```bash
cd lecciones/10-todos-los-canales
cp -R inicio mio
```

Agora `mio/` tem quatro pastas, uma por serviço: `orden-router`, `int-rest`, `int-bd` e `int-edi`.

## 2. Leia o router

Abra `mio/orden-router/orden-router.camel.yaml`. No alto tudo já é conhecido: o fio da 09 e o envelope `ce-*`. Embaixo
há algo novo: **três rotas de canal**, `canal-api`, `canal-erp` e `canal-socio`. Cada uma chama o próprio serviço e
é feita para deixar **uma única linha** com o que aconteceu, responda ou não o serviço dela:

| Se o serviço… | A linha é… |
|---|---|
| responde, ainda que com erro | `{"canal":"erp","codigo":200}`, com o código HTTP que ele deu |
| nem sequer responde (está fora do ar) | `{"canal":"erp","error":"UnknownHostException"}`, com o nome do erro |

Para isso usam `doTry` e `doCatch`: **tenta** a chamada e, se ela falhar, **captura** o erro e o transforma em linha.

Por que tanto cuidado? Porque ao testar esta lição vimos que, sem as rotas de canal, quando um serviço estava
fora do ar o router devolvia à loja **o pedido completo**, com o nome e o e-mail do cliente. Uma resposta de
erro nunca deve levar dados pessoais.

Hoje a rota principal só chama um dos três:

```yaml
- to:
    uri: direct:canal-api
```

`direct:` é uma chamada a outra rota **do mesmo arquivo**, sem rede no meio. O TODO de cima diz o que falta.

## 3. Faça a previsão

Responda em um papel:

1. Com o router como está, se o pedido 1003 for mandado, que destinos o recebem?
2. Quando ele for mandado aos três: se o ERP estiver fora do ar, a API e o parceiro recebem o pedido ou não?
3. Nesse caso, que código HTTP a loja deve receber?

Confira a primeira:

```bash
./verifica
```

Saem quatro marcas vermelhas. A API tem o pedido; o ERP e o parceiro não têm nada.

## 4. Escreva o multicast

Troque o `to: direct:canal-api` por um `multicast` que chame os três canais:

```yaml
- multicast:
    parallelProcessing: true
    aggregationStrategy: "#juntarRespuestas"
    steps:
      - to:
          uri: direct:canal-api
      # … los otros dos canales
```

| Parâmetro | O que faz |
|---|---|
| `parallelProcessing: true` | Os três ao mesmo tempo. Sem isto, um depois do outro: o parceiro esperaria o ERP terminar |
| `aggregationStrategy: "#juntarRespuestas"` | Como as três respostas são reunidas. `juntarRespuestas` é um bean que já vem declarado no alto, e as coloca em uma lista |

Depois do multicast, o corpo é essa lista de três linhas. Responda à loja com um `setBody`:

```
{"orden":"${variable.orden}","canales":[${join(",")}]}
```

`${join(",")}` une as três linhas da lista separando-as com vírgulas. Use a notação canônica, como nos demais
`setBody` do arquivo (`expression:` → `simple:` → `expression:`).

## 5. Suba e mande um pedido

Antes de subir à mão, **anote a chave do parceiro na pasta do int-edi**: é o serviço que se conecta ao
SFTP dele, como na 07.

```bash
(cd mio/int-edi && ../../../../herramientas/confiar-en-socio)
DONDE=mio docker compose -f compose.yaml up -d
DONDE=mio docker compose -f compose.yaml logs -f
```

Espere os quatro `Started`. Em outro terminal, a partir da raiz do curso:

```bash
curl -s -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' -d @datos/ordenes/orden-1001.json
```

A loja recebe o que cada canal disse:

```json
{"orden":"1001","canales":[{"canal":"api","codigo":201},{"canal":"erp","codigo":200},{"canal":"socio","codigo":200}]}
```

Os canais trabalham ao mesmo tempo, mas o router **espera os três terminarem** antes de responder, e a lista sai
na ordem em que estão escritos no YAML (api, erp, socio), não na ordem em que foram terminando. Repare nos logs:
as três linhas de «contestó» do router saem quase ao mesmo tempo, e a ordem delas, sim, pode mudar de uma execução para
outra.

Se o canal do parceiro disser `500`, confira o log do int-edi. Se aparecer `reject HostKey`, faltou anotar a chave.

## 6. Desligue um canal

Agora a pergunta 2. Com tudo no ar, desligue **só** o int-bd e mande outro pedido:

```bash
DONDE=mio docker compose -f compose.yaml stop int-bd
curl -s -w ' %{http_code}\n' -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' \
  -d @datos/ordenes/orden-1003.json
```

```
{"orden":"1003","canales":[{"canal":"api","codigo":…},{"canal":"erp","error":"UnknownHostException"},{"canal":"socio","codigo":200}]} 200
```

- A API e o parceiro receberam, **sim**, o pedido. Um canal fora do ar não detém os outros dois.
- A linha do ERP diz `UnknownHostException`: o router procurou o `int-bd` pelo nome e ninguém respondeu.
- A loja recebeu um **200**, embora um destino não tenha o pedido.

Se este passo for repetido com o mesmo pedido, o parceiro responde `500`: ele já tem esse 850 e não o sobrescreve, como na 07.

Reveja a previsão 3. E agora a pergunta que a lição deixa: **o que fazemos com o ERP, que não tem o pedido?**
Mandamos de novo? Quando? E se a API já o tem: mandamos de novo para ela também? Escreva a resposta: ela
será comparada com o que for construído nas lições 19 e 22.

Desligue tudo:

```bash
DONDE=mio docker compose -f compose.yaml down
```

## 7. O fio nos três destinos

O fio da 09 viaja no envelope para os três canais. Mas cada destino o guarda de um jeito:

| Destino | Onde fica o fio |
|---|---|
| API | No log dela: `correlacion=<id>` |
| ERP | Na coluna nova `pedido_cabecera.correlacion` |
| Parceiro | **Em nenhuma parte do arquivo.** O int-edi o anota no **próprio** log, e o 850 sai igual ao da 07 |

Por que não o mandamos ao parceiro? Porque ao parceiro só se enviam os campos que o formato X12 combina com ele. O
fio é nosso, da nossa integração; colocá-lo em um campo que o parceiro não pediu não se faz. Com o parceiro, o fio
já existe: são os **números de controle** do 850 vistos na 06. Quando o parceiro responder com a confirmação de recebimento,
o 997, o `AK1` dele cita o `GS06` (o do grupo) e o `AK2` o `ST02` (o da transação). O `ISA13`, o do
envelope completo, é citado por outra confirmação, o `TA1`. Isso é usado na lição 21.

**Nem todos os destinos podem guardar o ID da integração.** Por isso, mais adiante, uma base de estado vai unir o fio ao
número de controle.

## 8. Confira

```bash
./verifica
```

O `verifica` reinicia a API, esvazia o ERP e a caixa postal do parceiro, anota a chave do parceiro em `mio/int-edi/`, sobe os
quatro serviços e manda o pedido 1003 com um fio próprio. Está concluída quando aparecem as seis marcas verdes:

```
✅ Arrancaron el router y los tres canales
✅ La tienda mandó la 1003 y el router contestó lo que dijo cada canal: api=201 erp=200 socio=200
✅ API: tiene el pedido 1003, con el hilo tienda-…
✅ ERP: tiene el pedido 1003 REGISTRADO, con el hilo tienda-…
✅ Socio: tiene el 850 de la 1003 en su SFTP
✅ int-edi anotó el hilo (al socio no se le manda: con él, el hilo es el número de control del X12)
```

Três das seis são confirmadas pelo próprio destino: o registro da API, a tabela do ERP e a caixa postal do parceiro.

**Não roda?** `./verifica solucion` testa a nossa, e
`diff mio/orden-router/orden-router.camel.yaml solucion/orden-router/orden-router.camel.yaml` diz em que elas são
diferentes.

| O que aparece | O que fazer |
|---|---|
| `Tu ERP no tiene la columna correlacion` | Recrie o ERP com o comando do início |
| Vermelho só no ERP, ou só no parceiro | Esse canal não está no `multicast` |
| `El router no contestó que los tres canales la recibieron` e só aparece uma linha | Falta o `setBody` com `${join(",")}`, ou o multicast continua sendo um único `to` |
| `El puerto 8080 está ocupado` | Os serviços ficaram no ar: `DONDE=mio docker compose -f compose.yaml down` |

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [O fio de correlação viaja com o pedido, mas não sai de casa](../decisiones/12-el-hilo-de-correlacion.md)
- [O pedido sai para todos os canais, e o router conta o que aconteceu](../decisiones/13-multicast-que-no-inventa.md)

## Revisão

1. O que faz um multicast, e o que muda com `parallelProcessing: true`?
2. Para que serve o bean `juntarRespuestas`, e o que faz `${join(",")}`?
3. Por que cada canal deixa uma linha mesmo que o serviço dele esteja fora do ar? O que teria escapado sem isso?
4. Com o ERP fora do ar, que destinos receberam o pedido e que código a loja recebeu? Por que esse código é um
   problema?
5. Por que o fio não é mandado ao parceiro, e que número cumpre esse papel com ele?

**A seguir:** na lição 11 nem todos os pedidos vão para todos os destinos: o router decide para quais, conforme o que
o pedido trouxer.
