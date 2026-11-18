# Lição 09 — O fio entre serviços

> **O que será alcançado:** que cada pedido leve um **ID de correlação** que o acompanhe da loja ao router, do
> router ao int-rest e do int-rest à API, para que seja possível encontrá-lo em qualquer ponto do percurso buscando
> um único valor.

**Antes de começar é preciso ter:** a lição 08 concluída e o ambiente da 00 ligado:

```bash
docker compose -f infra/compose.yaml up -d --wait     # desde la carpeta raíz de la práctica
```

## A novidade de hoje, em uma frase

Na 08 o pedido passou por três serviços, e cada um escreveu no próprio log. Se amanhã uma loja reclamar «o meu
pedido não chegou», como juntar as linhas dos três logs que falam **desse** pedido? Hoje ele ganha um fio: um
ID que cada serviço recebe e passa para o seguinte.

**A imagem:** o código de rastreio de um pacote. Ele passa pela agência, pelo centro de distribuição e pelo entregador, e
em cada lugar é anotado. Com esse código sabe-se por onde o pacote passou. Se uma agência não o anota, dali em diante o pacote
fica invisível.

## O envelope, completo

Na 08 os cabeçalhos `ce-*` apareceram sem ser usados. Hoje eles são lidos:

| Cabeçalho | O que diz | Para o pedido 1003 |
|---|---|---|
| `ce-specversion` | Qual **versão** de CloudEvents o envelope usa | `1.0` |
| `ce-id` | Qual **evento** é este, um só | `orden-1003-creada` |
| `ce-type` | De que **tipo** é o evento | `mx.tienda.orden.creada` |
| `ce-source` | **Onde** aconteceu | `/tiendas/T-022` |
| `ce-subject` | **De que** coisa fala: o pedido | `1003` |
| `ce-correlationid` | O **fio**: une tudo o que acontece por causa de uma mesma requisição | quem decide é a loja, ou o router |

`ce-id` e `ce-correlationid` parecem a mesma coisa e não são. O `ce-id` nomeia **um** evento. O fio segue uma
requisição da loja por todos os serviços em que ela toca. Quando um pedido gerar vários eventos (criado, embarcado,
entregue), cada um terá o próprio `ce-id`, e todos poderão compartilhar o mesmo fio.

`ce-correlationid` não é um dos campos básicos de CloudEvents: é uma **extensão**, um campo que se acrescenta ao
envelope. A norma permite acrescentar extensões.

---

## 1. Prepare a pasta

```bash
cd lecciones/09-el-hilo-entre-servicios
cp -R inicio mio
```

## 2. Leia de onde sai o fio

Abra `mio/orden-router/orden-router.camel.yaml`. No alto há um passo `choice`:

| Se a loja… | O router… |
|---|---|
| manda o cabeçalho `X-Correlation-Id` | usa esse mesmo ID como fio |
| não o manda | inventa um novo, um UUID (um número aleatório tão longo que, na prática, não se repete) |

E no final, o router o **devolve** à loja na resposta. Assim a loja tem com o que perguntar pelo
pedido dela.

Repare no comentário: o ID é guardado em uma variável **antes** de limpar os cabeçalhos. Esse é o coração da
lição.

## 3. Faça a previsão

A rota de `inicio/` tem três TODO, e por enquanto nenhum serviço passa o fio para o seguinte. Responda em um papel:

1. Se a loja manda o pedido 1003 com o ID `mi-hilo-1`, em que ponto do percurso ele se perde: no router, no
   int-rest ou na API?
2. A loja recebe de volta o ID, mesmo que ele se perca no caminho?

Confira:

```bash
./verifica inicio
```

Saem três marcas vermelhas, e uma diz `La API no recibió el hilo de la 1003 (anotó: correlacion=-)`. O hífen é como
a API escreve «não me chegou nenhum». O router anotou o fio, sim, e a loja o recebeu de volta, sim: **o fio se
perde no primeiro serviço que não o reenvia**, logo depois do router.

## 4. Escreva os três passos

São três TODO e os três são a mesma ideia: **receber o fio e passá-lo adiante**.

**1. No router: coloque-o no envelope.** Em `mio/orden-router/orden-router.camel.yaml`, onde diz TODO, acrescente um
`setHeader` com o nome `ce-correlationid` e o valor da variável `correlacion`. Use como modelo o `setHeader`
de `ce-subject`, que está algumas linhas acima.

**2. No int-rest: guarde-o antes que seja apagado.** Em `mio/int-rest/int-rest.camel.yaml`, o primeiro TODO. Acrescente um
`setVariable` chamado `correlacion` com o valor `${header.ce-correlationid}`. Tem de ficar **ali**, no alto: alguns
passos abaixo está o `removeHeaders`, que apaga todos os cabeçalhos que chegaram.

**3. No int-rest: passe-o para a API.** O segundo TODO. Acrescente um `setHeader` chamado `X-Correlation-Id` com o valor
da variável `correlacion`. Vai depois do `removeHeaders`, junto aos demais cabeçalhos da chamada à API.

## 5. Acompanhe com os próprios olhos

A partir da pasta da lição, suba os serviços e veja os logs:

```bash
DONDE=mio docker compose -f compose.yaml up -d
DONDE=mio docker compose -f compose.yaml logs -f
```

Em **outro** terminal, a partir da raiz do curso, faça o papel da loja com um fio próprio:

```bash
curl -i -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: mi-hilo-1' -d @datos/ordenes/orden-1003.json
```

Procure `mi-hilo-1` em três lugares:

| Onde | O que procurar |
|---|---|
| A resposta do `curl` | O cabeçalho `X-Correlation-Id: mi-hilo-1` |
| Os logs do router e do int-rest | `correlacion=mi-hilo-1` nas linhas deles |
| O log da API | `docker compose -f infra/compose.yaml logs api-pedidos \| grep mi-hilo-1` |

Se nos três aparece o mesmo valor, o fio está completo. Agora mande o pedido **sem** o cabeçalho
`X-Correlation-Id`. Que ID foi devolvido à loja? Procure-o nos logs: deve ser o mesmo em todos.

Ao terminar, desligue os serviços:

```bash
DONDE=mio docker compose -f compose.yaml down
```

## 6. O erro mais comum: ler tarde

Faça o teste: no int-rest, mova o `setVariable` da correlação para que fique **abaixo** do `removeHeaders`.
Desligue, suba de novo e mande o pedido com `mi-hilo-1`.

A loja recebe o ID de volta, o router o anota, mas o int-rest escreve `correlacion=` (vazio) e a API,
`correlacion=-`. Quando o int-rest foi ler o cabeçalho, o `removeHeaders` já o tinha apagado. Não há erro nem
alarme: **o fio se corta em silêncio**.

Por isso a regra é: **o que for necessário da chamada que entrou, guarde em uma variável antes de limpar**. As
variáveis não são apagadas junto com os cabeçalhos. Devolva o passo ao lugar dele.

## 7. Confira

```bash
./verifica
```

O `verifica` reinicia a API, sobe os dois serviços e manda dois pedidos: o 1003 com um fio próprio e o 1001 sem
nenhum. Está concluída quando aparecem as oito marcas verdes:

```
✅ Arrancaron el router e int-rest
✅ La tienda mandó la 1003 y la respuesta final fue 201
✅ El router anotó el hilo tienda-…
✅ int-rest recibió el hilo en el sobre
✅ La API registró la 1003 con el mismo hilo: se puede seguir de punta a punta
✅ La tienda recibió de vuelta su mismo id
✅ Los encabezados del sobre (ce-*) no salieron hacia la tienda
✅ Sin id de la tienda, el router inventó … y llegó igual a la API
```

Onde aparece `…` vem o ID daquela execução: o `verifica` usa um novo a cada vez.

A sétima confere algo que não foi escrito por quem faz a lição: o envelope `ce-*` é para os serviços de dentro, e o router o
apaga antes de responder à loja. À loja só volta o `X-Correlation-Id`.

**Não roda?** `./verifica solucion` testa a nossa, e estes dois comandos dizem em que elas são diferentes:

```bash
diff mio/orden-router/orden-router.camel.yaml solucion/orden-router/orden-router.camel.yaml
diff mio/int-rest/int-rest.camel.yaml solucion/int-rest/int-rest.camel.yaml
```

| O que aparece | O que fazer |
|---|---|
| `int-rest no anotó el hilo` | Falta o passo 1 no router, ou o passo 2 ficou abaixo do `removeHeaders` (passo 6) |
| `La API no recibió el hilo` com todo o resto em verde | Falta o passo 3: o int-rest tem o fio, mas não o passa para a API |
| `El puerto 8080 está ocupado` | Os serviços ficaram no ar: `DONDE=mio docker compose -f compose.yaml down` |

---

## Exercício: encontre um pedido

Suba a versão própria, mande três pedidos (o 1001, o 1003 e outra vez o 1001), cada um sem `X-Correlation-Id`, e
anote o ID devolvido em cada um. Depois, **só com o ID do segundo**, responda: que pedido era, o que a
API respondeu a ele e quanto tempo levou desde que chegou ao router até a API responder? Dica: cada linha do log
começa com a hora.

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [CloudEvents em modo binário como envelope](../decisiones/11-cloudevents-como-sobre.md)
- [O fio de correlação viaja com o pedido, mas não sai de casa](../decisiones/12-el-hilo-de-correlacion.md)

## Revisão

1. Que diferença há entre `ce-id` e `ce-correlationid`?
2. Se a loja não manda ID, quem o coloca e de que forma?
3. Por que o int-rest guarda o fio em uma variável antes do `removeHeaders`?
4. O que a API escreveu quando o fio não chegou a ela, e por que isso é pior que um erro?
5. Para que serve à loja receber o ID de volta?

**A seguir:** na lição 10 o router deixa de mandar o pedido só para o int-rest: manda ao mesmo tempo para os três
destinos, e o mesmo fio tem de chegar à API, ao ERP e ao parceiro.
