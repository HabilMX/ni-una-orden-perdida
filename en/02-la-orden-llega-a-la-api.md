# Lesson 02 — The order reaches the API

> **What you will achieve:** have an order from the store enter your route over the internet and reach the orders API,
> and understand what the API answers in each case. It is the first time your integration talks to another system.

**Before you start you need:** lesson 01 finished and the environment from lesson 00 turned on:

```bash
docker compose -f infra/compose.yaml up -d --wait     # desde la carpeta raíz de la práctica
```

## What is new today, in one sentence

In lesson 01 the input station was a folder. Today it is an **HTTP address**: the store sends the order the way your
browser sends a form, and your route forwards it to the orders API in the same way.

**HTTP, the bare minimum:** one program sends another a **request** with a method (`POST` means "here is something new for you"),
an address (`/ordenes`), some **headers** (data about the message, like labels on an envelope) and a **body**
(the order). The other one answers with a three-digit **code** and its own body. If you want more, there is the card
[`conceptos/antes-de-empezar/`](../conceptos/antes-de-empezar/).

---

## 1. Set up your folder

```bash
cd lecciones/02-la-orden-llega-a-la-api
cp -R inicio mio
cd mio
```

## 2. Read the route

Open `orden-a-api.camel.yaml`. It has five steps:

```
POST /ordenes  →  sacar el id de la orden  →  traducir  →  POST a la API  →  anotar lo que contestó
```

| Step in the YAML | What it does |
|---|---|
| `from: platform-http:/ordenes` with `httpMethodRestrict: POST` | **The input station.** It opens the address `/ordenes`; the `httpMethodRestrict` parameter makes it accept only `POST`. Port 8080 is not set by the route: `herramientas/camel` opens it |
| `setHeader` with `name: Idempotency-Key` and the `jsonpath` expression `"$.orden"` | It takes the order number (`"orden": "1001"`) and puts it in a header called `Idempotency-Key`. It is the **key** with which the API recognizes an order it has already received |
| `to: jsonata:…/mapeo.jsonata` | **It translates** the order from the store's format to the API's format. The translator comes ready-made; in lesson 03 you write it yourself |
| `removeHeaders: CamelHttp*` | It deletes the labels of the call that came in, so that they do not get mixed with the one that is about to go out |
| `to: http://api-pedidos:8080/pedidos` | **The output station.** It calls the API by its service name (`api-pedidos`), not by an IP number |

Also open `datos/ordenes/orden-1001.json` and `mio/mappings/api/pedido/v1/mapeo.jsonata`. Put one next to the other:
which name changed (`renglones` → ?) and which one stayed the same?

## 3. Predict

The orders API does not accept just anything: it answers with a different code depending on what reaches it. Before
you run, write on paper which code you think it gives in each case:

| Case | Your prediction |
|---|---|
| You send order 1001 for the first time | |
| You send **the same** order 1001 again | |
| You send an order with no order lines | |

## 4. Run it and send it an order

In one terminal, start the route:

```bash
../../../herramientas/camel run orden-a-api.camel.yaml
```

The first time it takes longer, because Camel downloads what it needs to speak HTTP. Wait for the line
`Started orden-a-api`.

In **another** terminal, from the same `mio/` folder, play the store and send the order:

```bash
curl -i -XPOST localhost:8080/ordenes \
  -H 'Content-Type: application/json' \
  -d @../../../datos/ordenes/orden-1001.json
```

- `curl` is a program that makes HTTP requests from the terminal.
- `-i` shows you the code and the headers of the response, not only the body.
- `-XPOST` is the method.
- `-H` adds a header.
- `-d @archivo` sends that file as the body.

Look at the two terminals. In the route's terminal you see `Recibí la orden 1001` and `La API contestó …`. Now ask
the API what it stored; it lives on port 8081:

```bash
curl localhost:8081/pedidos/1001
```

## 5. Compare with your prediction

Send the same order a second time and notice the code. Then try an order with no order lines: copy the order,
**change its number** (`"orden": "1002"`, so that it is a new order), leave the order lines empty and send it.

```bash
cp ../../../datos/ordenes/orden-1001.json sin-renglones.json   # en tu editor: "orden": "1002" y "renglones": []
curl -i -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' -d @sin-renglones.json
```

| Case | What the API answers | What it means |
|---|---|---|
| First time | **201** | *Created*: "I created it" |
| The same order again | **200**, with `ya_existia` | "I already had it; I did not duplicate it". The API recognized the key |
| No order lines | **422** | "I understood what you sent, but it makes no sense": an order with no line items cannot be recorded |
| Without the `Idempotency-Key` key | **400** | "You are missing something mandatory" |
| The same key with **different** content | **422** | "You already used this key with something different". Look at the `type` in the body |

This table is the **contract of the course's API**. From the IETF draft for `Idempotency-Key` it takes the 400 without
a key and the 422 for a reused key; the 201 and the 200 are decided by this API, because the draft does not fix them.
And one more difference: in the draft the key is a quoted string (`"1001"`); here it goes without quotes, just like the
order number. An API that followed the draft to the letter would ask for it in quotes: before integrating, you read
its contract.

When something goes wrong, the API answers with an error JSON in a standard shape (*Problem Details*, the type
`application/problem+json`): it carries `type`, `title`, `status` and `detail`. The `type` is what tells one 422 from another.

You did not try the last two. **How would you go about causing a 400?** Hint: which line of the route sets the key?
And what if you send order 1001 with a different quantity? It answers 422, the same as the order with no order lines. How do
you tell the two apart? Look at the body of the response. Run the tests and then put the route back the way it was.

Do you see why the order with no order lines needed a different number? With 1001 you would have had the same key with
different content, and the 422 would have been because of the key, not because of the order lines.

**What matters about the 200:** if the network fails and the store resends the order, the API does not create two
orders, because the key tells it that it is the same one. That is why the order's id travels from the first station.
In lesson 18 you will build that same protection into your own integration.

## 6. Check

Stop the route with `Ctrl+C` (otherwise port 8080 stays taken) and, from the lesson folder:

```bash
cd ..
./verifica
```

`verifica` restarts the API so that it starts empty, starts your route, sends the order twice and checks. It is done
when you see the five green checks:

```
✅ La ruta arrancó
✅ Primer envío: la API creó el pedido (201)
✅ Reenvío: la API lo reconoció y no lo duplicó (200)
✅ La API tiene el pedido 1001 con sus 2 partidas
✅ La API recibió la llave 1001: el id de la orden viajó hasta el destino
```

The last one is confirmed by **the API**, not by your route: `verifica` reads what the destination wrote down. Your
route saying that it sent something does not prove that it arrived; whoever received it proves it. That idea will come
back many times in the course.

**Not working?** `./verifica solucion` tests ours. If ours comes out green and yours does not,
`diff mio/orden-a-api.camel.yaml solucion/orden-a-api.camel.yaml` tells you where they differ.

| What you see | What to do |
|---|---|
| `El puerto 8080 está ocupado` | A route was left running in another terminal: stop it with `Ctrl+C` |
| `La API no levantó: ¿corriste la 00?` | Turn on the environment: `docker compose -f infra/compose.yaml up -d --wait` |
| `curl: (7) Failed to connect` | The route has not started yet: wait for the `Started` line |

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [The API receives an idempotency key](../decisiones/05-idempotency-key-hacia-la-api.md)

## Review

1. Which is the input station of this route, and which is the output one?
2. What is the `Idempotency-Key` header for, and where does its value come from?
3. What is the difference between a 201 and a 200 in this API?
4. Why does the route call `api-pedidos` and not an IP address?
5. Why is the last check made by the API and not by your route?

**Next:** in lesson 03 the translator no longer comes ready-made. You write it yourself, and some test cases tell you
whether it came out right.
