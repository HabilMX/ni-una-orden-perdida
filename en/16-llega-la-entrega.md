# Lesson 16 — The delivery arrives

> **What you will achieve:** receive the notice the carrier sends when it delivers a package, so that the order ends up
> ENTREGADO in the API and in the ERP, and the partner receives its 214. And tell a notice like that apart from a
> request.

**Before you start you need:** lesson 15 finished and the environment from lesson 00 turned on.

## What is new today, in one sentence

You already have two lifecycle events of the order: it is **created** (the store calls) and it is **shipped** (the warehouse
drops a file). Today the third one arrives: it is **delivered**. And it arrives in a third way: the carrier **calls
you by itself** when it has delivered, at an address of yours. That is a **webhook**, and what it carries is an
**event**: it does not ask you for anything, it tells you that something has already happened.

**The picture:** the carrier's message on your phone, "Your package was delivered". It does not ask you to do
anything, nor does it expect you to answer what you are going to do. It only wants to know that the message reached
you. What you do afterwards (telling whoever was waiting for it, checking that it is your package) is your business.

---

## 1. Set up your folder

```bash
cd lecciones/16-llega-la-entrega
cp -R inicio mio
```

Open the sample notice, `datos/webhooks/entrega-4001.json`. It carries the event (`entregado`), the carrier, **the
tracking number**, the order, when it was delivered and who received it.

Everything on the outbound side is already done. Each channel has its `entrega.camel.yaml`:

| Channel | What it does with the delivery |
|---|---|
| int-rest | Tells the API that the order was delivered, as an event with its key |
| int-bd | Records the delivery in the ERP and moves the order to ENTREGADO |
| int-edi | Builds a **214**, the X12 shipment status, with the tracking number in the `B10` segment, and drops it on the SFTP |

The distribution from lesson 15 already knows how to send the lifecycle event `entregada`. **The door is missing.**

## 2. Predict

Answer on paper:

1. The carrier reports that it delivered the 4001. What has to end up in the state database, and what at each
   destination?
2. What does the router answer the carrier: what the API, the ERP and the partner said, or only "received"? Why?
3. A notice arrives for order 9999, which the store never created. What should the router answer?

Look at how it is today:

```bash
./verifica inicio
```

The webhook answers **501**, which in HTTP means "I do not know how to do that", and stores nothing. The 4001 stays
in EMBARCADO forever, even though the customer already has the package.

## 3. Open the door

Open `mio/orden-router/paqueteria.camel.yaml`. The route `webhook-paqueteria` listens on
`POST /webhooks/paqueteria`, and today it only answers 501. Your model is the `embarque-router` in
`orden-router.camel.yaml`: it does almost the same for the shipment. Read it before you start.

| TODO | What you write | What for |
|---|---|---|
| 1 | `convertBodyTo` to `String` | So that the notice is stored just as it arrived |
| 2 | Two variables: `orden` (from `$.orden`, with `jsonpath`) and `momento` (`entregada`). And call `direct:hilo` | To know which order it belongs to and which lifecycle event it is, with its thread from lesson 09 |
| 3 | Call `direct:cargar-orden`. With a `choice`: if `${variable.tienda} == null`, to `direct:no-existe`; otherwise, to `direct:canales`, `direct:guardar-momento` and `direct:contestar-y-repartir` | An order we do not know is rejected; one we do know is stored, answered and distributed |

And delete the two steps that answer 501 today.

Notice that **you write nothing fundamentally new**: `cargar-orden`, `canales`, `guardar-momento` and
`contestar-y-repartir` have existed since lesson 15. The delivery comes in through a different door, but inside it
follows the same path as the order and the shipment: **store, answer, distribute**.

## 4. Break one on purpose

With your webhook finished, remove the `choice` and leave only what is in the `otherwise` branch: as if every order
existed. Bring up the services and send a notice for an order that does not exist:

```bash
DONDE=mio docker compose -f compose.yaml up -d
curl -i -XPOST localhost:8080/webhooks/paqueteria -H 'Content-Type: application/json' \
  -d '{"evento":"entregado","paqueteria":"PAQX","guia":"PAQX770009999","orden":"9999"}'
```

First, predict: what does the router answer?

It no longer answers 404, it answers a server error. `guardar-momento` tries to create the operations of an order that
is not in the `orden` table, the database prevents it and the transaction is rolled back. Now think about the carrier:
faced with a server error, **a webhook sender sends the notice again**, and it will keep sending it. The 404 tells the
truth, "I do not know that order", and the sender stops insisting. Put the `choice` back and shut down:

```bash
DONDE=mio docker compose -f compose.yaml down
```

## 5. Check

```bash
./verifica
```

`verifica` creates the 4001, ships it, plays the carrier with `entrega-4001.json` and at the end sends a notice for
the 9999. It is done when you see the nine green checks:

```
✅ Arrancaron el router (con el webhook de la paquetería), los tres canales y el sondeo
✅ Antes de la entrega: la 4001 está EMBARCADO en la API y en el ERP
✅ La paquetería avisó la entrega de la 4001 y el router contestó 202 «recibida»
✅ Estado: tres operaciones de la 4001 con momento=entregada, aceptadas, y la orden va en «entregada»
✅ API: la 4001 está ENTREGADO, con la guía con la que salió
✅ ERP: la 4001 está ENTREGADO y su fila de entrega tiene la guía
✅ Socio: el 214 de la 4001 está en su SFTP y el lector X12 no le encontró fallas
✅ Socio: el 214 lleva la guía en el B10 y el número de control de la base (…)
✅ Un aviso de la orden 9999, que no existe: el router contestó 404 y no guardó nada
```

Open the 214 that reached the partner, `mio/salida/214-4001.edi`, and look for the tracking number in the `B10`
segment.

**Not working?** `./verifica solucion` tests ours, and
`diff mio/orden-router/paqueteria.camel.yaml solucion/orden-router/paqueteria.camel.yaml` tells you where they
differ.

| What you see | What to do |
|---|---|
| `Esperaba 202 del webhook y el router contestó` … 501 | The two steps from `inicio/` that answer 501 were left in: delete them |
| `Un aviso de una orden que no existe contestó «500»` | The `choice` is missing, or the condition is not `${variable.tienda} == null` |
| `las operaciones de la entrega quedaron «sin operaciones»` | The variable `momento` is not called that or is not set to `entregada`, or `direct:guardar-momento` is missing |
| The 4001 was stored, but no destination has it | `direct:contestar-y-repartir` is missing at the end |
| `El puerto 8080 está ocupado` | The services from step 4 were left up: `DONDE=mio docker compose -f compose.yaml down` |

---

## What this teaches beyond the course

**An event and a request are answered differently.** A request ("create this order") is answered with whether it could
be done. An event ("I already delivered") is only answered with the fact that it arrived: what happened has already
happened, and it does not depend on you. That is why the router answers 202 and distributes afterwards.

**Whoever sends a webhook waits little and retries.** Senders count as delivered only what received a 2xx in time, and
send everything else again. Answering as soon as you have stored takes care of both things: it does not keep anyone
waiting and it does not cause extra retries.

**The response code is an instruction for the sender.** 202 is "I have it now", 404 is "do not insist, that does not
exist" and a server error is "try again later". Choosing the wrong code changes what the other system does.

## Exercise: three doors, one path

With your version finished:

1. Make a table with the three lifecycle events: created, shipped and delivered. For each one: who sends it, where it comes in
   (HTTP from the store, file from the warehouse, webhook) and which route of the router receives it. What do the
   three routes have in common after the entrance?
2. In the state database, query the operations of the 4001 for the three lifecycle events:
   `SELECT momento, canal, estado FROM operacion WHERE orden = '4001' ORDER BY id`. How many rows are there, and why?
3. What would happen if the carrier reported the delivery of the 4002 **before** its shipment arrives? Write down your
   answer: it is lesson 17.

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [The carrier's webhook answers 202 as soon as it has stored](../decisiones/20-el-webhook-contesta-202.md)

## Review

1. What is a webhook, and how does it differ from the way the order arrives and from the way the shipment arrives?
2. What is a 214, and what does it carry in its `B10` segment?
3. Why does the router answer the carrier 202 and not what the API, the ERP and the partner said?
4. Why is a notice for an order that does not exist answered with 404 and not with a server error?
5. **The closing question:** "I delivered the package" is an event; "create this order" is a request. With one example
   from the course for each, explain how they differ and how what the router answers changes.

**Next:** in lesson 17 the world gets out of order: the delivery of the 4002 arrives before its tracking number. The
router cannot distribute it yet, but it cannot throw it away either. What do you do with it?
