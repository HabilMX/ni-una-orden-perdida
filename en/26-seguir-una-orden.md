# Lesson 26 — Following an order

> **What you will achieve:** answering "what happened to order 4006?" with a single query: each channel, each
> attempt, what went out in each one in full, what the partner answered and whether the order ended up complete. And
> serving that answer over HTTP, looking it up by the order number.

**Before you start you need:** lessons 15 and 25 finished and the environment from lesson 00 turned on.

## What is new today, in one sentence

You have spent eleven lessons storing things in the state database: the order (15), each operation and each attempt
(15), the conflicts (18), the partner's acknowledgments (21), the alerts and what is complete (25). Each piece answers
a small question. Today you gather them to answer the big one, the one a customer or someone in support asks: **"my
order did not arrive, what happened to it?"**. And you answer it by the **business key**, the order number, which is
the only thing the person asking has.

**The picture:** tracking a package on a carrier's website. You type the tracking number and you see the whole
history: received at the branch, in transit, delivery attempt failed because nobody was home, delivered. You do not
see only today's status: you see **each step**, with its time. If it only said "delivered", you would not know that
there was a failed attempt, or why it took two days.

---

## 1. Set up your folder

```bash
cd lecciones/26-seguir-una-orden
cp -R inicio mio
```

The router has two new files:

| File | What it is | Do you write it? |
|---|---|---|
| `orden-router/recorrido.camel.yaml` | On startup, it declares the view with whatever `recorrido.sql` says. And it serves `GET /ordenes/{orden}/recorrido` | No, read it |
| `orden-router/recorrido.sql` | The `recorrido` view of the state database | **Yes** |

A **view** is a stored query with a name. You read it as if it were a table
(`SELECT * FROM recorrido WHERE orden = '4006'`), but it stores nothing of its own: every time you read it, it gathers
the real tables again. That is why it does not go stale.

And there is a new table starting today, `payload`. Each attempt stores in `intento` its **fingerprint** (`sha256`)
and its **size**, and the full body of what went out lives in `payload`, once per fingerprint. If two attempts sent
the same thing, they share the row.

Open `infra/postgres/01-estado.sql` and locate the tables you are going to join: `operacion`, `intento`, `payload`,
`acuse`, `alerta` and `completitud`.

## 2. Predict

Answer on paper:

1. A customer says order 4006 did not arrive. What do you query first: the router's log, the API, the ERP or the
   state database? Why?
2. The API was down when order 4006 arrived; later someone did a rerun. If the view shows only the last attempt of
   each channel, what part of the history is lost?
3. Order 4007 has 150 order lines. Does the router's log show it in full?

Look at how it is today:

```bash
./verifica inicio
```

The view exists and the `GET` answers, but it is a **dashboard of today**: one row per operation, with its last
attempt. The history of the rest channel for order 4006 looks like a single `aceptada`, without the first attempt
that did not know, and without who did the rerun. And the `cuerpo` column comes out empty: it does not say what went
out.

## 3. Build the journey

Open `mio/orden-router/recorrido.sql`. It is a `CREATE VIEW` with a `SELECT` that already has the columns with their
final names; some come out as `NULL` for now. It has three TODOs:

| TODO | What you change | What for |
|---|---|---|
| 1 | The `LEFT JOIN LATERAL (… LIMIT 1) u` for a `LEFT JOIN intento i ON i.operacion = p.id`, and the `u.` columns for `i.` | One row for **each** attempt, not only the last |
| 2 | A `LEFT JOIN payload d ON d.sha256 = i.sha256`, and `cuerpo` comes from `d.cuerpo` | What went out in each attempt, in full |
| 3 | Three columns that today are `NULL`: `acuses`, `alertas` and `completa` | What came after the send |

For TODO 3, two of the columns are a **subquery** that gathers several rows into one text with `string_agg`:

```sql
(SELECT string_agg(a.codigo, ',' ORDER BY a.id) FROM acuse a WHERE a.operacion = p.id) AS acuses,
```

That one gives, for example, `A` or `R`: what the partner said about that operation. The one for `alertas` is the
same, over the `alerta` table, for the same order, the same moment and the same channel, with
`l.tipo || ' en ' || l.estado`. And `completa` comes from a
`LEFT JOIN completitud c ON c.orden = p.orden AND c.momento = p.momento`, with `c.completa`.

Why `LEFT JOIN` and not `JOIN`? Because an operation that has no attempts yet, or an attempt without an
acknowledgment, or a lifecycle event that has not been completed, **also has to come out**. With `JOIN` they would disappear,
and what is missing is exactly what matters most when following an order.

And why subqueries for acknowledgments and alerts, and not more `JOIN`s? Because a `JOIN` against a table with
several rows per operation **multiplies** the rows: each attempt would come out once for each acknowledgment. The
subquery gathers them into a single cell.

Leave the column names as they are: the `GET` uses them.

## 4. Break one on purpose

With your view finished, remove the `LEFT JOIN completitud` and leave `completa` as `CAST(NULL AS timestamptz)`. Run
`./verifica`.

First, predict: order 4007 was completed. What does the journey say?

That it was not. The last check comes out red: the view says that **neither** order 4007 **nor** order 4006 ended up
complete. Nothing failed: the view simply was not looking at that table, and an empty cell reads as "it did not
happen". **A journey that does not show something does not prove that it did not happen.** That is why it is built
with `LEFT JOIN` against everything the database knows. Put the line back.

## 5. Check

```bash
./verifica
```

`verifica` shuts down the API, sends order 4006 (which asks for a product the partner does not know) and order 4007
(large, with 150 order lines); it turns the API back on, does a rerun of the rest channel for both and asks for the
journey. It is done when you see the eight green checks:

```
✅ Arrancaron el router (con su GET /ordenes/{orden}/recorrido), los tres canales y el sondeo
✅ El router declaró la vista recorrido al arrancar
✅ GET /ordenes/4006/recorrido: (…) filas, una por cada intento de la 4006
✅ El canal rest de la 4006 cuenta su historia: (…)
✅ El canal del socio de la 4006: rechazada, con su 997 de rechazo (R) en el recorrido
✅ Cada intento del recorrido trae lo que salió en él (cuerpo)
✅ La 4007 (grande): su cuerpo está entero en el recorrido, (…) bytes con su huella y sus 150 renglones
✅ El recorrido dice que la 4007 quedó COMPLETA y la 4006 no
```

Ask for the journey yourself:

```bash
curl -s localhost:8080/ordenes/4006/recorrido | python3 -m json.tool | less
```

**Not working?** `./verifica solucion` tests ours, and
`diff mio/orden-router/recorrido.sql solucion/orden-router/recorrido.sql` tells you where they differ.

| What you see | What to do |
|---|---|
| `No existe la vista recorrido` | Your `recorrido.sql` has a SQL error: the router says so in the log, below that check |
| `la vista tiene … y la 4006 tiene … intentos` | The `LATERAL … LIMIT 1` is still there, or the `JOIN` of an acknowledgment multiplied the rows |
| `El canal rest de la 4006 en el recorrido: «aceptada/manual»` without what came before | You only see the last attempt: TODO 1 is missing |
| `intentos del recorrido sin su cuerpo` | The `LEFT JOIN payload` is missing, or it joins on another column: it is `sha256` |
| `El canal del socio de la 4006 … (esperaba «rechazada R»)` | The `acuses` column is still `NULL` |
| `Completa en el recorrido` | The `LEFT JOIN completitud` is missing |
| `El puerto 8080 está ocupado` | The services from another run were left up: `DONDE=mio docker compose -f compose.yaml down` |

If you change `recorrido.sql` with the services up, restart the router: the view is declared on startup.

---

## What this teaches beyond the course

**You search by what the person asking has.** Nobody in support knows the `id` of an operation or the control number
of an X12. They know the order number. The journey is built from there.

**The log is not the record.** The log truncates large bodies, gets mixed with the log of other orders and goes away
with the container. The database keeps each attempt with what went out, in full, as long as nobody deletes it on
purpose.

**The history is worth more than the state.** "Accepted" says how it ended; the history says that at first the
outcome was unknown, that the circuit breaker did not send it (lesson 22), that someone at the control desk did a
rerun and that the API answered 201. In `verifica` it looks like this:
`no_se_sabe/auto no_enviado/auto aceptada/manual`. With that you can explain why it took so long.

**A view does not go stale.** It does not copy the data: it gathers it again every time. It is the cheapest way to
have a fresh answer without a second source of truth.

## Exercise: the problem order

With your version finished, run `./verifica` and then:

1. Ask for the journey of order 4006 and, **with it alone**, write a paragraph for the customer who says their order
   "did not arrive". Did it reach the API? The ERP? The partner? What happened with the partner, on which order line and
   with which product? (Hint: the body of the partner's attempt and the 997 from lesson 21.)
2. Look for order 4007 in the router's log: `DONDE=mio docker compose -f compose.yaml logs orden-router | grep 4007`.
   Do you see its 150 order lines? Compare with the `tamano` column of the journey.
3. Write the query you would give someone in support to see **only the channels that did not end well** for an
   order, with their last outcome. Start with `SELECT canal, estado_canal, …  FROM recorrido WHERE orden = '4006'`.

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [The journey is a view](../decisiones/30-el-recorrido-es-una-vista.md)

## Review

1. What is a view, and why does it not go stale?
2. Why is the journey looked up by the order number?
3. What does the `payload` table store, and how is it joined to each attempt?
4. Why `LEFT JOIN` and not `JOIN`? Why subqueries for the acknowledgments?
5. **The closing question:** a customer says their order did not arrive. What do you query first, and why the
   journey and not the log?

**Next:** in lesson 27 you look at the journey of order 4008 again. It carries a person's name, address, phone number
and email. Who should be able to see them?
