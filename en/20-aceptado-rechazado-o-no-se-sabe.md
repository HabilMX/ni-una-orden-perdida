# Lesson 20 — Accepted, rejected or unknown

> **What you will achieve:** the router classifies each response from the destination by what it really said: "yes",
> "no" or nothing clear. A "no" is not repeated, and whatever was left without a response, once the retries run out,
> is marked for a person to review, never as a failure.

**Before you start you need:** lesson 19 finished and the environment from lesson 00 turned on. This lesson uses API
1.5.0, which has three test modes: `rechaza`, `lento` and `satura`. If your API is from before, recreate it once:

```bash
docker compose -f infra/compose.yaml up -d --wait --build --force-recreate api-pedidos
```

## What is new today, in one sentence

In lesson 19 the router learned to retry when the destination does not answer. But "it did not answer" is only one of
the possible responses. There are destinations that answer "no" with total clarity, and there are calls that get cut
off halfway without our knowing whether the destination managed to write. Today the router learns to tell them apart
**by the evidence**, not by what it expected.

**The picture:** you sent a certified letter. If the signed acknowledgment comes back to you, it arrived. If the
letter is returned with a stamp saying "address does not exist", it did not arrive and there is no point in sending it
again to the same address. But if three weeks go by and nothing comes back, you do not know: it may have been lost, or
it may have arrived and the acknowledgment got lost on the way back. The right thing is not to say "it was lost"; it
is to say "I do not know" and ask at the post office.

---

## 1. Set up your folder

```bash
cd lecciones/20-aceptado-rechazado-o-no-se-sabe
cp -R inicio mio
```

Today you work in two router files you already know:

| File | Route | What you change |
|---|---|---|
| `reparto.camel.yaml` | `intentar`, the one that calls the channel and records how it went | The `choice` that decides the outcome (TODO 1 and 2) |
| `reintentos.camel.yaml` | The `onException` from lesson 19 | What happens when the retries run out (TODO 3) |

And one piece that is already done: **the int-rest channel puts a 3-second timeout on the API**. If the API does not
answer within that time, int-rest does not make up a "yes" or a "no": it answers **504** to the router, which in HTTP
means "whoever was behind me did not answer in time".

And another one, also done: **if the destination says how long to wait, that is how long you wait.** A 429 usually
carries the header `Retry-After: 3`, "come back in 3 seconds". The router passes it along as the pause for its next
retry, with a cap of 10 seconds so that a destination cannot hold it up for an hour. And Camel's HTTP client, which on
its own would repeat a 429 without leaving an audit trail, is turned off (`automaticRetriesDisabled`): the only one that
retries is the router, and every attempt stays in the database.

## 2. Predict

Answer on paper:

1. The API took 10 seconds and the channel cut off at 3. Did it write the order or not? How would you know?
2. The API answered 422 "rejected for business reasons". If the router retries it five times, what will the fifth one
   answer? And what if it answered 429 "you sent me too many, come back in 3 seconds"?
3. The five retries ran out for a call that never answered. Did the order "fail"? What should someone do with it?

Look at how it is today:

```bash
./verifica inicio
```

The first test passes: with the API in `rechaza` mode, order 4001 ends up `rechazada` with a single attempt. But the
second one, with the API in `lento` mode, goes wrong: order 4002 ends up **`rechazada`**, and the last check shows that
the API **does have it**, in CREADO. The router wrote down "the destination said no" when the destination never said
anything, and it also wrote. If someone trusts that row and sends it again by hand, or gives it up for lost, they are
wrong in both cases.

The third one goes wrong too: with the API in `satura` mode, order 4003 gets a 429 and ends up `rechazada` on the first
attempt. The API does not have it, but it would have accepted it three seconds later. That order is never sent.

## 3. Decide by the evidence

Open `mio/orden-router/reparto.camel.yaml` and find the `intentar` route. Near the end, after calling the channel,
there is a `choice` that today says: 2xx is `aceptada`; everything else, `rechazada`. Above it are TODO 1 and 2.

This is the table it has to satisfy:

| What was seen | `${variable.codigo}` | Outcome | Is it retried? |
|---|---|---|---|
| The destination said yes | 200 to 299 | `aceptada` | No need |
| Not now, try later | 408, 425, 429 | `no_se_sabe` | Yes; if it carries `Retry-After`, that wait (cap 10 s) |
| The destination said no | The rest of 400 to 499 | `rechazada` | No: it will say the same |
| Nothing clear: a 5xx, a 504 or no response | 500 to 599, or `0` | `no_se_sabe` | Yes, with the policy from lesson 19 |

| TODO | What you write | What for |
|---|---|---|
| 1 | Two more `when`, in this sequence: one with `${variable.codigo} in '408,425,429'` that sets `desenlace` to `no_se_sabe`, and then one with `${variable.codigo} range '400..499'` that sets it to `rechazada` | So a "not now" is retried and a "no" is recorded as a "no" |
| 2 | Make the `otherwise` set `no_se_sabe` instead of `rechazada` | So what is neither a yes nor a no is recorded as "unknown" |

Why in that sequence? The `choice` keeps the **first** `when` that matches, and a 429 is also between 400 and
499. If the `range` came first, the 429 would never reach its `when`.

Notice how it fits with lesson 19. At the end of `intentar` there is a `filter`: if the outcome ended up `no_se_sabe`,
it throws an `IOException`, and that exception is the one your `onException` catches in order to retry. **The outcome
you write today is what decides whether there is a retry.** A `rechazada` throws nothing, so it is not repeated.

Now open `mio/orden-router/reintentos.camel.yaml`. Inside the `onException`, before the `log`, is TODO 3:

| TODO | What you write | What for |
|---|---|---|
| 3 | A `to` with `sql:UPDATE operacion SET estado = 'conciliacion_pendiente' WHERE id = :#${variable.operacion}`, with `dataSource: "#estado"` | So what ran out is marked for someone to review |

The last attempt stays as `no_se_sabe`, because that is what happened in that attempt. What changes is the
**operation**: it is no longer waiting for a retry, it is waiting for a person.

## 4. Break one on purpose

With everything finished, change your TODO 1 so that a 4xx is also `no_se_sabe`, as if everything that is not 2xx were
worth repeating. Run `./verifica`.

First, predict: the API is in `rechaza` mode and answers 422 every time. How many attempts is the router going to
make?

Six: the first one and five retries, and all six answer 422. The business rejection check comes out red. Repeating
a "no" does not turn it into a "yes": it only hits the API six times, holds up the queue for about thirty seconds
(lesson 19) and in the end leaves the operation in `conciliacion_pendiente`, sending a person to review something the
destination had already answered clearly. Put `rechazada` back.

## 5. Check

```bash
./verifica
```

`verifica` puts the API in `rechaza` mode and sends order 4001; then it puts it in `lento` mode (it answers after 10
seconds) and sends order 4002; finally, in `satura` mode (two 429s with `Retry-After: 3` and then it serves the
request), it sends order 4003. On exit, it puts it back in normal mode. It is done when you see the nine green checks:

```
✅ Arrancaron el router, los tres canales y el sondeo
✅ Rechazo de negocio: la 4001 quedó rechazada con un solo intento (422): un «no» no se repite
✅ Mientras la llamada va en camino, su intento ya está en la base (con huella y sin desenlace): se escribe antes de enviar
✅ No se sabe: la 4002 se intentó 6 veces, las 6 sin respuesta a tiempo (504, no_se_sabe), y quedó en conciliacion_pendiente
✅ Y la API sí tiene la 4002 (CREADO): por eso no era un fallo ni un rechazo, era «no se sabe»
✅ Ahora no: la 4003 recibió dos 429 (no_se_sabe), se reintentó y quedó aceptada al tercer intento (201): un 429 no es un «no»
✅ Y respetó el Retry-After: entre una llamada y la siguiente pasaron 3.05 3.04 s, ninguna antes de los 3 s que pidió la API
✅ La API tiene la 4003 (CREADO): los 429 no la registraron, el reintento sí
✅ Los otros canales siguieron: ERP y socio aceptaron las dos órdenes
```

Look at the third one. `verifica` peeks into the database **while the call is still on its way** and finds the attempt
already written, with its fingerprint and without an outcome. You did not write that check today: it has existed since
lesson 15. But today you can see what it is for: if the router went down at that moment, the database would say "this
was attempted and I do not know how it went". If the attempt were written **after** the call, nothing would be left.

And look at the one about the waits: the seconds come from the API's log, the time at which each call reached it.
Yours will be slightly different; what is asked is that none of them goes below 3.

**Not working?** `./verifica solucion` tests ours, and
`diff -r mio/orden-router solucion/orden-router` tells you where they differ.

| What you see | What to do |
|---|---|
| `esperaba rechazada, 1 intento y 422` and there are 6 attempts | A 4xx is being taken as `no_se_sabe`: check the `range '400..499'` |
| `esperaba conciliacion_pendiente con 6 intentos` and it says `rechazada` | The `otherwise` is still setting `rechazada`: it is `no_se_sabe` (TODO 2) |
| `Ahora no: esperaba aceptada con 3 intentos…` and there is `rechazada` with one attempt | The 429 was taken as a "no": the `when` for `408,425,429` goes **before** the `range '400..499'` (TODO 1) |
| `Retry-After: esperaba dos esperas de al menos 3 s…` | Something is retrying without waiting: check that you did not touch the `CamelRedeliveryDelay` of `intentar` or the `onException` |
| `esperaba conciliacion_pendiente` and it says `no_se_sabe` | The `UPDATE` from TODO 3 is missing, or it does not have `dataSource: "#estado"` |
| `No pude poner la API en modo rechaza` or `en modo satura` | Your API is from before 1.5.0: recreate it as "Before you start" says |
| `El puerto 8080 está ocupado` | The services from another run were left up: `DONDE=mio docker compose -f compose.yaml down` |

---

## What this teaches beyond the course

**"Unknown" is a result, not a failure of the program.** In any system that talks over a network there are calls that
get cut off halfway. The destination may have written or not. Calling it a "failure" leads to two errors: resending it
by hand (and duplicating, if there is no idempotency) or giving it up for lost (when it did arrive). You saw it in
`verifica`: the API registered order 4002 ten seconds after the channel cut off.

**A 4xx and a 5xx are not treated the same.** The 4xx is the destination saying "what you sent me is wrong, or I do
not accept it". The 5xx is the destination saying "something happened to me". The first does not change if you repeat
it; the second sometimes does. But not every 4xx is a "no": a 408 (you took too long to send it), a 425 (it is too
early) or a 429 (you are going too fast) say "not now, try later". That is why they are retried here, and if they
carry `Retry-After`, you wait what they ask. With a real destination, each code is classified according to what its
contract says; this table is the one for the course's API.

**The attempt is written before sending.** It is the only way that, after a crash, you can find what was left in the
air. Banks do the same with a transfer: first they record it as "in process", then they send it.

**Reconciling is going to look at the destination.** When the router cannot know, it marks it for someone who can:
whoever has access to the API, the ERP or the partner and checks whether the order made it.

## Exercise: seven responses

1. Classify these seven responses as `aceptada`, `rechazada` or `no_se_sabe`, and write for each one what evidence
   decides it:

   | # | What the channel returned |
   |---|---|
   | a | 201, the API created the order |
   | b | 200, the API already had that order with that same key (lesson 02) |
   | c | 422 `llave-reusada`: the same key with different content |
   | d | 503, the API is under maintenance |
   | e | 504, the channel cut off at 3 seconds |
   | f | None: the int-rest container restarted halfway (code `0`) |
   | g | 429 with `Retry-After: 3`: the API received too many in a short time |

2. In the database, after running `./verifica`, look at the attempts for order 4002:
   `SELECT n, desenlace, respuesta->>'codigo' AS codigo FROM intento i JOIN operacion p ON p.id = i.operacion
   WHERE p.orden = '4002' AND p.canal = 'rest' ORDER BY n;`
   And then ask the API: `curl -s localhost:8081/pedidos/4002`. Write in one sentence what you would tell the person
   who has to reconcile that operation.
3. If the attempt were written **after** calling the channel and the router went down with the call on its way, what
   would be left in the database? What would happen to that order?

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [Three outcomes, decided by the evidence: accepted, rejected or unknown](../decisiones/24-tres-desenlaces.md)

## Review

1. What three outcomes can an attempt have, and what evidence decides each one?
2. Why is a 422 not retried while a 429 or a 504 is? Why does the `when` for the 429 go before the one for the range?
3. What is `conciliacion_pendiente`, and why is it not called "failed"?
4. Why does the channel answer 504 when its timeout expires, instead of a 500 or a 422?
5. **The closing question:** the attempt is written before sending. Use order 4002 to explain what would be lost if it
   were written afterwards.

**Next:** in lesson 21 the destination is the partner, and the partner does not answer in the same call. The 850
stays in its SFTP and the acknowledgment arrives later, in another file. Dropping is not confirming.
