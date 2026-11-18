# Lesson 22 — Taking a channel down

> **What you will achieve:** when the partner's SFTP goes down, the API and the ERP keep receiving their orders right
> away. The router stops calling the destination that is down, records what it did not send, and tests it again on its
> own after a while.

**Before you start you need:** lesson 20 finished and the environment from lesson 00 turned on.

## What is new today, in one sentence

In lesson 10 you turned off the ERP and saw that the API and the partner received the order anyway: "a channel that is
down does not hold up the other two". Since lesson 19 that is no longer true. The router retries in place, with pauses that add up to half a minute, and **while it waits for the
partner, the whole queue waits with it**. An order that only had to reach the API takes half a minute because of
a destination that has nothing to do with it. Today you put in one **circuit breaker** per channel: when a destination
fails repeatedly, the router stops calling it for a while.

**The picture:** the breakers in your home's electrical panel, one per area. If the dryer shorts out, the laundry
room breaker trips and the kitchen still has power. You do not ask anyone to stop using the dryer: the breaker cuts
off on its own. And once you have checked, you flip it back up and test. If there were a single breaker for the whole
house, a short in the laundry room would leave you without a refrigerator.

---

## 1. Set up your folder

```bash
cd lecciones/22-tirar-un-canal
cp -R inicio mio
```

All your work goes in `mio/orden-router/reparto.camel.yaml`, in the `intentar` route, in step 3: the call to the
channel. Read it before writing. The comment at the top describes the three states of a circuit breaker:

| State | What it does | When it changes |
|---|---|---|
| **Closed** | Calls the channel, as always | It **opens** if, of the last 4 calls (with at least 2), half or more failed |
| **Open** | Does **not** call the channel: answers right away that no attempt was made | After 10 seconds, it becomes **half-open** |
| **Half-open** | Lets **one** test call through | If it goes well, it **closes**; if it fails, it opens again |

And there is a route already done, `sin-respuesta`, further down in the same file. It is the one that runs when the
circuit breaker does not let the call through or when the call failed. It tells the two cases apart, and that matters:

| Case | `codigo` | Outcome of the attempt | The operation |
|---|---|---|---|
| The circuit breaker was open and **no call was made** | `-1` | `no_enviado` | Stays `pendiente` |
| The call was made and failed | `0` | `no_se_sabe` (lesson 20) | It is retried |

`no_enviado` is not a doubt: the router knows it did not call. That is why it is not retried in place and does not go
to reconciliation. It stays `pendiente`, and in lesson 23 you will see how it gets sent again.

## 2. Predict

Answer on paper:

1. You take down the partner's SFTP and four franchise orders arrive. Without a circuit breaker, how long does the
   fourth one take to reach the API? (Hint: add up the pauses from lesson 19.)
2. Why one circuit breaker per channel and not one for the whole router?
3. The SFTP comes back. Who has to "flip the breaker back up"?

Look at how it is today:

```bash
./verifica inicio
```

`verifica` turns off the SFTP, sends four orders and counts how many reached the API and the ERP in about 25 seconds.
**One out of four** arrives. Order 4001 goes through to the API and the ERP, and then the queue sits waiting for the
partner's retries; the other three do not move forward even though the API and the ERP are perfectly fine.

## 3. Put in one breaker per channel

In step 3 of `intentar` there is a `doTry` that calls `direct:llamar-canal`. That `doTry` is what you are going to
replace. It has three TODOs:

| TODO | What you write | What for |
|---|---|---|
| 1 | A `choice` on `${variable.canal}` with three branches (`rest`, `bd`, `edi`), and in each one a `circuitBreaker` with its `id`: `cortacircuito-rest`, `cortacircuito-bd`, `cortacircuito-edi` | One breaker per area |
| 2 | In each `circuitBreaker`, its `resilience4jConfiguration` | When it opens and how long it takes to test |
| 3 | In each one, an `onFallback` that calls `direct:sin-respuesta` | So it is recorded whether no call was made or the call was made and failed |

This is what **one** branch looks like. The other two are the same, with their channel and their `id`:

```yaml
- choice:
    when:
      - expression:
          simple:
            expression: "${variable.canal} == 'rest'"
        steps:
          - circuitBreaker:
              id: cortacircuito-rest
              resilience4jConfiguration:
                slidingWindowSize: 4
                minimumNumberOfCalls: 2
                failureRateThreshold: 50
                waitDurationInOpenState: "10000"
                permittedNumberOfCallsInHalfOpenState: 1
                automaticTransitionFromOpenToHalfOpenEnabled: true
              steps:
                - to:
                    uri: direct:llamar-canal
              onFallback:
                steps:
                  - to:
                      uri: direct:sin-respuesta
      # … la rama de bd y la de edi
```

Each line of the configuration is a row of the state table:

| Parameter | Value | What it says |
|---|---|---|
| `slidingWindowSize` | 4 | Looks at the last 4 calls |
| `minimumNumberOfCalls` | 2 | Does not decide with fewer than 2 |
| `failureRateThreshold` | 50 | Opens with 50 % of failures or more |
| `waitDurationInOpenState` | `"10000"` | Open for 10 seconds (it goes in milliseconds and in quotes) |
| `permittedNumberOfCallsInHalfOpenState` | 1 | When half-open, a single test call |
| `automaticTransitionFromOpenToHalfOpenEnabled` | `true` | Moves to half-open on its own, without waiting for someone to call |

You write the configuration three times, the same. It could be written once and reused; here it goes in full in each
channel so that each breaker can be read on its own.

A note for later: if one day you compile this route to native with Camel Quarkus, the circuit breaker is provided by
a different library, but the `circuitBreaker` in the YAML is the same and you change nothing.

## 4. Break one on purpose

With your three circuit breakers finished, change the `id` of all three to a single one, `cortacircuito`, as if there
were a single breaker for the whole router. Run `./verifica`.

First, predict: the API and the ERP are fine. Do they stay up to date?

No. The circuit breaker counts the calls of the three channels together: the partner's fail, the API's and the ERP's
do not, and with half of them failing it opens **for everyone**. The API and the ERP stop receiving orders because of
a destination that is not theirs, and the log shows the circuit breaker for `rest` and the one for `bd` as open. The
check for the API and the ERP and the one for the log come out red. Put the three `id` values back.

## 5. Check

```bash
./verifica
```

`verifica` turns off the SFTP, sends four orders, checks the API, the ERP and the state; then it turns the SFTP back
on, waits for the 10 seconds to pass and sends a fifth one. On exit, it leaves the SFTP on. It is done when you see
the five green checks:

```
✅ Arrancaron el router (con un cortacircuito por canal), los tres canales y el sondeo
✅ Con el SFTP caído, la API y el ERP siguieron al día: las 4 órdenes en los dos, en (…) s
✅ El canal del socio: las 4 quedaron pendientes, su último intento «no_enviado», y de la 4002 en adelante con un solo intento (no esperaron)
✅ Log: se abrió el cortacircuito del socio, y solo ese
✅ Volvió el SFTP: la 4005 pasó por el cortacircuito ya cerrado y su 850 está en el socio
```

Read the third one calmly. Order 4001 did try several times: the circuit breaker was still closed, and the failures of
those attempts are what opened it. From order 4002 onward there is a single attempt, `no_enviado`: the router did not
even call.

And look at the router's log while it happens:

```bash
DONDE=mio docker compose -f compose.yaml logs orden-router | grep -i cortacircuito
```

**Not working?** `./verifica solucion` tests ours, and
`diff mio/orden-router/reparto.camel.yaml solucion/orden-router/reparto.camel.yaml` tells you where they differ.

| What you see | What to do |
|---|---|
| `a los (…) s la API tiene 1 de 4` | There is no circuit breaker, or the partner's never opens: check that the `edi` branch exists and that `failureRateThreshold` is 50 |
| `El canal del socio quedó` with 0 not sent | The `onFallback` to `direct:sin-respuesta` is missing: without it, nothing records the `no_enviado` |
| `esperaba el cortacircuito de edi abierto y los de rest y bd cerrados` | Two branches share an `id`: each channel has its own |
| `Volvió el SFTP y la 4005 quedó «pendiente»` | The circuit breaker does not move to half-open: `automaticTransitionFromOpenToHalfOpenEnabled: true` is missing, or `waitDurationInOpenState` is much greater than 10000 |
| `No arrancaron todas las rutas` | Check the indentation: `steps` and `onFallback` go inside `circuitBreaker`, at the same level as `resilience4jConfiguration` |
| `El puerto 8080 está ocupado` | The services from another run were left up: `DONDE=mio docker compose -f compose.yaml down` |

If `verifica` is cut off halfway, the SFTP turns itself back on when it exits. If you still see it off:
`docker compose -f ../../infra/compose.yaml start socio-sftp`.

---

## What this teaches beyond the course

**A destination that is down must not knock down the ones that are fine.** It is the idea behind the circuit breaker
and behind a ship's "compartments": if one floods, the rest stay dry. Without that, the smallest failure in the
system decides the speed of everything.

**Not calling what you know is down is a favor to both sides.** You save yourself the wait, and the destination does
not get a wave of calls while it is trying to come back.

**The circuit breaker recovers on its own.** The test call in half-open means nobody has to remember to "flip the
breaker back up". If the destination is still in bad shape, it opens again and nothing happened.

**"I did not send it" is a fact, and it is recorded as such.** A `no_enviado` attempt says something different from a
`no_se_sabe` one. The first can be sent again without fear, because the destination never saw it.

## Exercise: the breaker in three stages

With your finished version, run `./verifica` and then:

1. With the `orden-router` log, build a timeline of the run: when the circuit breaker for `edi` opened, how many
   orders it left unsent and when the test call went through. Mark the three states on it: closed, open and
   half-open.
2. In the database, query the attempts of the partner's channel:
   `SELECT p.orden, i.n, i.desenlace, i.respuesta->>'cuerpo' FROM intento i JOIN operacion p ON p.id = i.operacion
   WHERE p.canal = 'edi' AND p.momento = 'creada' ORDER BY p.orden, i.n;`
   What does the response of a `no_enviado` attempt say? And that of a `no_se_sabe` one?
3. The partner's four orders stayed `pendiente`. Nobody is going to send them on their own. What is the router missing
   in order to recover them? Write down your answer: it is lesson 23.

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [One circuit breaker per channel, in the router](../decisiones/26-un-cortacircuito-por-canal.md)

## Review

1. What three states does a circuit breaker have, and what does it do in each one?
2. What does each number in the configuration mean: 4, 2, 50, 10000 and 1?
3. Why one circuit breaker per channel and not one for the whole router?
4. How does a `no_enviado` attempt differ from a `no_se_sabe` one? Why does the first not go to reconciliation?
5. **The closing question:** in lesson 10 you concluded that "a channel that is down does not hold up the other two".
   Why did it stop being true in lesson 19, and what makes it true again? Use what you saw in the `verifica` run for
   `inicio/` and in yours.

**Next:** in lesson 23 someone has to resend what was left pending or in reconciliation. How do you reprocess an
order without duplicating it and leaving written who did it and why?
