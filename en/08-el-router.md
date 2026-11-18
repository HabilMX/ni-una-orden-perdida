# Lesson 08 — The router

> **What you will achieve:** split your integration into two services that run separately: a **router** that receives
> the order and an **int-rest** service that knows how to talk to the API. You write the call that joins them, by the
> service's name and never by an address.

**Before you start you need:** lessons 02 to 07 finished and the environment from lesson 00 turned on:

```bash
docker compose -f infra/compose.yaml up -d --wait     # desde la carpeta raíz de la práctica
```

## What is new today, in one sentence

Until now each lesson was **one** route that did everything: receive, translate and deliver. With three destinations
(the API, the ERP and the partner), that route would grow without end. Today you split it: one service **decides where
the order goes** and another **knows how to deliver it**. Each one runs in its own container.

```
tienda ──POST /ordenes──▶ [orden-router] ──POST http://int-rest:8080/integrar──▶ [int-rest] ──▶ API de pedidos
```

**The picture:** a hospital's front desk. The receptionist does not operate on anyone: she asks what is wrong with you
and sends you to the specialist. If a new specialty opens tomorrow, the front desk stays the same; it only learns one
more door.

---

## 1. Set up your folder

```bash
cd lecciones/08-el-router
cp -R inicio mio
```

Look at what you copied: `mio/` has **two folders**, one per service.

| Folder | Service | What it contains |
|---|---|---|
| `mio/orden-router/` | The router | Its route: it receives the order from the store and passes it to int-rest |
| `mio/int-rest/` | int-rest | Its route and the v2 mapping from lesson 03: it translates the order and delivers it to the API |

## 2. Read the lesson's compose file

This lesson does not use `herramientas/camel`. The two services are brought up together with their own `compose.yaml`,
like the environment from lesson 00. Open it:

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

Two things worth seeing:

- **The two services are the same Camel image.** The only thing that differs is the folder each one mounts. `&camel`
  defines the common part once and `<<: *camel` reuses it. To create a new service you do not have to write another
  program: another folder with its routes is enough.
- **Only the router has `ports`.** It is the only one visible from your machine. int-rest is not visible from outside,
  but inside the Docker network any service can call it; in this course, the only one that calls it is the router.

`DONDE` is a variable that says which folders to mount: `mio` (yours, by default) or `solucion` (ours).

## 3. Read the two routes

**`mio/int-rest/int-rest.camel.yaml`** already comes complete. It receives on `/integrar`, translates with the v2
mapping and calls the API, just like the route from lesson 03.

**`mio/orden-router/orden-router.camel.yaml`** receives on `/ordenes` and prepares the order to pass it on. Before the
call it adds some headers that start with `ce-`:

| Header | Value for order 1003 |
|---|---|
| `ce-specversion` | `1.0` |
| `ce-type` | `mx.tienda.orden.creada` |
| `ce-source` | `/tiendas/T-022` |
| `ce-subject` | `1003` |
| `ce-id` | `orden-1003-creada` |

They are the order's **envelope**, in a standard format called **CloudEvents**: they say what happened, where and to
whom. Today you are only going to **watch** them travel; in lesson 09 they are used to follow an order from service to
service.

At the end of the route there is a **TODO**: the call to int-rest is missing.

## 4. Predict

Before you write the call, answer on paper:

1. If you send order 1003 to the router as it is, without the call, what does it answer the store? Does anything reach
   the API?
2. Once the call is there, in what sequence do you expect to see the order go through the logs of the two services?
3. Inside the router's container, which machine do you think `localhost` points to?

## 5. Write the call

In place of the TODO, add a `to` step that makes a `POST` to the `/integrar` route of int-rest, on its port 8080.
Call it **by its service name**, `int-rest`, the way int-rest calls the API by `api-pedidos`. Use
`throwExceptionOnFailure: false` so that, if int-rest answers with an error, the router writes it down instead of
failing: the next line of the log says what it answered.

If you get stuck, the call from int-rest to the API has exactly the same shape.

## 6. Bring it up and send it an order

From the lesson folder:

```bash
DONDE=mio docker compose -f compose.yaml up -d
DONDE=mio docker compose -f compose.yaml logs -f
```

The second command shows the logs of the two services together, each line with the name of the one that wrote it.
Wait until you see `Started orden-router` and `Started int-rest`. In **another** terminal, from the root of the course:

```bash
curl -i -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' -d @datos/ordenes/orden-1003.json
```

In the logs you should see the order go through the two services:

```
orden-router-1  | Orden 1003: se la paso a int-rest
int-rest-1      | Recibí del router la orden 1003 (sobre orden-1003-creada, tipo mx.tienda.orden.creada)
int-rest-1      | La API contestó 201 para la orden 1003
orden-router-1  | Orden 1003: int-rest contestó 201
```

Does it match your prediction 2? The response comes back along the same path: the API answers int-rest, int-rest
answers the router and the router answers the store.

If the API answers `200` instead of `201`, it already had order 1003 from lesson 03. Restart it so that it starts empty:
`docker compose -f infra/compose.yaml restart api-pedidos`.

## 7. Break it on purpose: localhost

Now question 3. In the router's route, change `int-rest` to `localhost`:

```yaml
uri: http://localhost:8080/integrar
```

Shut the services down and bring them up again so that they pick up the change, and send the order again:

```bash
DONDE=mio docker compose -f compose.yaml down
DONDE=mio docker compose -f compose.yaml up -d
```

The store receives a **404** and the router's log says `int-rest contestó 404`. But int-rest never received anything.
**Inside a container, `localhost` is that same container.** The router called itself, looked for an
`/integrar` route that it does not have and answered itself "it does not exist". On your machine, `localhost` is your
machine; in each container, it is the container.

That is why services are called by their **name**: Docker knows which container `int-rest` lives in, even if tomorrow
it changes address. Put the name back and shut down:

```bash
DONDE=mio docker compose -f compose.yaml down
```

## 8. Check

```bash
./verifica
```

`verifica` restarts the API so that it starts empty, brings up the two services, sends order 1003 and at the end shuts
them down. It is done when you see the six green checks:

```
✅ El router arrancó
✅ int-rest arrancó (la misma imagen, otra carpeta)
✅ La tienda mandó la 1003 al router y la respuesta final fue 201
✅ La API registró la orden 1003: el recorrido tienda → router → int-rest → API se completó
✅ int-rest recibió el sobre CloudEvents de la orden (id orden-1003-creada)
✅ int-rest no se ve desde tu máquina: solo el router lo alcanza
```

The fourth one is confirmed by the API from its own records: it is not enough for the router to say that it passed the
order on.

**Not working?** `./verifica solucion` tests ours, and
`diff mio/orden-router/orden-router.camel.yaml solucion/orden-router/orden-router.camel.yaml` tells you where they
differ.

| What you see | What to do |
|---|---|
| `La tienda esperaba 201 y le llegó 200` | The router returned the order without calling anyone: the `to` step is missing |
| `La tienda esperaba 201 y le llegó 404` | The call points to `localhost`: go back to step 7 |
| `El puerto 8080 está ocupado` | Another route was left running, or this lesson's services: `DONDE=mio docker compose -f compose.yaml down` |
| `El router no arrancó` | `verifica` shows the last lines of the log: it is almost always the indentation of the new step |

---

## Exercise: one more specialist

Without writing code, answer on paper: if tomorrow you wanted an `int-erp` service that delivers the order to the ERP
with the route from lesson 05, which folder would you create, what would you put in it and which lines would you add
to the `compose.yaml`? Would you give it `ports`? What would change in the router?

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [A single image; each service is a folder](../decisiones/09-una-imagen-un-servicio-por-carpeta.md)
- [Services talk to each other by name, over HTTP](../decisiones/10-servicios-por-nombre-sobre-http.md)
- [CloudEvents in binary mode as the envelope](../decisiones/11-cloudevents-como-sobre.md)

## Review

1. What does the router do and what does int-rest do? Why is it better to separate them?
2. If the two services use the same image, what makes them different?
3. Why does the router call `int-rest` and not `localhost`?
4. Why does int-rest not have `ports` in the compose file?
5. What do the headers `ce-type`, `ce-subject` and `ce-id` of order 1003 say?

**Next:** in lesson 09 you follow an order from service to service with the CloudEvents envelope, and you discover
what happens when a service does not forward it.
