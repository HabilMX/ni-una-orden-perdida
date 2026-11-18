# Lesson 09 — The thread between services

> **What you will achieve:** every order carries a **correlation ID** that goes with it from the store to the router,
> from the router to int-rest and from int-rest to the API, so that you can find it at any point of the journey by
> searching for a single value.

**Before you start you need:** lesson 08 finished and the environment from lesson 00 turned on:

```bash
docker compose -f infra/compose.yaml up -d --wait     # desde la carpeta raíz de la práctica
```

## What is new today, in one sentence

In lesson 08 the order went through three services, and each one wrote to its own log. If tomorrow a store complains
"my order did not arrive", how do you put together the lines of the three logs that talk about **that** order? Today
you give it a thread: an ID that each service receives and passes on to the next one.

**The picture:** the tracking number of a package. It goes through the branch office, the distribution center and the
delivery driver, and at each place they write it down. With that number you know where it has been. If one branch does
not write it down, from that point on the package becomes invisible.

## The envelope, complete

In lesson 08 you saw the `ce-*` headers without using them. Today you read them:

| Header | What it says | For order 1003 |
|---|---|---|
| `ce-specversion` | Which **version** of CloudEvents the envelope uses | `1.0` |
| `ce-id` | Which **event** this is, one and only one | `orden-1003-creada` |
| `ce-type` | What **type** of event it is | `mx.tienda.orden.creada` |
| `ce-source` | **Where** it happened | `/tiendas/T-022` |
| `ce-subject` | **What** it is about: the order | `1003` |
| `ce-correlationid` | The **thread**: it ties together everything that happens because of one same request | the store decides it, or the router does |

`ce-id` and `ce-correlationid` look like the same thing and they are not. The `ce-id` names **one** event. The thread
follows a request from the store through every service it touches. When an order generates several events (created,
shipped, delivered), each one will have its own `ce-id`, and all of them will be able to share the same thread.

`ce-correlationid` is not one of the base fields of CloudEvents: it is an **extension**, a field that is added to the
envelope. The specification allows adding extensions.

---

## 1. Set up your folder

```bash
cd lecciones/09-el-hilo-entre-servicios
cp -R inicio mio
```

## 2. Read where the thread comes from

Open `mio/orden-router/orden-router.camel.yaml`. At the top there is a `choice` step:

| If the store… | The router… |
|---|---|
| sends the `X-Correlation-Id` header | uses that same ID as the thread |
| does not send it | makes up a new one, a UUID (a random number so long that, in practice, it never repeats) |

And at the end, the router **returns** it to the store in the response. That way the store has something to ask about
its order with.

Notice the comment: the ID is saved in a variable **before** cleaning the headers. That is the heart of the lesson.

## 3. Predict

The route in `inicio/` has three TODOs, and for now no service passes the thread on to the next one. Answer on paper:

1. If the store sends order 1003 with the ID `mi-hilo-1`, at what point of the journey is it lost: in the router, in
   int-rest or in the API?
2. Does the store get its ID back, even if it is lost along the way?

Check it:

```bash
./verifica inicio
```

Three red marks come out, and one says `La API no recibió el hilo de la 1003 (anotó: correlacion=-)` (the API did not
receive the thread of order 1003). The dash is how the API writes "none reached me". The router did write down the
thread and the store did get it back: **the thread is lost at the first service that does not forward it**, right
after the router.

## 4. Write the three steps

There are three TODOs and all three are the same idea: **receive the thread and pass it on**.

**1. In the router: put it in the envelope.** In `mio/orden-router/orden-router.camel.yaml`, where it says TODO, add a
`setHeader` with the name `ce-correlationid` and the value of the variable `correlacion`. Use as a model the
`setHeader` for `ce-subject`, which is a few lines above.

**2. In int-rest: save it before it is erased.** In `mio/int-rest/int-rest.camel.yaml`, the first TODO. Add a
`setVariable` called `correlacion` with the value `${header.ce-correlationid}`. It has to go **there**, at the top: a
few steps further down is `removeHeaders`, which erases all the headers that came in.

**3. In int-rest: pass it to the API.** The second TODO. Add a `setHeader` called `X-Correlation-Id` with the value
of the variable `correlacion`. It goes after the `removeHeaders`, next to the other headers of the call to the API.

## 5. Follow it with your own eyes

From the lesson's folder, bring up the services and watch their logs:

```bash
DONDE=mio docker compose -f compose.yaml up -d
DONDE=mio docker compose -f compose.yaml logs -f
```

In **another** terminal, from the root of the course, play the store with your own thread:

```bash
curl -i -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: mi-hilo-1' -d @datos/ordenes/orden-1003.json
```

Look for `mi-hilo-1` in three places:

| Where | What to look for |
|---|---|
| The response from `curl` | The header `X-Correlation-Id: mi-hilo-1` |
| The logs of the router and of int-rest | `correlacion=mi-hilo-1` in their lines |
| The API's log | `docker compose -f infra/compose.yaml logs api-pedidos \| grep mi-hilo-1` |

If the same value shows up in all three, the thread is complete. Now send the order **without** the
`X-Correlation-Id` header. Which ID did the store get back? Look for it in the logs: it must be the same in all of
them.

When you finish, shut down the services:

```bash
DONDE=mio docker compose -f compose.yaml down
```

## 6. The most common mistake: reading it late

Try it: in int-rest, move your correlation `setVariable` so that it ends up **below** the `removeHeaders`. Shut down,
bring up again and send the order with `mi-hilo-1`.

The store gets its ID back, the router writes it down, but int-rest writes `correlacion=` (empty) and the API,
`correlacion=-`. By the time int-rest went to read the header, `removeHeaders` had already erased it. There is no
error and no alarm: **the thread is cut in silence**.

That is why the rule is: **whatever you need from the call that came in, save it in a variable before cleaning**.
Variables are not erased along with the headers. Put your step back in its place.

## 7. Check

```bash
./verifica
```

`verifica` restarts the API, brings up the two services and sends two orders: the 1003 with a thread of its own and
the 1001 with none. It is done when you see the eight green checks:

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

Where you see `…` the ID of that run appears: `verifica` uses a new one every time.

The seventh one checks something you did not write: the `ce-*` envelope is for the services on the inside, and the
router erases it before answering the store. The store only gets `X-Correlation-Id` back.

**Not working?** `./verifica solucion` tests ours, and these two commands tell you where they differ:

```bash
diff mio/orden-router/orden-router.camel.yaml solucion/orden-router/orden-router.camel.yaml
diff mio/int-rest/int-rest.camel.yaml solucion/int-rest/int-rest.camel.yaml
```

| What you see | What to do |
|---|---|
| `int-rest no anotó el hilo` | Step 1 is missing in the router, or step 2 ended up below the `removeHeaders` (step 6) |
| `La API no recibió el hilo` with everything else green | Step 3 is missing: int-rest has it, but does not pass it to the API |
| `El puerto 8080 está ocupado` | The services were left up: `DONDE=mio docker compose -f compose.yaml down` |

---

## Exercise: find an order

Bring up your version, send three orders (the 1001, the 1003 and the 1001 again), each one without
`X-Correlation-Id`, and write down the ID each one gave you back. Then, **with the ID of the second one only**,
answer: which order was it, what did the API answer and how long did it take from the moment it reached the router
until the API responded? Hint: every line of the log starts with the time.

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [CloudEvents in binary mode as the envelope](../decisiones/11-cloudevents-como-sobre.md)
- [The correlation thread travels with the order, but does not leave the house](../decisiones/12-el-hilo-de-correlacion.md)

## Review

1. What is the difference between `ce-id` and `ce-correlationid`?
2. If the store does not send an ID, who sets it and in what form?
3. Why does int-rest save the thread in a variable before the `removeHeaders`?
4. What did the API write when the thread did not reach it, and why is that worse than an error?
5. What use is it to the store to get its ID back?

**Next:** in lesson 10 the router stops sending the order only to int-rest: it sends it to the three destinations at
once, and the same thread has to reach the API, the ERP and the partner.
