# Lesson 19 — The destination does not answer

> **What you will achieve:** when the API does not answer, the router tries again on its own, each time with a longer
> pause, until the order arrives. And in the meantime the shipment for that order does not jump ahead: the API has to
> receive the order first and its shipment afterwards.

**Before you start you need:** lesson 18 finished and the environment from lesson 00 turned on.

## What is new today, in one sentence

In lesson 15 the router learned to save before answering, and in lesson 18 to recognize a repeated message. Today the
opposite of lesson 18 happens: the message **does not arrive**, because the destination does not answer. The router
cannot know whether the API received the order; what it can do is try again. And since it already knows how to
recognize repeats (the API does too: you saw it in lesson 02 with the key), retrying does not duplicate anything.

**The picture:** calling someone who does not pick up. You do not call every second for an hour: you call, wait a
minute, call again, wait two, then four. You give them time to get out of the meeting. And you do not tell them the
second thing before the first: if the call was to let them know their package already left, you do not first send a
message saying "did it arrive yet?".

---

## 1. Set up your folder

```bash
cd lecciones/19-el-destino-no-contesta
cp -R inicio mio
```

Today all the work goes in a new router file, `orden-router/reintentos.camel.yaml`. Open it and read the whole comment
at the top. It has a drawing:

```
intento 1 ✗ ─ 1 s ─ intento 2 ✗ ─ 2 s ─ intento 3 ✗ ─ 4 s ─ intento 4 ✓     (más o menos 15 % al azar)
```

And three rules that are already in the code:

| Rule | What it means | Where it lives |
|---|---|---|
| **Only the router retries** | The channels (int-rest, int-bd, int-edi) answer what happened to them and that is it | The channels have no retries |
| **Only what was left as "unknown" is repeated** | No response, or a 5xx error. A 4xx from this API (its 422) is a "no" from the destination and is not repeated (in lesson 20 you will see that not every 4xx is a "no") | `direct:intentar` throws an `IOException` only in that case |
| **Every attempt leaves its row** | Before calling the channel, `intentar` writes to the `intento` table; afterwards it records how it went | `reparto.camel.yaml`, since lesson 15 |

## 2. Predict

Answer on paper:

1. The API is off when order 4001 arrives, and it comes back a few seconds later. In the meantime, the warehouse
   drops the shipment for order 4001. In what sequence should the order and the shipment reach the API? And what if
   they arrived the other way around?
2. If the router retries every 100 milliseconds, what happens to an API that is starting up?
3. The ERP and the partner were up. How many times should they receive order 4001?

Look at how it is today:

```bash
./verifica inicio
```

The store gets its 202, because the router saved. But the rest channel tries **only once**: the API was off, the
attempt is left as `no_se_sabe` and nobody tries it again. The API comes back a few seconds later and order 4001
never reaches it.

## 3. Retry with an increasing delay (backoff)

Open `mio/orden-router/reintentos.camel.yaml`. At the top there is a `routeConfiguration` with an `onException` that
catches the `IOException` thrown by `intentar`. Today it only notes in the log that it gave up. Your job is to tell it
**how many times and with what pause** to retry, with a `redeliveryPolicy` inside the `onException`:

| TODO | What you write | What for |
|---|---|---|
| 1 | `maximumRedeliveries: 5` and `redeliveryDelay: 1000` | Up to five retries; the first one, after one second |
| 2 | `useExponentialBackOff: true` and `backOffMultiplier: 2` | So each pause is double the previous one: 1, 2, 4, 8, 16 seconds |
| 3 | `useCollisionAvoidance: true` and `collisionAvoidanceFactor: 0.15` | So each pause varies by a random 15 % |
| 4 | `retryAttemptedLogLevel: WARN` | So each retry shows up in the log |

The `redeliveryPolicy` goes at the same level as `handled` and `steps`, inside the `onException`. This is what the
skeleton looks like:

```yaml
- onException:
    exception:
      - java.io.IOException
    redeliveryPolicy:
      maximumRedeliveries: 5
      …
    handled:
      constant:
        expression: "true"
    steps:
      - log: …
```

Notice two things:

- **The random variation (jitter) of TODO 3 looks like a whim and it is not.** Imagine three replicas of the router (lesson 12) that
  lose the API at the same time. Without randomness, all three retry at exactly one second, then at two, then at
  four: the API comes back and takes three hits together every time. With a 15 % variation, the hits spread apart.
- **The retry is "in place".** The `seda:repartir` queue has a single consumer, and the pause stops it. What comes
  behind in the queue, like the shipment for order 4001, waits its turn. That is what preserves the sequence.

## 4. Break one on purpose

With your retries finished, change `maximumRedeliveries` to `1` and run `./verifica`.

First, predict: `verifica` leaves the API off for about six seconds. With a single retry after one second, does the
order reach the API?

It does not. The first attempt fails, the retry after one second does too, and the router gives up while the API is
still off. The check for the rest attempts and the one for the API come out red. The operation stays as `no_se_sabe`,
and that is correct: the router **does not make up** that it delivered it. But it shows the cost of falling short:
five retries with backoff cover about thirty seconds of downtime; a single one, one second. Go back to `5`.

## 5. Check

```bash
./verifica
```

`verifica` turns the API off, sends order 4001, drops its shipment in the mailbox, waits a few seconds and turns the
API back on. It is done when you see the six green checks:

```
✅ Arrancaron el router (con sus reintentos), los tres canales y el sondeo
✅ Con la API apagada, la tienda recibió su 202: lo suyo quedó guardado
✅ Estado: el canal rest reintentó en el lugar: (…) intentos; esperas (…); desenlaces (…)
✅ Estado: el ERP y el socio no se repitieron: aceptados al primer intento (bd=aceptada/1 edi=aceptada/1)
✅ API: recibió la orden y después su embarque, y la 4001 quedó EMBARCADO
✅ ERP: la 4001 está EMBARCADO
```

Read the third check calmly. It tells you how many attempts there were, how long it waited between one and the next
and how each one ended. The waits change on every run because of the randomness, but they always grow, and the last
attempt is the only `aceptada`.

**Not working?** `./verifica solucion` tests ours, and
`diff mio/orden-router/reintentos.camel.yaml solucion/orden-router/reintentos.camel.yaml` tells you where they
differ.

| What you see | What to do |
|---|---|
| `esperaba 3 o más intentos de rest` and there is 1 | The `redeliveryPolicy` is missing, or it ended up outside the `onException` |
| `esperaba 3 o más intentos de rest` and the waits do not grow | `useExponentialBackOff: true` is missing |
| `esperaba la orden antes que el embarque` and the order `no llegó` (did not arrive) | Too few retries or pauses that are too short: the router gave up before the API came back |
| `El ERP y el socio quedaron` with more than one attempt | You put retries somewhere else too: they only go in `reintentos.camel.yaml` |
| `El puerto 8080 está ocupado` | The services from another run were left up: `DONDE=mio docker compose -f compose.yaml down` |

If `verifica` is cut off halfway, the API turns itself back on when it exits. If you still see it off:
`docker compose -f ../../infra/compose.yaml start api-pedidos`.

---

## What this teaches beyond the course

**Retrying is only safe if the destination recognizes repeats.** The router does not know whether the first attempt
arrived. If it arrived and the router retries, the API receives the order twice, and what prevents the duplicate is
its idempotency key (lesson 02). Retries without idempotency are duplicates under another name.

**A growing wait with randomness is the usual practice**, and it has a name: *exponential backoff with jitter*. The
large cloud providers recommend it for any client of their services. With a fixed wait, you either give too much time
to the one that already came back or you hammer the one that is still starting up.

**Only one retries.** If the router retries five times and the channel also five, a single send can turn into
thirty-six calls. One place is chosen, and the rest answer what happened to them.

**Sequence has a price.** Retrying in place stops the queue: while the API does not answer, nothing that comes behind
moves forward. Here it is worth it, because a shipment before its order is an error. When a destination that is down
holds up the others too much, another tool is needed: that is lesson 22.

## Exercise: the log of attempts

With your finished version, run `./verifica` and go into the database:

```bash
docker compose -f ../../infra/compose.yaml exec estado psql -U router -d estado
```

1. Query the rest channel's attempts for the creation of order 4001, with their time:
   `SELECT i.n, i.inicio, i.desenlace FROM intento i JOIN operacion p ON p.id = i.operacion WHERE p.orden = '4001'
   AND p.momento = 'creada' AND p.canal = 'rest' ORDER BY i.n;`
   Subtract by hand the time of each attempt minus that of the previous one. Do they look like 1, 2, 4 and 8 seconds?
   Why are they not exact?
2. Add up the five pauses of the policy (1 + 2 + 4 + 8 + 16). If the API took a minute to come back, what would happen
   to the order? What state would it be left in, and what would it take for it to arrive? (Lesson 23 solves it.)
3. Explain in your own words why the shipment for order 4001 arrived **after** the order, even though the warehouse
   dropped it while the API was off.

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [Retries happen in place, only in the router, with backoff](../decisiones/23-reintentar-en-el-lugar.md)

## Review

1. Which responses from the destination are retried and which are not? Why not this API's 422?
2. What is the point of each pause being double the previous one?
3. What is the 15 % of randomness for? Explain it with three replicas of the router.
4. Why did retrying not duplicate the order in the API?
5. **The closing question:** retrying "in place" preserves the sequence. Why, and what does it cost? Use the shipment
   for order 4001 as an example.

**Next:** in lesson 20 the API does answer, but it does not always say "yes". Sometimes it says "no", and sometimes it
takes so long that the call is cut off. Did it write or did it not?
