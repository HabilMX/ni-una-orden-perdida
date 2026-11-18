# Lesson 30 — Do not lose when publishing

> **What you will achieve:** closing the last gap in the course. If Kafka is down right when an order arrives, the
> order is stored, the store receives its `202` and, when Kafka comes back, the order goes out by itself to its three
> channels, without anyone doing a rerun.

**Before you start you need:** lessons 17 and 29 finished, the environment from lesson 00 turned on and Kafka up
(`docker compose -f infra/compose.yaml --profile kafka up -d --wait`).

## What is new today, in one sentence

Since lesson 28 the router does two things in a row: it **stores** the order in the state database and **then**
publishes the event to Kafka. They are two different systems, and no transaction spans both. If Kafka is down at that
instant, the order was stored and the store already has its `202`, but the event did not go out, and nobody tries it
again. The other way around does not work either: publishing before storing leaves an event for an order that, if the
database fails, does not exist. Today the router stores **what has to be published** in the same transaction as the
order, in a table called **outbox**, and a poller publishes it when Kafka can receive it. The pattern is called
*Transactional Outbox*.

**The picture:** the "to be sent" tray in an office. Whoever serves the customer does not run out to the carrier with
each envelope: they fill in the form and, **at the same moment**, leave the envelope in the tray. The courier comes by
every so often and takes whatever is there. If one day the carrier is closed, the envelopes stay in the tray, not in
anyone's pocket, and they go out when it opens.

```
antes:  guardar (transacción) → 202 → publicar en Kafka           ← si Kafka está caído aquí, se pierde
hoy:    guardar orden + operaciones + outbox (UNA transacción) → 202
        cada 1 s: outbox pendiente → publicar → ¿Kafka lo recibió? sí → «publicado» · no → espera la siguiente vuelta
```

---

## 1. Set up your folder

```bash
cd lecciones/30-no-perder-al-publicar
cp -R inicio mio
```

Open `infra/postgres/01-estado.sql` and find `CREATE TABLE outbox`: each row points to an `operacion` and has a
`publicado` column, empty until Kafka has received the event.

The poller is already written: it is the `sondeo-outbox` route in `mio/orden-router/reparto.camel.yaml`. Read it.
Every second it takes what is pending **in the sequence in which it came in** and, if something does not go out, what
follows waits for the next round (`stopOnException`): that way the events of a single order do not go out of
sequence. The sweep from lesson 17 already leaves in the outbox whatever it releases.

Your work goes in two files:

| TODO | File | What you do |
|---|---|---|
| 1 | `mio/orden-router/estado.camel.yaml` | When storing a new order, leave in the outbox one row for each operation (lifecycle event `creada`) |
| 2 | The same one | When storing a new lifecycle event (shipment, delivery), the same for the operations that were born `pendiente` |
| 3 | `mio/orden-router/orden-router.camel.yaml` | Remove the direct send to `seda:repartir`: from today on, only the outbox publishes |

## 2. Predict

Answer on paper:

1. With the version from lesson 29, Kafka is down and orders 4002 and 4003 arrive. Does the store receive its `202`?
   How many rows are there in the outbox? What happens to the orders when Kafka comes back?
2. Why does the outbox row have to be written in the **same** transaction as the operation, and not right after?
3. If you leave the direct send and also the outbox, what happens to each lifecycle event? Would the API create it twice?

Look at how it is today:

```bash
./verifica inicio
```

With Kafka down, the router stores the two orders and answers `202`, but the outbox stays **empty**. When Kafka comes
back, the 6 operations are still pending: nobody sends them again.

## 3. The "to be sent" tray

### TODO 1: the new order

In `mio/orden-router/estado.camel.yaml` find TODO 1. It is inside the transaction that stores the order and its
operations. Right there, an `INSERT … SELECT` leaves one row for each operation that was just stored:

```yaml
- to:
    uri: "sql:INSERT INTO outbox (operacion) SELECT id FROM operacion WHERE orden = :#${variable.orden} AND momento = 'creada' ORDER BY id"
    parameters:
      dataSource: "#estado"
```

The `ORDER BY id` makes the rows come in in the sequence in which the operations were born, and the poller publishes
in that sequence.

### TODO 2: a new lifecycle event

Further down, in the same transaction that stores the shipment or the delivery, is TODO 2. It is the same `INSERT`,
with two differences:

| | TODO 1 | TODO 2 |
|---|---|---|
| Which lifecycle event | `'creada'` | `:#${variable.momento}` |
| Which operations | All | Only the ones that were born `pendiente`: `AND estado = 'pendiente'` |

Why only the pending ones? Because the `estacionada` ones (lesson 17) cannot go out yet. Those are left in the outbox
by the sweep, when it releases them.

### TODO 3: a single path

In `mio/orden-router/orden-router.camel.yaml`, in the `contestar-y-repartir` route, remove the `to: seda:repartir`
step. If you leave it, each lifecycle event goes out by **two** paths: the direct one and the outbox. The API would recognize
it by its key (lesson 18), but each channel would receive two events and the router would record two attempts. A
single path is easier to explain and to trace.

## 4. Break one on purpose

With your version finished, delete the `INSERT` from TODO 1 and run `./verifica`.

First, predict: Kafka is up at the beginning. Does order 4001 arrive?

No. `verifica` stops at the second check: order 4001 does not reach any of its channels. You already removed the
direct send (TODO 3), so the outbox is **the only path**, and an order that does not leave its row is published by
nobody. Before the outbox, "Kafka is up" was enough; now what rules is the stored row. Put it back.

## 5. Check

```bash
./verifica
```

`verifica` sends order 4001 with everything up, **stops Kafka**, sends orders 4002 and 4003, checks the outbox and
the API for a few seconds, brings Kafka back up and waits for everything to go out. It is done when you see the eight
green checks:

```
✅ Arrancaron el router, los tres canales con su puente y el sondeo
✅ La 4001 llegó a sus tres canales
✅ Con Kafka caído, el router guardó la 4002 y la 4003 y contestó 202 a las dos
✅ En el outbox esperan 6 filas sin publicar, una por operación de la 4002 y la 4003
✅ Control: mientras Kafka está caído, la API no tiene ninguna de las dos
✅ Kafka volvió y lo guardado salió solo: la API, el ERP y el socio tienen la 4002 y la 4003
✅ En la API se creó cada una UNA vez (2 creaciones para 2 órdenes)
✅ El outbox quedó sin pendientes: todo lo que se guardó, salió
```

The fifth one is a **control**: if the API already had the orders with Kafka down, the sixth check would not prove
that the outbox got them out.

Look at the outbox yourself, with the services up:

```bash
docker compose -f ../../infra/compose.yaml exec estado psql -U router -d estado \
  -c "SELECT x.id, p.orden, p.canal, x.publicado FROM outbox x JOIN operacion p ON p.id = x.operacion ORDER BY x.id"
```

**Not working?** `./verifica solucion` tests ours, and `diff -r mio solucion` tells you where they differ.

| What you see | What to do |
|---|---|
| `A la base de estado le falta la tabla outbox` | Your database is from before lesson 30: recreate it with the command that comes in the message (`--force-recreate -V estado`) |
| `La 4001 no llegó a sus tres canales` | TODO 1 is missing: with TODO 3 done, without a row in the outbox nothing is published |
| `En el outbox esperaba 6 filas sin publicar … y hay 0` | The `INSERT` from TODO 1 is not running: check that it is inside the transaction, after the operations are stored |
| `Kafka volvió y lo guardado no salió` | Look at which channel was left in which state (the message says so) and check the `sondeo-outbox` route in the router's log |
| `En la API hubo … creaciones para 2 órdenes` | The API created one of them twice: did you change the key that int-rest sends it? (lesson 18) |
| `El outbox tiene … filas sin publicar` | Something got stuck: check the router's log, below `sondeo-outbox` |
| `El puerto 8080 está ocupado` | The services from another run were left up: `DONDE=mio docker compose -f compose.yaml down` |

---

## What this teaches beyond the course

**Two systems do not share a transaction.** The database and Kafka each commit their own part. If an action has to
end up in both, it is stored in only one (the database, with the order) and the other is fed from there.

**What is stored is what goes out.** The poller publishes what the outbox says, reading the database. If the router is
restarted halfway, the row is still there.

**Publishing too much is tolerated; publishing too little is not.** If the router goes down in the middle of the
send, or after publishing and before marking `publicado`, the row goes out again. That is why whoever reads has to
recognize repeats (lessons 18 and 29). Losing an event, on the other hand, is noticed by nobody until a customer
complains.

**A single path.** Keeping the direct send "just in case" next to the outbox duplicates everything and fixes nothing:
the outbox already covers the case in which Kafka is up.

## Exercise: the tray

1. Stop Kafka with your services up, send an order and run the query from step 5. What does the `publicado` column
   say? Bring Kafka up and run it again.
2. On paper: the router stored the order and its outbox row, the poller published it and the router went down before
   marking `publicado`. What happens when it comes back? Which piece keeps the API from creating it twice?
3. Make a list of the ways of losing an order that you saw in the course (the store that does not receive the `202`,
   the ERP down, the 997 that does not arrive, the event committed without an outcome…). Next to each one, the lesson
   that closes it.

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [What has to be published is stored in the same transaction (outbox), and a poller publishes it](../decisiones/34-el-outbox-publica-lo-guardado.md)

## Review

1. What gap is left between storing and publishing, and why is it not fixed by publishing first?
2. What does the `outbox` table store, and in which transaction is it written?
3. Why does TODO 2 leave out the `estacionada` operations?
4. What would happen if you left the direct send next to the outbox?
5. **The closing question:** a store sends an order with Kafka down. Tell, step by step, what happens from the `202`
   until the partner has its 850, and in which tables an audit trail is left.

**Next:** with this lesson the course in Docker Compose ends. If your team works with Kubernetes or wants to see the
integration without containers, the appendix (lessons 31 and 32) is for reading and is optional.

---

> **What Hábil does**
>
> At the close of this course:
> - a repeated notice duplicates nothing;
> - if it is not known whether something arrived, it stays pending reconciliation instead of being counted as failed;
> - what was dropped is not considered confirmed until the partner acknowledges receipt;
> - a channel that is down does not hold back the others;
> - each destination receives only the calls it can handle;
> - each order can be followed without showing personal data.
>
> In a company, this usually sounds like: "We do not know whether that operation arrived. It gets retried by hand,
> and sometimes it is duplicated." And there are failures that raise no error: the send went out, but the response
> never arrived.
>
> At Hábil we build to measure the integration between your store, your ERP, your partners and your channels.
> **[Hábil MDW](https://www.habil.mx/es/habil-mdw/?utm_source=github&utm_medium=referral&utm_campaign=2026-11_ni-una-orden-perdida_es)**
> is designed to operate 24/7, with an audit trail of each operation: what was sent to each system, what it answered, at what
> time and, if it failed, why. Each send is left with its response or with its reason for failure, and the retries are
> designed not to repeat the effect. A send that went wrong is retried by your operations team as a new send, with a
> audit trail of who did it and without touching the database by hand.
>
> **[Ask for your first conversation, at no cost.](https://wa.me/525667541323?text=Hola%2C%20vengo%20de%20GitHub%20%28Ni%20una%20orden%20perdida%29.%20Me%20interesa%20una%20primera%20conversaci%C3%B3n.%0AIndustria%3A%0APuesto%3A%0ASistemas%20principales%3A)**
