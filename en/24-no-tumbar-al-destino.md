# Lesson 24 — Do not knock the destination down

> **What you will achieve:** the ERP does not receive more registrations at the same time than the cap you set for it,
> even if int-bd runs in three replicas and the store sends 30 orders at once. Whatever goes over the cap waits its
> turn: it is not rejected.

**Before you start you need:** lessons 12 and 22 finished and the environment from lesson 00 turned on.

## What is new today, in one sentence

In lesson 22 the problem was a destination that was down. Today it is the other way around: the destination is fine,
and **you can knock it down yourself**. Starting with this lesson the router distributes up to 10 orders at a time
(we explain why below), and int-bd runs in 3 replicas, as in lesson 12. With 30 orders at once and an ERP that takes a
second and a half to register each one, the ERP gets to have 10 registrations in progress. A real ERP can take a
certain number of integration connections; past that number it gets slow for everyone, including the users who are
entering data by hand. Today you put a **cap** on it: the pattern is called *Throttler*.

**The picture:** the entrance to an elevator with a capacity of six. There are three doors in the lobby, and at each
one a guard who lets two through at a time. The seventh person does not leave: they wait in their line until someone
gets out. If the building opens a fourth and a fifth door, each with its guard of two, ten can reach the elevator.
**The capacity is per door**, and when you open doors you have to do the math again.

---

## 1. Set up your folder

```bash
cd lecciones/24-no-tumbar-al-destino
cp -R inicio mio
```

Before writing, two things that are already done and that you need to know.

**The distribution now serves 10 orders at a time.** Open `mio/orden-router/reparto.camel.yaml` and read the comment
on the `repartir` route: its `from` has `concurrentConsumers: 10`. Up to lesson 23 there was a single consumer, and
that is why in lesson 19 we said that retrying in place preserved the sequence. **Starting today that changes:**

| | Up to lesson 23 | From lesson 24 on |
|---|---|---|
| Orders distributed at a time | 1 | Up to 10 |
| The operations of **one** order | In line | In line, the same as before |
| Two **different** orders | In line | They can cross |
| Two lifecycle events of the same order that cross | Did not happen | The state machine from lesson 17 takes care of them: what arrives early is parked |

Why the change? Because with a single consumer, a batch of 30 orders takes 30 times as long as the slowest
destination takes. The price is today's problem: the router can now send 10 at a time to a destination.

**The ERP measures what it sees.** Every time the ERP registers an order, it writes in the `muestra_concurrencia`
table how many registrations it had in progress at that instant. The proof of the cap is not what you wrote in the
route: it is the maximum of that column.

Your work goes in `mio/int-bd/int-bd.camel.yaml`, in the `int-bd` route, at the beginning of its steps. Also open the
lesson's `compose.yaml` and find `replicas: 3`.

## 2. Predict

Answer on paper:

1. Remember lesson 12. With int-bd in 3 replicas and no cap, how many registrations at a time can the ERP see? (Hint:
   the router sends up to 10.)
2. The ERP can take 6 at a time. How many does **each** replica of int-bd have to let through?
3. With the cap in place, do the 30 orders take more or less time to be registered? Is any of them lost?

Look at how it is today:

```bash
./verifica inicio
```

The ERP registers all 30, and fast. But the concurrency check comes out red: the ERP saw **up to 10** registrations
at a time, when the maximum with 3 replicas and a cap of 2 would be 6.

## 3. Put in the cap

The TODO is in the `int-bd` route. What you write is a single step, at the beginning, before everything else:

```yaml
- throttle:
    mode: ConcurrentRequests
    expression:
      constant:
        expression: "…"        # cuántos a la vez, POR COPIA
```

You calculate the number yourself, with the elevator math:

| Fact | Value |
|---|---|
| What the ERP can take | 6 at a time |
| Replicas of int-bd (`compose.yaml`) | 3 |
| Cap per replica | 6 ÷ 3 = **2** |

Notice the `mode`. Camel has two ways of counting, and **the one it uses by default is not today's**:

| Mode | What it counts | Does it work for the ERP? |
|---|---|---|
| `TotalRequests` (by default) | How many **come in** per period, for example 2 per second | No: if each registration takes 1.5 s, 2 come in every second and they pile up |
| `ConcurrentRequests` | How many **are in progress**: they came in and have not answered yet | Yes: it is exactly what the ERP can take |

That is why the `mode` is written even though it looks like a detail. Without it, the cap exists, but it counts
something else.

And notice what the cap does **not** do: it does not reject. The third order that reaches a replica waits there for its
turn until one of the two in progress answers. For the router, that call just took a little longer: it is not an
error, and the router does not retry it.

## 4. Break one on purpose

With your cap finished, delete the line `mode: ConcurrentRequests` and run `./verifica`.

First, predict: the cap still says "2". Does the ERP see 6 at most?

No. Without `mode`, Camel uses `TotalRequests`: each replica lets 2 orders **come in** per second, no matter how many
are still in progress. Since each registration takes a second and a half, they pile up, and the ERP sees more than 6
again. The concurrency check comes out red. The number was the right one; what was wrong was **what it counted**. Put
the line back.

## 5. Check

```bash
./verifica
```

`verifica` brings up int-bd in 3 replicas, sends 30 orders at once and then asks the ERP how many registrations it saw
at a time. It is done when you see the five green checks:

```
✅ Arrancaron el router, los tres canales (int-bd en 3 copias) y el sondeo
✅ El ERP registró las 30 órdenes en (…) s
✅ El ERP vio a lo más (…) registros a la vez: no pasó de 3 copias × tope 2 = 6
✅ El tope hizo esperar, no rechazó: las 30 operaciones del ERP aceptadas al primer intento
✅ La API recibió las 30: el tope del ERP no frenó a los otros canales
   Registros por copia de int-bd: (…)
```

The last line is not a check: it tells you how many registrations each replica made. All three worked.

Compare the second check with the one from `inicio/`. With the cap, the 30 take longer: in our runs, about 16 seconds
against about 6; your `verifica` prints yours. **That is what it costs to take care of the ERP**, and it is a reasonable price: the ERP keeps serving everyone else.

Look at the ERP's count yourself:

```bash
docker compose -f ../../infra/compose.yaml exec erp mariadb -uintegracion -ppractica erp \
  -e "SELECT MAX(en_curso) AS maximo, COUNT(*) AS registros FROM muestra_concurrencia"
```

**Not working?** `./verifica solucion` tests ours, and
`diff mio/int-bd/int-bd.camel.yaml solucion/int-bd/int-bd.camel.yaml` tells you where they differ.

| What you see | What to do |
|---|---|
| `El ERP vio hasta 10 registros a la vez` | There is no `throttle`, or it ended up after the call to the ERP: it goes at the beginning of the steps |
| `El ERP vio hasta` more than 6, but fewer than 10 | `mode: ConcurrentRequests` is missing, or the cap is not 2 |
| `int-bd corre en 1 copia(s) y se esperaban 3` | You changed the `replicas` in the `compose.yaml`: set it back to 3 |
| `El puerto 8080 está ocupado` | The services from another run were left up: `DONDE=mio docker compose -f compose.yaml down` |

---

## What this teaches beyond the course

**The destination has a limit even if it does not say so.** An ERP, a database or a third party's API can take a
certain number of connections. An integration that scales out in replicas and in concurrency without a cap is the most
common way to knock down a system that was fine.

**The cap goes where the destination is known.** int-bd is the only one that talks to the ERP, so it is the one that
knows how much the ERP can take. Putting the cap in the router would mix what is known about all the destinations in
a single place.

**The cap is per replica, and scaling forces you to recalculate.** If tomorrow int-bd runs in 5 replicas, the same cap of
2 lets 10 through. The math is: what the destination can take divided by the replicas that talk to it.

**Making someone wait is not rejecting.** A cap that rejects forces the caller to retry, and little is gained by
that. A cap that makes callers wait only makes the call slower, and for the router it is still a "yes".

## Exercise: the elevator with more doors

1. On paper: the ERP can take 6 and int-bd runs in 3 replicas with a cap of 2. If the production ERP could take 20 and
   you wanted to run 4 replicas, what cap would you set? And with 6 replicas? Write down the math.
2. In the `compose.yaml`, change the int-bd replicas to `5`, without touching the cap, and run `./verifica`. The first
   check is going to come out red, because `verifica` expects 3. Read the third one: how many registrations at a time
   did the ERP see? Did it go over 6? Write down what you would change in `int-bd.camel.yaml` so that it would not,
   and set the replicas back to 3.
3. Starting today the distribution serves 10 orders at a time. Explain in two sentences why that does not scramble
   the lifecycle events of **one** same order. (Hint: lesson 17.)

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [One cap per replica toward the ERP, counting what is in progress](../decisiones/28-un-tope-por-copia.md)

## Review

1. What is a Throttler, and what problem does it solve here?
2. Why does the cap go in int-bd and not in the router?
3. What is the difference between `TotalRequests` and `ConcurrentRequests`? Why is the second one the right one today?
4. What happens to an order that arrives when its replica already has 2 in progress?
5. **The closing question:** the ERP can take 6 and today it runs with 3 replicas. How many registrations at a time can
   it see with 5 replicas and the same cap, and what would you have to change when scaling?

**Next:** in lesson 25 the question is a different one: the API and the ERP already accepted and the partner has not
sent its 997. When is an order **complete**?
