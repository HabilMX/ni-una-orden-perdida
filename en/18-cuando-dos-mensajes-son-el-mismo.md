# Lesson 18 — When are two messages the same one?

> **What you will achieve:** the router recognizes a notice it already received, even if it arrives written
> differently, and does not distribute it again. And it tells that repeat apart from a notice that carries the same key
> with different content: that is not a repeat, it is a conflict, and it gets flagged.

**Before you start you need:** lessons 15 and 16 finished and the environment from lesson 00 turned on. This lesson
uses a new table in the state database, `conflicto`. If your database is from before, recreate it once:

```bash
docker compose -f infra/compose.yaml up -d --wait --force-recreate -V estado
```

## What is new today, in one sentence

In lesson 16 you learned that whoever sends a webhook **retries** if it does not get its 2xx in time. That has a
consequence: sooner or later the same notice arrives twice. Today the router learns to recognize it. This is
called an *Idempotent Consumer*: receiving the same thing twice leaves the same result as receiving it once.

**The picture:** the guard booth at the entrance of a building, with its visitor notebook. The lady from 302 arrives
at 10:00 and they write her down. At 10:05 she comes back because she forgot her badge: they do not write her down
again, she is already in the notebook. But if someone arrives saying she is the lady from 302 and does not look like
the one in the photo, the guard does not let her in or write her over the existing entry: he calls whoever lives
there. The first case is a **repeat**; the second, a **conflict**.

---

## 1. Set up your folder

```bash
cd lecciones/18-cuando-dos-mensajes-son-el-mismo
cp -R inicio mio
```

The router has a new file, `orden-router/reconocer.camel.yaml`. Open it and read the comment at the top. It says
what a message is recognized by:

| Concept | What it is | In the course |
|---|---|---|
| **Key** | What says **what** the message **is about** | `orden-<orden>-<momento>`, for example `orden-4001-entregada`. Each channel carries it once: it is the `UNIQUE (orden, momento, canal)` of `operacion` |
| **Fingerprint** | What says **what** the message **carries** | The sha256 of the body, already normalized. Two bodies with the same content have the same fingerprint |

Those two pieces give three cases:

| Case | Key | Fingerprint | What the router answers | What it does |
|---|---|---|---|---|
| **new** | There are no operations with that key | — | 202 `recibida` (received) | Saves and distributes, as it has up to lesson 17 |
| **repeat** | Already exists | The same | 202 `repetido` (repeat) | Nothing: it already had it |
| **conflict** | Already exists | A different one | 409 `conflicto` (conflict) | Writes it in the `conflicto` table, warns in the log and does not overwrite what is saved |

At the bottom of the file is the `recibir-momento` route, which already uses that decision: it reads
`${variable.visto}` and answers or saves depending on the case. Your job is the route that fills that variable,
`reconocer`.

## 2. Predict

Answer on paper:

1. The carrier reports the delivery of order 4001 twice, with the same content. How many 214s do you expect in the
   partner's SFTP? How many events in the API?
2. The second notice carries the same thing, but on a single line and with the fields in a different sequence. Is it
   the same message?
3. A third notice for order 4001 arrives with the same key and **a different tracking number**. What should happen to
   the tracking number the API and the ERP already have?

Look at how it is today:

```bash
./verifica inicio
```

The first notice goes through. The second one **answers with an error**: the route in `inicio/` sees everything as
new, tries to save the operations again and the `UNIQUE` of `operacion` stops it. Nothing was duplicated, but the
carrier gets an error for something we did have, and as you learned in lesson 16, a sender that gets an error
**sends again**. And the notice with a different tracking number is not told apart either: it is one more error.

## 3. Recognize what arrives

Open `mio/orden-router/reconocer.camel.yaml` and find the `reconocer` route. Today it only sets `visto` to `nuevo`.
It has four TODOs:

| TODO | What you write | What for |
|---|---|---|
| 1 | A `to` with `sql:` that returns two columns: `nueva`, the fingerprint of what arrived, and `guardada`, the fingerprint of the first operation with that order and that moment | To compare what arrived with what you already have, in a single query |
| 2 | A `setVariable` `huella` with the new fingerprint | `guardar-momento` writes it in each operation, to compare next time |
| 3 | A `choice` with three branches that sets `visto` to `nuevo`, `repetido` or `conflicto` | The decision that `recibir-momento` reads |
| 4 | In the conflict branch: an `INSERT INTO conflicto` and a `log` at `WARN` that starts with `CONFLICTO orden-<orden>-<momento>` | So the conflict is written down and someone sees it |

Four hints:

- The query for TODO 1 is almost complete in the comment. It takes `dataSource: "#estado"`, `outputType: SelectOne`
  and `variableReceive: huellas`. With that, `${variable.huellas[nueva]}` and `${variable.huellas[guardada]}` are the
  two fingerprints.
- For `guardada`, a subquery: `(SELECT huella FROM operacion WHERE orden = … AND momento = … ORDER BY id
  LIMIT 1)`. If there are no operations, it returns `NULL`.
- The `choice` compares like this: `${variable.huellas[guardada]} == null` is new; `${variable.huellas[guardada]} ==
  ${variable.huella}` is a repeat; everything else (`otherwise`) is a conflict.
- The `conflicto` table is in `infra/postgres/01-estado.sql`. Its columns are `llave`, `orden`, `momento`,
  `huella_guardada`, `huella_nueva`, `cuerpo` (as `jsonb`, the same as in lesson 15) and `correlacion`.

Notice the fingerprint line:

```sql
encode(sha256(convert_to(CAST(CAST(:#${body} AS jsonb) AS text), 'UTF8')), 'hex')
```

It reads from the inside out: the body is converted to `jsonb`, the `jsonb` becomes text, the text becomes
bytes, the sha256 is taken from the bytes and the result is written in hexadecimal. The step that matters is the
first one. **PostgreSQL stores `jsonb` without the extra spaces and with the keys in a sequence of its own**, so two
bodies with the same content, written differently, come out the same when they become text. Without that `CAST`, the
fingerprint would be that of the text exactly as it arrived.

## 4. Break one on purpose

With your `reconocer` finished, remove the inner `CAST(… AS jsonb)`: leave `convert_to(:#${body}, 'UTF8')`. Run
`./verifica`.

First, predict: the second notice from `verifica` carries the same content on a single line and with the fields
reversed. How are you going to recognize it?

The check for the two notices comes out red: the router answered 409 to the second one. The fingerprint was taken from the text, and the text changed even though the
content did not. **You took a resend for a change**, and someone is going to have to review a conflict that does not
exist. Put the `CAST` back.

## 5. Check

```bash
./verifica
```

`verifica` creates and ships order 4001, plays the carrier with its delivery notice, sends it again written
differently and finally sends one with a different tracking number. It is done when you see the nine green checks:

```
✅ Arrancaron el router (con su reconocedor de repetidos), los tres canales y el sondeo
✅ Antes de los avisos: la 4001 está EMBARCADO
✅ Los dos avisos contestaron 202, y el segundo se reconoció como repetido aunque viniera escrito distinto
✅ Estado: la entrega tiene 3 operaciones con su huella sha256 y 3 intentos, uno por canal
✅ Destinos: un solo 201 de la entrega en la API, un solo 214 en el socio y el ERP en ENTREGADO
✅ Un aviso con la misma llave y otra guía contestó 409 «conflicto»
✅ Estado: el conflicto quedó en su tabla, con la llave, lo que llegó y las dos huellas distintas
✅ Log: el router avisó el conflicto con un WARN
✅ Nada se pisó: la orden guarda la guía original, sigue habiendo 3 intentos y la API tiene la guía con la que salió
```

Look at the conflict table:

```bash
docker compose -f ../../infra/compose.yaml exec estado psql -U router -d estado \
  -c "SELECT llave, cuerpo->>'guia' AS guia, huella_guardada <> huella_nueva AS distintas FROM conflicto"
```

**Not working?** `./verifica solucion` tests ours, and
`diff mio/orden-router/reconocer.camel.yaml solucion/orden-router/reconocer.camel.yaml` tells you where they
differ.

| What you see | What to do |
|---|---|
| `Esperaba 202 y 202 «repetido»` and the second one answered 500 | Your `choice` never got to set `repetido`: check that the query has `variableReceive: huellas` and that `guardada` searches by order **and** lifecycle event |
| `Esperaba 202 y 202 «repetido»` and the second one answered 409 | The fingerprint is being taken from the text: the inner `CAST(:#${body} AS jsonb)` is missing |
| `esperaba 3 operaciones con huella` and there are 0 fingerprints | TODO 2 is missing: the `huella` variable does not exist when `guardar-momento` writes it |
| `Esperaba 409 para el aviso con otra guía` | The conflict branch does not set `visto` to `conflicto`, or the `choice` takes it as new |
| `esperaba una fila en conflicto` | The `INSERT INTO conflicto` is missing, or it does not have `dataSource: "#estado"` |
| `no hay un WARN «CONFLICTO orden-4001-entregada»` | The `log` does not have `loggingLevel: WARN`, or the message does not start with `CONFLICTO orden-${variable.orden}-${variable.momento}` |
| `El puerto 8080 está ocupado` | The services from another run were left up: `DONDE=mio docker compose -f compose.yaml down` |

---

## What this teaches beyond the course

**Design as if a message could arrive more than once.** In many flows with retries, like this one, what is
guaranteed is that it arrives "at least once", not "exactly once": retries protect against loss, and in exchange they
bring repeats. Whoever receives is in charge of recognizing them. That is why almost every serious payments or
shipping API asks for an idempotency key.

**The key comes from the business, not from chance.** A new random number on every send would make every resend look
like a different message. `orden-4001-entregada` is the same every time the carrier resends, and that is why it works.

**Key and fingerprint answer different questions.** The key says whether you have already seen something **about
this**; the fingerprint, whether it is **the same thing**. With only the key, a change passes for a repeat and is
lost. With only the fingerprint, the same notice with a different send date would pass for new.

**A conflict is not resolved by the program.** Keeping the first one or the last one in silence would be deciding for
someone. It is written down, it is flagged and a person resolves it.

## Exercise: the guard booth notebook

With your finished version:

1. Write on paper the key for these three messages: the shipment of order 4002, the delivery of order 4002 and the
   delivery of order 4003. Why does a random number that the router generates on receipt not work as a key?
2. Compute by hand, with `psql`, the fingerprint of `{"orden":"4001","guia":"X"}` and of `{ "guia": "X", "orden": "4001" }`, with and
   without the `CAST` to `jsonb`. Use `SELECT encode(sha256(convert_to(CAST('…' AS jsonb)::text, 'UTF8')), 'hex')`. How many
   different fingerprints do you get in each case?
3. In the carrier's notice, which field could change between one resend and another without changing what happened?
   (Hint: think about when the notice was **sent**, not when the delivery happened.) What would happen to the
   fingerprint? Write down your answer: this lesson does not solve it.

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [A message is recognized by its key, and its content by its fingerprint](../decisiones/22-la-llave-y-la-huella.md)

## Review

1. What is an Idempotent Consumer? Say it with the guard booth example.
2. What is the key of a lifecycle event in this course, and where does it come from?
3. What is the fingerprint, and why is it taken after converting the body to `jsonb`?
4. Why is the repeat answered with 202 and not an error? (Go back to lesson 16.)
5. **The closing question:** a repeat and a conflict have the same key. What tells them apart, what does the
   router answer to each one and why does the conflict not overwrite what is saved?

**Next:** in lesson 19 the problem is the opposite. It does not arrive twice: it does not arrive. The API does not
answer, and the router has to try again without scrambling the order's lifecycle events.
