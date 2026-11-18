# Lesson 25 — Waiting for the channels

> **What you will achieve:** knowing when an order is **complete**: when every channel it applies to has reached what
> is expected of it, including the partner's 997. And having a channel that takes too long raise an **alert** without
> the order being treated as closed, so that whatever arrives late still completes it, even if the router was
> restarted in the middle.

**Before you start you need:** lessons 21 and 22 finished and the environment from lesson 00 turned on.

## What is new today, in one sentence

Since lesson 10, the router **scatters**: it sends each lifecycle event of the order to its channels. It never **gathers**:
there is no place that says "this order is finished". And in lesson 21 you saw that "finishing" is not the same for
everyone: the API and the ERP finish when they accept, the partner when it sends its 997. Scattering and then
gathering the responses is a pattern with a name, *Scatter-Gather*, and the gathering part is called *Aggregator*.
Today you build it, with an idea that comes from lesson 15: **what has to be gathered is already in the database.**

**The picture:** organizing the New Year's Eve dinner with three cousins who each bring something. The aunt does not
consider the dinner ready when each cousin has said "on my way": she considers it ready when the food is on the table.
If at nine the one with the dessert has not arrived, the aunt does not cross out the dessert and declare the dinner
complete: she calls him to ask. And if the dessert arrives at nine thirty, the dinner is complete then. If the aunt
steps out to the patio for a while, she does not lose count: the table is still there with what has already arrived.

---

## 1. Set up your folder

```bash
cd lecciones/25-esperar-a-los-canales
cp -R inicio mio
```

First open `mio/orden-router/reglas/maquina-de-estados.yaml` and find the `completa` section. That is where you find
what is expected of each channel and how long it is waited for:

| Channel | `llega_a` | `plazo_s` | Why |
|---|---|---|---|
| `rest` (the API) | `aceptada` | 10 | Its "yes" arrives in the same call |
| `bd` (the ERP) | `aceptada` | 10 | The same |
| `edi` (the partner) | `confirmada` | 20 | Its "yes" is the 997, and it arrives later (lesson 21) |

Now open `mio/orden-router/juntar.camel.yaml`. At the top there is a drawing of order 4001 halfway there: the API and
the ERP have already arrived, the partner has not yet. And there is a route, `vigilar`, already written: a **clock**
(`timer`) that every 2 seconds reads the rules and calls two routes:

| Route | What it does | Where it records it |
|---|---|---|
| `juntar` | Marks as COMPLETE each lifecycle event whose channels have all reached their `llega_a` | Table `completitud`, one row per order and lifecycle event |
| `vencidos` | Raises an alert for each channel that passed its deadline without arriving | Table `alerta` and a `WARN` in the log |

Why a clock, instead of waiting for something to give notice? Because **what does not arrive gives no notice**. A 997
that never comes does not fire any event. Someone has to watch the clock.

Notice what is not there: there is no aggregator keeping in the router's memory which channels have already answered.
Each operation and its state have lived in the `operacion` table since lesson 15. **Gathering is asking the
database.**

## 2. Predict

Answer on paper:

1. The API and the ERP accepted order 4001, and the 850 was dropped. The partner has not sent the 997. Is the order
   complete?
2. Order 3001 comes from a company-owned store: it does not carry EDI (lesson 11). When is it complete?
3. 20 seconds go by and the 997 for order 4002 does not arrive. Which is better: treating the order as complete so
   that it stops getting in the way, or raising an alert and leaving it open? What happens in each case if the 997
   arrives at 30 seconds?
4. You restart the router while order 4002 is waiting for its 997. Is what had already been gathered lost?

Look at how it is today:

```bash
./verifica inicio
```

It goes wrong from the first check: order 4001 ends up COMPLETE **before** its 997, because the version in `inicio/`
marks a lifecycle event as complete when no channel is still `pendiente`, and a dropped 850 is `aceptada`. And order 4002, on
passing its deadline, is closed by force: it ends up complete without the partner having answered anything.

## 3. Gather and watch

In `juntar.camel.yaml` there are two TODOs, one in each route. Both are a SQL query that is already almost whole.

### TODO 1: complete means "every channel reached its own goal"

The query in `juntar` joins each operation with its channel's rule and groups by order and lifecycle event:

```sql
INSERT INTO completitud (orden, momento)
SELECT p.orden, p.momento
FROM operacion p JOIN jsonb_each(CAST(:#${variable.reglas} AS jsonb)) AS r(canal, regla) ON r.canal = p.canal
WHERE NOT EXISTS (SELECT 1 FROM completitud c WHERE c.orden = p.orden AND c.momento = p.momento)
GROUP BY p.orden, p.momento
HAVING bool_and(p.estado NOT IN ('pendiente', 'estacionada'))     -- ← esta condición
ON CONFLICT DO NOTHING
RETURNING orden, momento
```

It reads like this:

| Piece | What it does |
|---|---|
| `jsonb_each(…) AS r(canal, regla)` | Turns the rules into rows: one per channel, with its `llega_a` and its `plazo_s` |
| `JOIN … ON r.canal = p.canal` | Attaches to each operation the rule of its channel |
| `GROUP BY p.orden, p.momento` | Gathers the operations of each lifecycle event of each order |
| `HAVING bool_and(…)` | The lifecycle event passes only if the condition is true **for all** of its operations |
| `ON CONFLICT DO NOTHING` | A lifecycle event is completed only once |

Your job is the condition in the `HAVING`. Today it says "none is still pending or parked". It has to say **"each
one is in a state listed in its `llega_a`"**:

```sql
HAVING bool_and(jsonb_contains(r.regla -> 'llega_a', to_jsonb(p.estado)))
```

`r.regla -> 'llega_a'` is the rule's list, for example `["confirmada"]`; `to_jsonb(p.estado)` is the operation's
state as a JSON value; `jsonb_contains` asks whether the second is in the first.

Notice something that comes for free: **order 3001 has no `edi` operation**, because it comes from a company-owned
store. Since it has no row, it does not count: nobody waits for a 997 that does not apply to it.

### TODO 2: overdue alerts and does not close

The query in `vencidos` today **writes to `completitud`**: it closes the lifecycle event when the deadline passes. Change it so
that it writes an alert to `alerta`, once per channel:

| Change | What you write |
|---|---|
| Where to | `INSERT INTO alerta (orden, momento, canal, tipo, estado, espera)` |
| What | `SELECT p.orden, p.momento, p.canal, 'vencido', p.estado, r.regla ->> 'llega_a'` (without the `DISTINCT`) |
| Which ones | Add to the `WHERE`: `NOT jsonb_contains(r.regla -> 'llega_a', to_jsonb(p.estado))`, to alert only about the channels that have **not** arrived |
| Once | Leave the `ON CONFLICT DO NOTHING`: the `alerta` table has a `UNIQUE (orden, momento, canal, tipo)` |
| For the log | Add `RETURNING orden, momento, canal, estado, espera` and `outputType: SelectList` |

And after the query, a `split` over `${body}` with a `log` at `WARN` for each new alert, like the `split` in
`juntar`. The message starts with `VENCIDO orden ${body[orden]} momento ${body[momento]} canal ${body[canal]}`.

## 4. Break one on purpose

With everything finished, remove the condition `NOT jsonb_contains(…)` from the `WHERE` in `vencidos`. Run
`./verifica`.

First, predict: order 4002 waits for its 997 for more than 20 seconds. The API and the ERP accepted it within a few
seconds. How many alerts do you expect?

Three instead of one. Without that condition, the clock alerts about **every** channel that has gone past its
deadline, whether it has arrived or not: the API and the ERP, which already accepted, also come out as overdue. The
check for the alert on order 4002 comes out red. An alert that goes off for what is fine teaches people to ignore
alerts, and then the one that matters gets lost. Put the condition back.

## 5. Check

```bash
./verifica
```

`verifica` sends one order from a company-owned store and one from a franchise, and waits for the 997; then it shuts
down the partner's system and sends order 4002, waits for it to pass its deadline, **restarts the router** and turns
the partner back on. It is done when you see the seven green checks:

```
✅ Arrancaron el router (con su reloj que junta y vigila), los tres canales y el sondeo
✅ La 4001: API y ERP aceptada y el 850 depositado, pero sin 997 todavía no está COMPLETA
✅ La 3001 (tienda propia): COMPLETA con la API y el ERP; el socio no le toca y no se le espera
✅ La 4001: llegó el 997, el socio quedó confirmada y la orden COMPLETA
✅ La 4002 sin 997 pasó su plazo: un solo aviso (vencido, en «aceptada», se espera confirmada) y sigue abierta
✅ Log: el aviso del vencido está en el log del router
✅ Tras reiniciar el router, el 997 tardío confirmó al socio y la 4002 quedó COMPLETA, con su único aviso como historia
```

Read the last one slowly. The router restarted **in the middle of the wait**, and order 4002 was completed all the
same. You wrote nothing for that: what was being gathered never lived in the router's memory, it lived in the
database. When it came back, the clock asked again and found the same thing, plus the 997 that had just arrived.

Look at the two tables:

```bash
docker compose -f ../../infra/compose.yaml exec estado psql -U router -d estado \
  -c "SELECT * FROM completitud ORDER BY completa" -c "SELECT orden, canal, estado, espera, creada FROM alerta"
```

**Not working?** `./verifica solucion` tests ours, and
`diff mio/orden-router/juntar.camel.yaml solucion/orden-router/juntar.camel.yaml` tells you where they differ.

| What you see | What to do |
|---|---|
| `La 4001 con el socio en «aceptada» ya quedó COMPLETA` | The `HAVING` still has the condition from `inicio/`: change it to the `jsonb_contains` |
| `La 3001 (tienda propia) no quedó COMPLETA` | The `JOIN` with the rules is missing, or you changed `bool_and` to something else |
| `La 4002 sin 997: hay 0 avisos` and `completa=1` | `vencidos` is still writing to `completitud`: it goes in `alerta` |
| `La 4002 sin 997: hay 3 avisos` | The `NOT jsonb_contains(…)` is missing from the `WHERE` in `vencidos` |
| `no está el WARN del vencido de la 4002` | The `RETURNING` with `outputType: SelectList` is missing, or the `split` with its `log` at `WARN` |
| `No está arriba socio-bot` | Bring up the environment: `docker compose -f infra/compose.yaml up -d --wait` |
| `El puerto 8080 está ocupado` | The services from another run were left up: `DONDE=mio docker compose -f compose.yaml down` |

---

## What this teaches beyond the course

**Scattering is easy; gathering is where the work is.** Any multicast can send something to several destinations.
Knowing when all of them have finished, each with its own idea of "I am done", is what tells the business that the
order is now everywhere.

**What does not arrive gives no notice.** A system that only reacts to events never finds out about what is missing.
It takes something that watches the clock and asks.

**Alerting is not closing.** Closing by force when the deadline passes says "finished" about something that did not
finish, and if what was missing arrives late it no longer changes anything. Alerting lets a person decide, and lets
what is late still count.

**If state lives outside the process, a restart is not a problem.** We said it in lesson 15 as theory; today you saw
it: the router restarted in the middle of the wait and lost nothing, without one extra line of code.

## Exercise: the New Year's Eve dinner

1. Before running anything, write on paper your prediction for question 4 above: is what was gathered lost on
   restart? After running `./verifica`, compare it with what happened and write down where what was gathered lived.
2. In the rules, change the partner's `plazo_s` to `5` and run `./verifica`. Which checks change, and why? What would
   happen to the people who handle the alerts if the deadline were that short in real life? Put it back to `20`.
3. Imagine the router had three replicas, each with its own clock. Would an order be completed three times? Would an
   overdue channel be alerted three times? (Hint: look for the two `UNIQUE` constraints and the `ON CONFLICT`.)

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [Gathering is asking the database, and an overdue channel alerts without closing](../decisiones/29-juntar-es-preguntarle-a-la-base.md)

## Review

1. What is a Scatter-Gather? Which part of the course scatters and which part gathers?
2. What does each channel wait for before it counts as finished, and where is that written?
3. Why does gathering need a clock?
4. Why is order 3001 completed without a 997?
5. **The closing question:** when the partner's deadline passes, the router alerts and leaves the order open. Why
   would closing it by force be worse? Use the late 997 of order 4002 in your answer.

**Next:** in lesson 26 a customer says their order did not arrive. You have the database, the log and the
destinations. What do you query first, and how do you reconstruct an order's complete journey?
