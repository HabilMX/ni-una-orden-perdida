# Lição 02 — O pedido chega à API

> **O que será alcançado:** que um pedido da loja entre na rota pela internet e chegue à API de pedidos, e
> entender o que a API responde em cada caso. É a primeira vez que a integração fala com outro sistema.

**Antes de começar é preciso ter:** a lição 01 concluída e o ambiente da 00 ligado:

```bash
docker compose -f infra/compose.yaml up -d --wait     # desde la carpeta raíz de la práctica
```

## A novidade de hoje, em uma frase

Na lição 01 a estação de entrada era uma pasta. Hoje é um **endereço HTTP**: a loja manda o pedido como quando
o navegador manda um formulário, e a rota o reenvia para a API de pedidos da mesma forma.

**HTTP, o mínimo:** um programa manda a outro uma **requisição** com um método (`POST` é «aqui vai algo novo»),
um endereço (`/ordenes`), alguns **cabeçalhos** (dados sobre a mensagem, como etiquetas em um envelope) e um **corpo**
(o pedido). O outro responde com um **código** de três algarismos e o próprio corpo. Para saber mais, há a ficha
[`conceptos/antes-de-empezar/`](../conceptos/antes-de-empezar/).

---

## 1. Prepare a pasta

```bash
cd lecciones/02-la-orden-llega-a-la-api
cp -R inicio mio
cd mio
```

## 2. Leia a rota

Abra `orden-a-api.camel.yaml`. Tem cinco passos:

```
POST /ordenes  →  sacar el id de la orden  →  traducir  →  POST a la API  →  anotar lo que contestó
```

| Passo no YAML | O que faz |
|---|---|
| `from: platform-http:/ordenes` com `httpMethodRestrict: POST` | **A estação de entrada.** Abre o endereço `/ordenes`; o parâmetro `httpMethodRestrict` faz com que só aceite `POST`. A porta 8080 não é a rota que define: quem a abre é `herramientas/camel` |
| `setHeader` com `name: Idempotency-Key` e a expressão `jsonpath` `"$.orden"` | Tira o número do pedido (`"orden": "1001"`) e o coloca em um cabeçalho chamado `Idempotency-Key`. É a **chave** com que a API reconhece um pedido que já recebeu |
| `to: jsonata:…/mapeo.jsonata` | **Traduz** o pedido do formato da loja para o formato da API. O tradutor já vem pronto; na lição 03 ele é escrito à mão |
| `removeHeaders: CamelHttp*` | Apaga as etiquetas da chamada que entrou, para que não se misturem com a que vai sair |
| `to: http://api-pedidos:8080/pedidos` | **A estação de saída.** Chama a API pelo nome de serviço (`api-pedidos`), não por um número de IP |

Abra também `datos/ordenes/orden-1001.json` e `mio/mappings/api/pedido/v1/mapeo.jsonata`. Coloque um ao lado do outro:
que nome mudou (`renglones` → ?) e qual ficou igual?

## 3. Faça a previsão

A API de pedidos não aceita qualquer coisa: responde com um código diferente conforme o que chega. Antes de rodar,
escreva em um papel que código ela deve dar em cada caso:

| Caso | A previsão |
|---|---|
| O pedido 1001 é enviado pela primeira vez | |
| **O mesmo** pedido 1001 é enviado outra vez | |
| É enviado um pedido sem linhas | |

## 4. Rode a rota e mande um pedido

Em um terminal, inicie a rota:

```bash
../../../herramientas/camel run orden-a-api.camel.yaml
```

Na primeira vez demora mais, porque o Camel baixa o que precisa para falar HTTP. Espere a linha
`Started orden-a-api`.

Em **outro** terminal, a partir da mesma pasta `mio/`, faça o papel da loja e mande o pedido:

```bash
curl -i -XPOST localhost:8080/ordenes \
  -H 'Content-Type: application/json' \
  -d @../../../datos/ordenes/orden-1001.json
```

- `curl` é um programa que faz requisições HTTP a partir do terminal.
- `-i` mostra o código e os cabeçalhos da resposta, não só o corpo.
- `-XPOST` é o método.
- `-H` acrescenta um cabeçalho.
- `-d @archivo` manda esse arquivo como corpo.

Veja os dois terminais. No da rota aparecem `Recibí la orden 1001` e `La API contestó …`. Agora pergunte à
API o que ela guardou; ela vive na porta 8081:

```bash
curl localhost:8081/pedidos/1001
```

## 5. Compare com a previsão

Mande o mesmo pedido uma segunda vez e repare no código. Depois teste um pedido sem linhas: copie o pedido,
**troque o número** (`"orden": "1002"`, para que seja um pedido novo), deixe as linhas vazias e mande.

```bash
cp ../../../datos/ordenes/orden-1001.json sin-renglones.json   # en tu editor: "orden": "1002" y "renglones": []
curl -i -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' -d @sin-renglones.json
```

| Caso | O que a API responde | O que significa |
|---|---|---|
| Primeira vez | **201** | *Created*: «criei» |
| O mesmo pedido outra vez | **200**, com `ya_existia` | «Já o tinha; não dupliquei». A API reconheceu a chave |
| Sem linhas | **422** | «Entendi o que foi enviado, mas não faz sentido»: um pedido sem itens não pode ser registrado |
| Sem a chave `Idempotency-Key` | **400** | «Falta algo obrigatório» |
| A mesma chave com **outro** conteúdo | **422** | «Esta chave já foi usada com algo diferente». Veja o `type` do corpo |

Esta tabela é o **contrato da API do curso**. Do rascunho do IETF para `Idempotency-Key` ela toma o 400 sem
chave e o 422 por chave reutilizada; o 201 e o 200 são decisão desta API, porque o rascunho não os fixa. E outra
diferença: no rascunho a chave é uma cadeia com aspas (`"1001"`); aqui vai sem aspas, igual ao número
do pedido. Uma API que seguisse o rascunho ao pé da letra a pediria entre aspas: antes de integrar, lê-se o
contrato dela.

Quando algo dá errado, a API responde com um JSON de erro de forma padrão (*Problem Details*, o tipo
`application/problem+json`): traz `type`, `title`, `status` e `detail`. O `type` é o que distingue um 422 de outro.

As duas últimas não foram testadas. **Como provocar um 400?** Dica: que linha da rota coloca a chave?
E se o pedido 1001 for enviado com outra quantidade? A resposta é 422, igual à do pedido sem linhas. Como distinguir
os dois? Veja o corpo da resposta. Faça os testes e depois devolva a rota a como estava.

Dá para ver por que o pedido sem linhas precisava de outro número? Com o 1001 haveria a mesma chave com outro
conteúdo, e o 422 teria sido pela chave, não pelas linhas.

**O importante do 200:** se a rede falha e a loja reenvia o pedido, a API não cria dois pedidos, porque a chave
diz a ela que é o mesmo. Por isso o id do pedido viaja desde a primeira estação. Na lição 18 essa
mesma proteção será construída na própria integração.

## 6. Confira

Pare a rota com `Ctrl+C` (senão, a porta 8080 fica ocupada) e, a partir da pasta da lição:

```bash
cd ..
./verifica
```

O `verifica` reinicia a API para que comece vazia, inicia a rota, manda o pedido duas vezes e confere. Está concluída
quando aparecem as cinco marcas verdes:

```
✅ La ruta arrancó
✅ Primer envío: la API creó el pedido (201)
✅ Reenvío: la API lo reconoció y no lo duplicó (200)
✅ La API tiene el pedido 1001 con sus 2 partidas
✅ La API recibió la llave 1001: el id de la orden viajó hasta el destino
```

A última quem confirma é **a API**, não a rota: o `verifica` lê o que o destino anotou. A rota dizer que mandou algo
não prova que chegou; quem prova é quem recebeu. Essa ideia vai voltar muitas vezes no curso.

**Não roda?** `./verifica solucion` testa a nossa. Se a nossa sai em verde e a própria não,
`diff mio/orden-a-api.camel.yaml solucion/orden-a-api.camel.yaml` mostra em que são diferentes.

| O que aparece | O que fazer |
|---|---|
| `El puerto 8080 está ocupado` | Ficou uma rota rodando em outro terminal: pare-a com `Ctrl+C` |
| `La API no levantó: ¿corriste la 00?` | Ligue o ambiente: `docker compose -f infra/compose.yaml up -d --wait` |
| `curl: (7) Failed to connect` | A rota ainda não iniciou: espere a linha `Started` |

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [A API recebe uma chave de idempotência](../decisiones/05-idempotency-key-hacia-la-api.md)

## Revisão

1. Qual é a estação de entrada desta rota, e qual a de saída?
2. Para que serve o cabeçalho `Idempotency-Key`, e de onde sai o valor dele?
3. Que diferença há entre um 201 e um 200 nesta API?
4. Por que a rota chama `api-pedidos` e não um endereço IP?
5. Por que a última conferência é feita pela API e não pela rota?

**A seguir:** na lição 03 o tradutor deixa de vir pronto. Ele é escrito à mão, e alguns casos de teste dizem se
ficou certo.
