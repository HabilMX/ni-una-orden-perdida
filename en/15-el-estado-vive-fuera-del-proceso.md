# Lesson 15 — State lives outside the process

> **What you will achieve:** the router **stores** each order in its own database before answering the store, with one
> row per channel and a record of every attempt, and with that an order is not lost even if a destination is down.

**Before you start you need:** lesson 14 finished, the environment from lesson 00 turned on (the state database,
`estado`, is the one you brought up on the first day and that was not used until today) and your answer to the closing
question of lesson 12.

## What is new today, in one sentence

Up to lesson 14, the router received the order, called the three channels, waited and answered the store with what
each one said. If the router went down halfway through, nobody knew which destinations already had the order. From
today on the router **first writes it down and then distributes**: it stores the order in PostgreSQL, answers the
store **202 `recibida`** ("received") and, with the store now at ease, goes out to distribute.

**The picture:** the counter at a parcel carrier. They take your package, record it in the system and give you your
receipt. You do not stay at the counter until it reaches its destination. The receipt is good because **the package
has already been recorded**, not because the clerk remembers you. If the shift changes, whoever comes in reads the
record.

Go back to your answer from lesson 12: "if the router had replicas, where would what belongs to each order live?".
Today we answer it: **outside the process, in a database**. That way any replica of the router can read it, and if one
goes down, what was written is still there.

---

## 1. Set up your folder

```bash
cd lecciones/15-el-estado-vive-fuera-del-proceso
cp -R inicio mio
```

The router now has three files. Open them in this order:

| File | What it does | Do you write it? |
|---|---|---|
| `orden-router.camel.yaml` | Receives the order or the shipment, has it stored, answers 202 and queues the distribution | No, read it |
| `estado.camel.yaml` | Stores in the database. Here is `guardar-orden`, your work | **Yes** |
| `reparto.camel.yaml` | Reads from the database what is pending, writes down the attempt, calls the channel and writes down how it went | No, read it |

And open the database schema, `infra/postgres/01-estado.sql`. Three tables matter today:

| Table | One row per… | What matters |
|---|---|---|
| `orden` | order | Which lifecycle event it is at (`creada`, `embarcada`, `entregada`) and its `datos`, the order just as it arrived |
| `operacion` | order, lifecycle event and channel | What has to be done at each destination and how it ended up (`estado`: `pendiente`, `aceptada`, `rechazada`, `no_se_sabe`) |
| `intento` | time an operation was attempted | The fingerprint of what went out, the version of the mapping and the outcome |

A franchise order, when it is created, leaves **one** row in `orden` and **three** in `operacion`: `rest`, `bd` and
`edi`.

## 2. Predict

Answer on paper:

1. Today the router answers 202 even though `guardar-orden` stores nothing. What do you think happens to the order
   after the 202? (Hint: the distribution reads from the database what is pending.)
2. If the ERP is shut down when the order arrives, what should the router answer the store? And what should be left
   in the database?

Look at how it is today:

```bash
./verifica inicio
```

The store receives its 202, and after that nothing happens: the database does not have the order and no destination
receives it. **The router promised something it did not deliver.** It is the worst error in this course, because the
store believes everything went well.

## 3. Store before answering

Open `mio/orden-router/estado.camel.yaml` and look for `guardar-orden`. It has three TODOs. Your model is right below:
`guardar-momento` does the same for the shipment. Read it all before writing.

| TODO | What you write | What for |
|---|---|---|
| 1 | `transacted` with `ref: todoONada` | So that the order and its operations are stored **all or none** (the same idea as lesson 05, now in the state) |
| 2 | A `to` with `sql:INSERT INTO orden (orden, momento, datos) VALUES (…)` | The order's row: its number, the lifecycle event `'creada'` and the complete order as `jsonb` |
| 3 | A `split` over `${variable.canales.split(',')}` with an `INSERT INTO operacion …` inside | One operation per channel, in `'pendiente'`, with its thread. The partner's, with its control number |

Three hints:

- The variables you need already exist when this route is called: `orden`, `canales` and `correlacion`. The body is
  the order as the store sent it.
- To store the body as `jsonb`: `CAST(:#${body} AS jsonb)`. `:#${…}` is how the `sql` component passes a
  value as a parameter, without pasting it into the SQL text.
- The `INSERT INTO operacion` of `guardar-momento` works almost as is: what changes is the lifecycle event.

Notice the control number line:
`CASE WHEN :#${body} = 'edi' THEN CAST(nextval('numero_control_seq') AS text) END`. Only the partner's operation
asks the sequence for a number. **From today on the X12 control number is assigned by the router**, it no longer comes
from the order number as in lessons 06 and 13.

## 4. Break one on purpose

With your `guardar-orden` finished, shut down the state database and send the router an order:

```bash
DONDE=mio docker compose -f compose.yaml up -d
docker compose -f ../../infra/compose.yaml stop estado
curl -i -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' -d @../../datos/ordenes/tramo-2/orden-4003.json
```

Before sending it, predict: does the router answer 202?

It does not: it answers with an error. Without a database it cannot store, and **without storing it does not
promise**. The store sees the error and knows it has to try again. It is exactly the opposite of `inicio/`, which
answered 202 without storing. Turn everything back on:

```bash
docker compose -f ../../infra/compose.yaml start estado
DONDE=mio docker compose -f compose.yaml down
```

## 5. Check

```bash
./verifica
```

`verifica` sends the 4001 and its shipment, checks the database and the destinations, and at the end shuts down the
ERP and sends the 4002. It is done when you see the twelve green checks:

```
✅ Arrancaron el router (con su base de estado), los tres canales y el sondeo
✅ La tienda mandó la 4001 y el router contestó 202 «recibida»
✅ Estado: una fila de la orden 4001, con sus datos
✅ Estado: tres operaciones de la 4001, una por canal, y las tres aceptadas
✅ Estado: cada operación tiene su intento, con huella sha256, versión del mapeo y desenlace
✅ Destinos: la API tiene la 4001 CREADO y el ERP REGISTRADO
✅ Socio: el 850 de la 4001 lleva en el ISA el número de control de la base (…)
✅ Estado: el embarque de la 4001 tiene sus tres operaciones aceptadas y la orden va en «embarcada»
✅ Socio: el 856 lleva su propio número de control (…), distinto del 850 (…)
✅ Destinos: la API tiene la 4001 EMBARCADO
✅ Con el ERP caído, la tienda recibió su 202 por la 4002
✅ Estado: la operación del ERP quedó sin aceptar y está guardada (…); las otras dos siguieron
```

The control numbers change on every run: the sequence gives them out.

Look at the last two. With the ERP down, the store **did** receive its 202, because the router stored, and what did
not go out was written down: the `bd` operation of the 4002 is in `no_se_sabe` or `rechazada`, while the API and the
partner already have it. In lesson 14 that was lost in a log. Today it is in a row that can be queried.

**Not working?** `./verifica solucion` tests ours, and
`diff mio/orden-router/estado.camel.yaml solucion/orden-router/estado.camel.yaml` tells you where they differ.

| What you see | What to do |
|---|---|
| `no está la fila de la orden 4001` | The INSERT into `orden` is missing, or it does not carry `dataSource: "#estado"` |
| `esperaba «bd=aceptada edi=aceptada rest=aceptada» y hay «nada»` | The `split` of the operations is missing, or the INSERTs ended up outside it |
| `column "datos" is of type jsonb but expression is of type…` | You forgot the `CAST(:#${body} AS jsonb)` |
| `el 850 … no lleva el número de control de la base` | The `edi` operation was stored without `numero_control`: check the `CASE WHEN` |
| `El puerto 8080 está ocupado` | The services from step 4 were left up: `DONDE=mio docker compose -f compose.yaml down` |

---

## What this teaches beyond the course

**Answering "received" is a promise, and it is only kept if it has already been written down.** That is why the
router stores first. It is the same thing a bank does with a transfer: it gives you the reference number when the
transfer is already in its ledger, not when it has already arrived.

**State outside the process is what lets the router grow.** A process that remembers in its memory cannot be copied:
each replica would remember different things, and on restart it would forget. With the database, any replica reads the same
thing.

**Separating "I received" from "I delivered" changes the conversation.** The store no longer waits for the three
destinations to answer. In exchange, someone has to look at what was left pending: that is what part 3 builds.

## Exercise: the order in three tables

With your version finished, run `./verifica` and get into the database:

```bash
docker compose -f ../../infra/compose.yaml exec estado psql -U router -d estado
```

1. On paper, **draw** order 4001 in the three tables: one box for its row in `orden`, one for each `operacion`
   (creation and shipment) and one for each `intento`, with arrows showing which points to which.
2. Write the query that tells you, for the 4002, which state each channel is in. Start with
   `SELECT canal, estado FROM operacion WHERE orden = '4002'` and add the lifecycle event to it.
3. Which control number does the 850 of the 4001 have, and which did it have in lesson 06? Where does each one come
   from, and why does one given out by the database never repeat even if two orders arrive at the same time?

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [Store before answering](../decisiones/18-guardar-antes-de-contestar.md)
- [The X12 control number is assigned by the router](../decisiones/19-el-numero-de-control-lo-asigna-el-router.md)

## Review

1. What does each of the three tables store, `orden`, `operacion` and `intento`?
2. Why does the router store the order **before** answering 202, and not after?
3. What is the transaction in `guardar-orden` for? What would happen without it if the second INSERT failed?
4. With the ERP down, what did the store receive and what was left in the database? What happened to that same order
   in lesson 14?
5. **The closing question:** from today on the X12 control number is assigned by the router. Where did it come from in
   lesson 06, and what changed? Compare with your answer from lesson 12 about where what belongs to each order lives.

**Next:** in lesson 16 the third lifecycle event arrives: the carrier reports that it delivered. Nobody asks it: it calls by
itself, through a webhook. What has to happen in the state and on the SFTP?
