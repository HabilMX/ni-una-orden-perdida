# Lesson 23 — Reprocessing what failed

> **What you will achieve:** a door in the router, `POST /rerun`, so that a person can resend **only** the step that
> got stuck, without duplicating anything in the destinations and leaving written who asked for it, why and with what
> version it went out.

**Before you start you need:** lessons 22 and 18 finished, and the environment from lesson 00 turned on.

## What is new today, in one sentence

In lesson 20 you left what could not be known in `conciliacion_pendiente`, and in lesson 22 what the circuit breaker
did not send in `pendiente`. Both are waiting for the same thing: for someone to fix the cause and send it again.
Until today, that someone had no way to do it. The **rerun** is that tool: one more attempt of the same operation,
requested by a person and recorded as such.

**The picture:** a carrier's claims counter. You bring your tracking number and say "this package got stuck in the
warehouse because of the storm". They do not resend everything you sent that day: they look for **that** package,
check that it really is stuck and dispatch it again. The system records that the counter resent it, at your request,
because of the storm. If you bring the tracking number of a package that was already delivered, they tell you no.

---

## 1. Set up your folder

```bash
cd lecciones/23-reprocesar-lo-que-fallo
cp -R inicio mio
```

The router has a new file, `orden-router/rerun.camel.yaml`, with a single route: `rerun`, which listens on
`POST /rerun`. It receives this:

```json
{"orden": "4001", "momento": "creada", "canal": "edi",
 "pedido_por": "ana.soporte", "motivo": "el SFTP del socio estuvo caído"}
```

Open it and read the comment at the top. The version in `inicio/` **works, but it is not enough**. It marks the
operation as `pendiente` and throws it back into the distribution. That has three problems:

| Problem | Why it matters |
|---|---|
| It goes out as an `auto` attempt, with no name and no reason | Tomorrow nobody knows that a person asked for it, or why |
| It reopens what had already gone well, and sends it again | A rerun of something accepted calls the destination again |
| It answers 202 `devuelta al reparto` (returned to the distribution) | Whoever asked for it does not know how it went |

And there is something you do **not** have to build: the `intento` table already has the columns `tipo`, `pedido_por`
and `motivo`, and the `INSERT` of the `intentar` route (in `reparto.camel.yaml`) already fills them with three
variables. If they do not exist, `tipo` stays as `auto`.

## 2. Predict

Answer on paper:

1. The partner's SFTP comes back. If you reprocess **the whole order** (all three channels) for order 4001, what
   happens to the API and the ERP, which already had it?
2. The API was slow and the channel cut off: order 4005 was left without knowing how it went. You do the rerun. Is another order created in the API?
   (Hint: lessons 02 and 18.)
3. Someone asks for a rerun of an operation that is already `aceptada`. What should the router answer?

Look at how it is today:

```bash
./verifica inicio
```

The reprocessing goes out, and that is why several checks pass. But it goes out as `auto` attempts, the rerun of what
was already accepted calls the destination again instead of refusing, and one without a reason is accepted anyway.

## 3. A manual attempt, with a name and a reason

Open `mio/orden-router/rerun.camel.yaml`. After the five variables taken from the body there are four TODOs, and
all four replace what is below: the `UPDATE` to `pendiente`, the send to `seda:repartir` and the 202 response.

### TODO 1: no name or no reason, no

First two headers, so the response goes out clean: `removeHeaders` with `pattern: "*"` and a `setHeader` of
`Content-Type` to `application/json`. Then a `filter` that lets through **only what is wrong**: if `orden`,
`momento`, `canal`, `pedido_por` or `motivo` is missing, it answers 400 and stops.

```yaml
- filter:
    expression:
      simple:
        expression: >-
          ${variable.orden} == null || ${variable.orden} == '' || …
          ${variable.pedido_por} == null || ${variable.pedido_por} == '' ||
          ${variable.motivo} == null || ${variable.motivo} == ''
    steps:
      - setHeader:
          name: CamelHttpResponseCode
          expression:
            constant:
              expression: "400"
              resultType: java.lang.Integer
      - setBody:
          expression:
            constant:
              expression: '{"resultado":"no reprocesado","motivo":"faltan datos: orden, momento, canal, pedido_por y motivo"}'
      - stop: {}
```

The `stop` ends the route there: what comes below does not run.

### TODO 2: only what failed

Read the operation **from the database**, not from the body. Copy the query from the `repartir` route in
`reparto.camel.yaml` and change two things: make it also filter by `p.canal = :#${variable.canal}` instead of by
`p.estado = 'pendiente'`, and make it also return `p.estado`. It goes with `outputType: SelectOne`, so the body ends
up as **one** row (or `null`).

Then, a `choice` with two branches that answer and stop, like the `filter` in TODO 1:

| If | It answers | Why |
|---|---|---|
| `${body} == null` | 404 | There is no such operation |
| `${body[estado]} in 'aceptada,confirmada,estacionada'` | 409 | What was accepted and what was confirmed went well; what is parked is released by the sweep (lesson 17) |

Everything else (`pendiente`, `no_se_sabe`, `conciliacion_pendiente`, `rechazada`) goes on to TODO 3. Before going on,
store the state in a variable `antes`: you are going to answer with it at the end.

### TODO 3: one attempt, right here

This is the difficulty today. The reprocessing is **not** thrown into the queue: one attempt is made at that moment,
with the same routes the distribution uses.

1. A `setVariable` `tipo` with `constant` `manual`. With that, the `INSERT` in `intentar` records who and why (the
   variables `pedido_por` and `motivo` already exist).
2. A `to` to `direct:preparar`, with the row in the body. It builds the envelope, the fingerprint and the control
   number the way the distribution does.
3. A `doTry` with a `to` to `direct:intentar`. In its `doCatch` for `java.io.IOException` (the "could not be known"
   from lesson 20), an `UPDATE operacion SET estado = 'conciliacion_pendiente' WHERE id = :#${variable.operacion}`: a
   manual attempt is not retried on its own, it goes back into view of whoever asked for it.
4. A `filter`: if `${variable.desenlace} == 'aceptada'`, a `to` to `direct:barrido`, as the distribution does.
   Something parked could have been waiting for this step (lesson 17).

### TODO 4: tell them how it went

Answer 200 with what was left in the variables:

```yaml
- setBody:
    expression:
      simple:
        expression: '{"resultado":"reprocesado","operacion":${variable.operacion},"intento":${variable.intento},"antes":"${variable.antes}","desenlace":"${variable.desenlace}","codigo":${variable.codigo},"version":"${variable.version}"}'
```

Notice the last field. The attempt goes out with **what is deployed today**: the current mapping and template, not
the ones from the day it failed. If between the outage and the rerun someone fixed the mapping, the rerun goes out
fixed, and the version it went out with is recorded in the attempt (`version_mapeo`) and in the response.

## 4. Break one on purpose

With everything finished, remove the second branch of the `choice` in TODO 2, the 409 one. Run `./verifica`.

First, predict: `verifica` asks for a rerun of order 4001, which is already `aceptada` at the partner. What happens
at the destination?

The router makes the manual attempt: it calls the partner's channel again, and the operation has one more attempt.
The check for the rerun of what was accepted comes out red. The audit trail now has a reprocessing that nobody needed, and
in a destination that did not recognize repeats there would be a duplicate. **The router does not ask the
destination whether it already has it: it asks its own database.** Put the branch back.

## 5. Check

```bash
./verifica
```

`verifica` turns off the SFTP and sends four orders; it puts the API in slow mode and sends order 4005; then it puts
everything back and does five reruns, one for each stuck step. At the end it asks for one of something accepted and
one without a reason. It is done when you see the eleven green checks:

```
✅ Arrancaron el router (con su POST /rerun), los tres canales y el sondeo
✅ Con el SFTP caído, el canal del socio de la 4001 a la 4004 quedó pendiente (lo que se reprocesa)
✅ Con la API lenta, el canal rest de la 4005 no supo cómo le fue y quedó (…)
✅ Rerun: el canal del socio de la 4001 a la 4004 y el rest de la 4005 quedaron aceptados
✅ Cada rerun quedó como intento «manual», con quién lo pidió, por qué y la versión del mapeo con que salió
✅ Sin duplicados: un 850 por orden en el socio, y la API creó la 4005 una vez y al rerun contestó 200 «reenvío»
✅ Solo el paso que falló: la API y el ERP de la 4001 a la 4004 no recibieron intentos nuevos
✅ Rerun de lo que ya está aceptado: 409, sin intento nuevo y la operación sigue (…)
✅ Rerun sin motivo: 400 (sin quién y por qué, el rastro quedaría mudo)
✅ El ERP reconoce un pedido que ya tiene: 200 «ya registrado», sin renglones nuevos
✅ El 997 del socio confirmó los cuatro 850 reprocesados
```

Look at the sixth one. The API **already had** order 4005: the slow call registered it after the channel cut off, as
in lesson 20. The rerun sent it again with the same key, and the API answered 200 `reenvío` (resend), not 201. That
is what makes the rerun of an "unknown" safe.

Do a rerun by hand yourself and look at the response:

```bash
curl -s -XPOST localhost:8080/rerun -H 'Content-Type: application/json' \
  -d '{"orden":"4001","momento":"creada","canal":"edi","pedido_por":"yo","motivo":"probar"}'
```

**Not working?** `./verifica solucion` tests ours, and
`diff mio/orden-router/rerun.camel.yaml solucion/orden-router/rerun.camel.yaml` tells you where they differ.

| What you see | What to do |
|---|---|
| `Esperaba 5 intentos manuales` and there are 0 | The `setVariable` `tipo` to `manual` is missing, or you put the attempt in the queue instead of calling `direct:intentar` |
| `Se reprocesó de más: la API y el ERP … pasaron de … a … intentos` | Your query does not filter by channel: it is taking the three operations of the lifecycle event |
| `Rerun de la 4001 ya aceptada: contestó …` | The 409 branch is missing, or it has no `stop` |
| `Rerun sin motivo: esperaba 400` | The `filter` in TODO 1 is missing `motivo`, or it is missing the `stop` |
| `de las 5 operaciones reprocesadas, … quedaron aceptadas` | `direct:preparar` is missing before `direct:intentar`: without it there is no envelope and no control number |
| `No pude poner la API en modo lento` | Your API is from before: recreate it as in lesson 20 |
| `El puerto 8080 está ocupado` | The services from another run were left up: `DONDE=mio docker compose -f compose.yaml down` |

---

## What this teaches beyond the course

**A reprocessing is one more attempt, not a new operation.** If it were new, each destination would see it as a
different order. Since it is the same operation, it goes out with the same key and the same control number, and each
destination can recognize it.

**You reprocess the step, not the order.** Resending everything "just in case" fills the audit trail with noise and bets
that every destination can take a repeat. You send what failed and you refuse what went well.

**Who and why are not bureaucracy.** A month from now someone is going to ask why order 4005 has two attempts.
Without `pedido_por` or `motivo`, the answer is "I do not know"; with them, "support asked for it because the API was
slow".

**It goes out with today's setup, and that is recorded.** A rerun does not revive the past: it uses the current
mapping. That is why every attempt stores its version, and that is why you can know what the destination received
each time.

## Exercise: the claims counter

With your finished version, run `./verifica` and then:

1. Query the attempts of the partner's channel for order 4001:
   `SELECT n, tipo, pedido_por, motivo, desenlace, version_mapeo FROM intento i JOIN operacion p ON p.id = i.operacion
   WHERE p.orden = '4001' AND p.canal = 'edi' ORDER BY n;`
   Tell the story of that operation in three sentences, with what each row says.
2. Ask for three reruns by hand: one for an order that does not exist (`9999`), one for order 4001 on the `rest`
   channel and one without `pedido_por`. Write down what each one answers and why.
3. Imagine that tomorrow you fix the partner's mapping to version 2 and do a rerun of an 850 that had failed
   yesterday. What version does it go out with? Where can you check it? Why would it not be better for it to go out
   with yesterday's version?

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [Reprocessing by hand is one more attempt, with a name and a reason](../decisiones/27-el-rerun-es-un-intento-manual.md)

## Review

1. Which states of an operation can be reprocessed, and which cannot? Why not what is parked either?
2. Why does the rerun read the operation from the database and not trust what the body says?
3. What keeps a rerun of order 4005 from creating another order in the API?
4. Why is 400 the answer when `pedido_por` or `motivo` is missing?
5. **The closing question:** the rerun goes out with the current configuration and leaves its version recorded. Why
   with today's, and what is recording it for?

**Next:** in lesson 24 the problem is not that the destination is down, but that you knock it down yourself. With
three replicas of int-bd, how many calls at the same time does the ERP receive?
