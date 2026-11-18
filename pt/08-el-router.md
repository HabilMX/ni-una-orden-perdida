# Lição 08 — O router

> **O que será alcançado:** dividir a integração em dois serviços que rodam separados: um **router** que recebe
> o pedido e um serviço **int-rest** que sabe falar com a API. Escreve-se a chamada que os une, pelo nome
> do serviço e nunca por um endereço.

**Antes de começar é preciso ter:** as lições 02 a 07 concluídas e o ambiente da 00 ligado:

```bash
docker compose -f infra/compose.yaml up -d --wait     # desde la carpeta raíz de la práctica
```

## A novidade de hoje, em uma frase

Até agora cada lição era **uma** rota que fazia tudo: receber, traduzir e entregar. Com três destinos (a API, o
ERP e o parceiro), essa rota cresceria sem parar. Hoje ela é dividida: um serviço **decide para onde vai** o pedido e outro **sabe
entregá-lo**. Cada um roda no próprio contêiner.

```
tienda ──POST /ordenes──▶ [orden-router] ──POST http://int-rest:8080/integrar──▶ [int-rest] ──▶ API de pedidos
```

**A imagem:** a recepção de um hospital. A recepcionista não opera ninguém: pergunta qual é o problema e encaminha para
o especialista. Se amanhã abrir uma especialidade nova, a recepção continua igual; só aprende uma porta a mais.

---

## 1. Prepare a pasta

```bash
cd lecciones/08-el-router
cp -R inicio mio
```

Veja o que foi copiado: `mio/` tem **duas pastas**, uma por serviço.

| Pasta | Serviço | O que traz |
|---|---|---|
| `mio/orden-router/` | O router | A rota dele: recebe o pedido da loja e o passa para o int-rest |
| `mio/int-rest/` | int-rest | A rota dele e o mapeamento v2 da lição 03: traduz o pedido e o entrega à API |

## 2. Leia o compose da lição

Esta lição não usa `herramientas/camel`. Os dois serviços sobem juntos com um `compose.yaml` próprio, como o
ambiente da lição 00. Abra-o:

```yaml
x-camel: &camel
  image: apache/camel-jbang:4.22.1
  command: ["run", "."]          # corre todos los *.camel.yaml de su carpeta

services:
  orden-router:
    <<: *camel
    volumes:
      - ./${DONDE:-mio}/orden-router:/work
    ports: ["8080:8080"]

  int-rest:
    <<: *camel
    volumes:
      - ./${DONDE:-mio}/int-rest:/work
    # sin ports: no se ve desde tu máquina; por dentro de la red, cualquier servicio lo alcanza
```

Duas coisas que vale a pena ver:

- **Os dois serviços são a mesma imagem do Camel.** A única diferença é a pasta que cada um monta. `&camel`
  define a parte comum uma vez e `<<: *camel` a reutiliza. Para criar um serviço novo não é preciso programar outro
  programa: basta outra pasta com as rotas dela.
- **Só o router tem `ports`.** É o único que se vê a partir da máquina. O int-rest não se vê de fora, mas
  dentro da rede do Docker qualquer serviço pode chamá-lo; neste curso, o único que o chama é o router.

`DONDE` é uma variável que diz que pastas montar: `mio` (a própria, por padrão) ou `solucion` (a nossa).

## 3. Leia as duas rotas

**`mio/int-rest/int-rest.camel.yaml`** já vem completa. Recebe em `/integrar`, traduz com o mapeamento v2 e chama
a API, igual à rota da lição 03.

**`mio/orden-router/orden-router.camel.yaml`** recebe em `/ordenes` e prepara o pedido para passá-lo adiante. Antes da
chamada coloca nele alguns cabeçalhos que começam com `ce-`:

| Cabeçalho | Valor para o pedido 1003 |
|---|---|
| `ce-specversion` | `1.0` |
| `ce-type` | `mx.tienda.orden.creada` |
| `ce-source` | `/tiendas/T-022` |
| `ce-subject` | `1003` |
| `ce-id` | `orden-1003-creada` |

São o **envelope** do pedido, em um formato padrão que se chama **CloudEvents**: dizem o que aconteceu, onde e com quem.
Hoje eles só serão **vistos** viajando; na lição 09 servem para acompanhar um pedido de serviço em serviço.

No fim da rota há um **TODO**: falta a chamada ao int-rest.

## 4. Faça a previsão

Antes de escrever a chamada, responda em um papel:

1. Se o pedido 1003 for enviado ao router tal como está, sem a chamada, o que ele responde à loja? Chega algo à
   API?
2. Quando a chamada já estiver lá, em que ordem se espera ver o pedido passar pelos logs dos dois serviços?
3. Dentro do contêiner do router, para que máquina `localhost` deve apontar?

## 5. Escreva a chamada

No lugar do TODO, acrescente um passo `to` que faça um `POST` na rota `/integrar` do int-rest, na porta 8080 dele.
Chame-o **pelo nome de serviço**, `int-rest`, como o int-rest chama a API por `api-pedidos`. Use
`throwExceptionOnFailure: false` para que, se o int-rest responder com erro, o router o anote em vez de falhar:
a linha seguinte do log diz o que ele respondeu.

Se travar, a chamada do int-rest à API tem exatamente a mesma forma.

## 6. Suba os serviços e mande um pedido

A partir da pasta da lição:

```bash
DONDE=mio docker compose -f compose.yaml up -d
DONDE=mio docker compose -f compose.yaml logs -f
```

O segundo comando mostra os logs dos dois serviços juntos, cada linha com o nome de quem a escreveu.
Espere até ver `Started orden-router` e `Started int-rest`. Em **outro** terminal, a partir da raiz do curso:

```bash
curl -i -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' -d @datos/ordenes/orden-1003.json
```

Nos logs deve aparecer o pedido passando pelos dois serviços:

```
orden-router-1  | Orden 1003: se la paso a int-rest
int-rest-1      | Recibí del router la orden 1003 (sobre orden-1003-creada, tipo mx.tienda.orden.creada)
int-rest-1      | La API contestó 201 para la orden 1003
orden-router-1  | Orden 1003: int-rest contestó 201
```

Coincide com a previsão 2? A resposta volta pelo mesmo caminho: a API responde ao int-rest, o int-rest ao
router e o router à loja.

Se a API responder `200` em vez de `201`, ela já tinha o pedido 1003 da lição 03. Reinicie-a para que comece vazia:
`docker compose -f infra/compose.yaml restart api-pedidos`.

## 7. Quebre de propósito: localhost

Agora a pergunta 3. Na rota do router, troque `int-rest` por `localhost`:

```yaml
uri: http://localhost:8080/integrar
```

Desligue e suba de novo os serviços para que peguem a mudança, e mande o pedido outra vez:

```bash
DONDE=mio docker compose -f compose.yaml down
DONDE=mio docker compose -f compose.yaml up -d
```

A loja recebe um **404** e o log do router diz `int-rest contestó 404`. Mas o int-rest nunca recebeu nada.
**Dentro de um contêiner, `localhost` é esse mesmo contêiner.** O router chamou a si mesmo, procurou uma rota
`/integrar` que ele não tem e respondeu a si mesmo «não existe». Na máquina de quem faz a lição, `localhost` é essa máquina; em cada contêiner,
é o contêiner.

Por isso os serviços se chamam pelo **nome**: o Docker sabe em que contêiner vive o `int-rest`, mesmo que amanhã
ele mude de endereço. Devolva o nome e desligue:

```bash
DONDE=mio docker compose -f compose.yaml down
```

## 8. Confira

```bash
./verifica
```

O `verifica` reinicia a API para que comece vazia, sobe os dois serviços, manda o pedido 1003 e no fim os
desliga. Está concluída quando aparecem as seis marcas verdes:

```
✅ El router arrancó
✅ int-rest arrancó (la misma imagen, otra carpeta)
✅ La tienda mandó la 1003 al router y la respuesta final fue 201
✅ La API registró la orden 1003: el recorrido tienda → router → int-rest → API se completó
✅ int-rest recibió el sobre CloudEvents de la orden (id orden-1003-creada)
✅ int-rest no se ve desde tu máquina: solo el router lo alcanza
```

A quarta quem confirma é a API, a partir do próprio registro: não basta o router dizer que passou o pedido.

**Não roda?** `./verifica solucion` testa a nossa, e
`diff mio/orden-router/orden-router.camel.yaml solucion/orden-router/orden-router.camel.yaml` mostra em que são
diferentes.

| O que aparece | O que fazer |
|---|---|
| `La tienda esperaba 201 y le llegó 200` | O router devolveu o pedido sem chamar ninguém: falta o passo `to` |
| `La tienda esperaba 201 y le llegó 404` | A chamada aponta para `localhost`: volte ao passo 7 |
| `El puerto 8080 está ocupado` | Ficou outra rota rodando, ou os serviços desta lição: `DONDE=mio docker compose -f compose.yaml down` |
| `El router no arrancó` | O `verifica` mostra as últimas linhas do log: quase sempre é a indentação do passo novo |

---

## Exercício: um especialista a mais

Sem escrever código, responda em um papel: para ter amanhã um serviço `int-erp` que entregue o pedido ao ERP
com a rota da lição 05, que pasta seria criada, o que iria nela e que linhas seriam acrescentadas ao `compose.yaml`?
Ele teria `ports`? O que mudaria no router?

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [Uma única imagem; cada serviço é uma pasta](../decisiones/09-una-imagen-un-servicio-por-carpeta.md)
- [Os serviços se falam pelo nome, sobre HTTP](../decisiones/10-servicios-por-nombre-sobre-http.md)
- [CloudEvents em modo binário como envelope](../decisiones/11-cloudevents-como-sobre.md)

## Revisão

1. O que faz o router e o que faz o int-rest? Por que convém separá-los?
2. Se os dois serviços usam a mesma imagem, o que os torna diferentes?
3. Por que o router chama `int-rest` e não `localhost`?
4. Por que o int-rest não tem `ports` no compose?
5. O que dizem os cabeçalhos `ce-type`, `ce-subject` e `ce-id` do pedido 1003?

**A seguir:** na lição 09 acompanha-se um pedido de serviço em serviço com o envelope CloudEvents, e descobre-se o que
acontece quando um serviço não o reenvia.
