# Lesson 17 — The delivery arrived before the tracking number

> **What you will achieve:** a delivery that arrives **before** its shipment is neither lost nor sent at the wrong
> time: it stays parked in the database and goes out by itself, only once, as soon as the shipment arrives.

**Before you start you need:** lessons 15 and 16 finished and the environment from lesson 00 turned on.

## What is new today, in one sentence

In lesson 16 everything arrived in order: the order was created, then it shipped and at the end it was delivered. But
the warehouse and the carrier do not talk to each other, and sometimes the delivery notice **arrives before** the
shipment file. If the router distributes it like that, the API and the ERP reject it: something that, for them, never
shipped cannot be delivered. And if the router throws it away, the customer never shows up as served. Today you
**park** it and **release** it when it can go out.

**The picture:** the parking lot of a hospital. A transfer ambulance arrives, but the patient's bed is not ready yet.
They do not send it back, nor do they put the patient in an occupied room: it parks. And as soon as someone reports
"the bed is ready", the ambulance goes through. Nobody goes out every five minutes to check the parking lot: what
moves the ambulance is the notice.

---

## 1. Set up your folder

```bash
cd lecciones/17-la-entrega-llego-antes-que-la-guia
cp -R inicio mio
```

Today the router brings new pieces. Almost all of them are already done: your work is the last one.

| Piece | Where | Do you write it? |
|---|---|---|
| The state machine: which lifecycle event needs which, on each channel | `mio/orden-router/reglas/maquina-de-estados.yaml` | No, read it |
| The decision: with the machine and what is stored, it says `procesa`, `estaciona` or `invalido` | `direct:decidir`, with its mapping in `mappings/estado/decision/v1/` and its golden test cases | No, read it |
| The parking: when a lifecycle event is stored, each operation is born `pendiente` or `estacionada` | `guardar-momento`, in `estado.camel.yaml` | No, it comes ready |
| **The sweep**: releases what is parked and can now go out | `barrido.camel.yaml` | **Yes** |

Open the state machine. It reads like this: on the `rest` channel, the lifecycle event `entregada` needs `embarcada` to be
already **accepted on that same channel**. It goes per channel because each destination keeps its own order: the API
already having the shipment says nothing about the ERP.

Also open a golden test case of the decision, `cases/entrega-sin-embarque/`: its `entrada.json` and its `esperado.json`.
It is the same idea as lesson 03: the rule is tested with examples, not with faith.

## 2. Predict

Answer on paper:

1. The delivery of the 4002 arrives, and its shipment has not yet. What has to end up in the database? What does each
   destination have to receive?
2. Then the shipment arrives. Who notices that the delivery can now go out? When?
3. If the sweep runs twice, could the delivery go out twice? What would prevent it?

Look at how it is today:

```bash
./verifica inicio
```

The delivery is parked correctly, but the sweep releases nothing: even though the shipment arrives, the delivery stays
parked forever. The 4002 never shows up as ENTREGADO.

## 3. Write the sweep

Open `mio/orden-router/barrido.camel.yaml`. At the top is `barrido-http`, the door for asking for a sweep by hand with
`POST /barrido`; it is already done. Your work is the route `barrido`, with four TODOs:

| TODO | What you write | What for |
|---|---|---|
| 1 | A `to` with `sql:SELECT id, orden, momento, canal FROM operacion WHERE estado = 'estacionada' ORDER BY id` | Fetch what is parked, in the order in which it arrived |
| 2 | A `split` over `${body}`; inside, four variables (`operacion`, `orden`, `momento`, `canal`, from `${body[id]}`, `${body[orden]}`…) and a call to `direct:decidir` | Decide each one again with what is in the database today |
| 3 | If `${variable.decision} == 'procesa'`: `UPDATE operacion SET estado = 'pendiente' WHERE id = … AND estado = 'estacionada'` | Move it to pending, **only if it is still parked** |
| 4 | If `${header.CamelSqlUpdateCount} == 1`: to `seda:repartir` with `pattern: InOnly` | Queue the distribution, **only if you were the one who changed it** |

For the "if…" use a `filter`: it lets the message through only when the condition is met. TODO 4 goes inside the
`filter` of TODO 3.

The `AND estado = 'estacionada'` of TODO 3 is the heart of the lesson. Imagine two sweeps at the same time: both read
the parked delivery and both decide `procesa`. The first one changes it: the database answers "I changed 1 row". The
second one arrives late, and since it is no longer parked, the database answers "I changed 0". Only the one that
changed 1 queues it. **The database decides who wins**, not luck.

And who calls the sweep? Open `reparto.camel.yaml` and look for `direct:barrido`, almost at the end: it is called
**every time a channel accepts a lifecycle event**, because that is the only thing that can unblock another one. There is no
clock that triggers it every so often.

## 4. Break one on purpose

With your sweep finished, comment out in `reparto.camel.yaml` the step that calls `direct:barrido`. Bring up the
services, create the 4002, send its delivery and then drop the warehouse batch, which carries its shipment:

```bash
DONDE=mio docker compose -f compose.yaml up -d
curl -s -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' -d @../../datos/ordenes/tramo-2/orden-4002.json
curl -s -XPOST localhost:8080/webhooks/paqueteria -H 'Content-Type: application/json' -d @../../datos/webhooks/entrega-4002.json
cp ../../datos/embarques/lote-bodega-0001.json buzon/
```

The batch carries shipments of other orders that you did not create today; the router rejects them with 404, and that
is fine. Wait a few seconds and ask the database:

```bash
docker compose -f ../../infra/compose.yaml exec estado psql -U router -d estado \
  -c "SELECT momento, canal, estado FROM operacion WHERE orden = '4002' ORDER BY id"
```

First, predict: which state is the delivery in?

The shipment is already `aceptada` on the three channels, but the delivery is still `estacionada`. Nobody reported
that something changed. Now ask for the sweep by hand and ask again:

```bash
curl -s -XPOST localhost:8080/barrido
```

Now it does go out. That is what the event does: report at exactly the right moment. Without it, someone would have to
ask for it, or a clock would have to check the database every so often even if nothing has happened. Put the call back
and shut down:

```bash
DONDE=mio docker compose -f compose.yaml down
```

## 5. Check

```bash
./verifica
```

`verifica` creates the 4002, sends its delivery **before** its shipment, checks that it was parked, drops the shipment
in the mailbox and, at the end, asks for a sweep by hand to check that nothing goes out twice. It is done when you see
the ten green checks:

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

The fourth and the last are the ones that matter most. What is parked **has no attempts**: it did not go out anywhere.
And the extra sweep repeated nothing.

**Not working?** `./verifica solucion` tests ours, and
`diff mio/orden-router/barrido.camel.yaml solucion/orden-router/barrido.camel.yaml` tells you where they differ.

| What you see | What to do |
|---|---|
| `después del embarque, embarque y entrega quedaron` … with the delivery `estacionada` | TODO 3 or 4 is missing, or the `filter` compares `decision` with another word: it is `'procesa'` |
| `${body[id]}` comes out empty | The `SELECT` does not bring the `id` column, or the variables ended up outside the `split` |
| `Esperaba el barrido a mano en 200, 3 intentos de la entrega y un 201` and there are more | The `UPDATE` does not carry `AND estado = 'estacionada'`, or you did not filter by `CamelSqlUpdateCount` |
| Nothing is queued even though the UPDATE changed the row | The `to` to `seda:repartir` ended up outside the second `filter`, or `pattern: InOnly` is missing |
| `El puerto 8080 está ocupado` | The services from step 4 were left up: `DONDE=mio docker compose -f compose.yaml down` |

---

## What this teaches beyond the course

**The order in which things arrive is not the order in which they happened.** Between systems that do not coordinate,
one notice can get ahead of another. The pattern that solves it is called *Resequencer*: store what arrived out of
order and release it in the right order.

**The rule of who goes first lives in a single place.** It is in a YAML that is read as data. Adding a new lifecycle event, a
return for example, means adding a line, not another route.

**When two processes compete for the same thing, let the database decide.** The `UPDATE … WHERE … AND estado = …` is a
kind of lock: only one can move the row from one state to another. You will find this idea in almost any system that
hands out work.

## Exercise: the rule as data

With your version finished:

1. In the state machine, what would you have to add for a new lifecycle event, `devuelta`, that needs the delivery? Write it.
   Would you have to touch any route?
2. Open the five golden test cases of the decision. For `entrega-con-embarque-pendiente`, why is the decision to park, if
   the shipment **has already arrived**?
3. In the database, after `verifica`, count the attempts of the delivery of the 4002:
   `SELECT o.canal, COUNT(i.id) FROM operacion o LEFT JOIN intento i ON i.operacion = o.id WHERE o.orden = '4002' AND o.momento = 'entregada' GROUP BY o.canal`.
   How many do you expect per channel, and why are there not two even though the sweep ran twice?

---

## What already works

With this lesson you close part 2. Besides what part 1 gave you, an order already does this:

- it ships from a warehouse file, with or without a batch, without the file being taken half-written or twice;
- it is stored before answering, with one row per channel and a record of every attempt;
- it is delivered from the carrier's notice, which receives a 202 as soon as it has been stored;
- and if the delivery arrives before the shipment, it waits parked and goes out only once, as soon as it can.

What it does not do yet: recognize a message that arrives repeated with different content, or retry what was left in
`no_se_sabe`. That is part 3.

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [What arrives ahead of time is parked, not rejected](../decisiones/21-estacionar-lo-que-llega-antes.md)

## Review

1. What does it mean for an operation to be `estacionada`, and how does it differ from `pendiente`?
2. What does the state machine say about the delivery, and why does it go per channel?
3. What is the `AND estado = 'estacionada'` in the sweep's UPDATE for?
4. Why does the sweep check `CamelSqlUpdateCount` before queuing?
5. **The closing question:** the sweep is triggered by an event, a channel accepting a lifecycle event, and not by a timer that
   checks the database every minute. What do you gain with the event? What would you lose with the timer, and in which
   case would you actually need one?

**Next:** in lesson 18 part 3 begins, "make it not fail". First, a question that looks easy: when are two messages the
same one?
