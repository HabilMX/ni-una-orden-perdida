# Lesson 12 — Growing in replicas

> **What you will achieve:** run **three replicas** of int-bd without touching the other services, measure how the
> orders are spread among them, and find out why at first they are not spread at all.

**Before you start you need:** lesson 11 finished and the environment from lesson 00 turned on (with the ERP recreated
in lesson 10).

## What is new today, in one sentence

Buen Fin, the big sales weekend, arrives and the store sends three times as many orders. The bottleneck is int-bd,
which writes to the ERP. Today you **scale a single service**: instead of one replica of int-bd you run three, and the
router keeps calling them by the same name, `int-bd`.

**The picture:** a bank branch that opens two more teller windows. The customers keep lining up in the same line,
and the bank puts someone there to send them to a free window. Today you will see what happens when that someone does
not do the job well.

A **replica** is an identical copy of the same service: the same image, the same folder, the same routes.
int-bd can be copied without an order being recorded twice because **it keeps nothing in memory**: everything that matters ends up in the ERP.

---

## 1. Set up your folder

```bash
cd lecciones/12-crecer-en-replicas
cp -R inicio mio
```

Today **you do not touch any route**. All the work goes in a single file, `mio/replicas.yaml`. Compose puts it
together with the lesson's `compose.yaml`, which is not touched: Compose merges them field by field: a
`command` in the second file replaces the one in the first, and in `environment` each variable is added or
replaces the one with the same name.

```bash
DONDE=mio docker compose -f compose.yaml -f mio/replicas.yaml up -d
```

It is the same idea as the `franquicias.yaml` of lesson 11: changing how a service runs without changing the service.

## 2. Predict

Today you use a new batch: `datos/ordenes/lote-30/`, the batch of 30 orders, all from company-owned stores (the
partner receives none). Answer on paper:

1. If you run three replicas of int-bd and send the 30 orders, how many does each replica record?
2. Who decides which replica each order goes to: the router, Camel, Docker or the ERP?

Check how it is today:

```bash
./verifica inicio
```

Two red marks come out: `int-bd corre en 1 copia(s) y se esperaban 3`, and `Con una sola copia no hay nada que repartir`
(with a single replica there is nothing to spread). `verifica` prints the spread, and today it is `int-bd-1=30`: a single
replica does everything.

## 3. Ask for three replicas

In `mio/replicas.yaml`, TODO 1. Ask for three replicas of int-bd with `deploy` → `replicas`:

```yaml
services:
  int-bd:
    deploy:
      replicas: 3
```

Run `verifica`:

```bash
./verifica
```

Look at the `Reparto:` line (the spread). What we saw when we tested it:

```
   Reparto: practica-retail-l12-int-bd-1=0 practica-retail-l12-int-bd-2=0 practica-retail-l12-int-bd-3=30
❌ 2 de 3 copias no registraron nada: el router sigue usando la misma conexión.
```

The three replicas are up, but **one does everything and two do nothing**. Did you guess 10/10/10? Almost nobody gets it
right, because it seems obvious. Before going on, **write down your explanation**: why were they not spread?

## 4. Why they are not spread

There are two reasons, and they go together.

**The connection is reused.** Opening an HTTP connection costs time, so the router opens **one** to `int-bd` and
leaves it open (*keep-alive*). All the orders that follow travel over that same connection, and a connection goes to a
single replica. Docker does not spread requests: it spreads **connections**.

**The JVM remembers the address.** When the router asks "where is `int-bd`?", Docker's DNS answers with the three
addresses, in a different order each time. But the router's JVM keeps the first answer and does not ask again for a
good while.

| | What it does today | What you need |
|---|---|---|
| The connection | Lives forever | For it to expire soon, so another one is opened |
| The address of `int-bd` | The JVM remembers it | For it to forget it soon, so it asks again |

With both of them short, each new connection asks for the address again and can land on another replica.

## 5. Make the router spread

TODO 2, in the same `mio/replicas.yaml`, inside `orden-router`. It is two things:

**a) The connection lives one second at most.** It is the `connectionTimeToLive` option of the `http` component, in
milliseconds. It is passed to the router at startup:

```yaml
    command: ["run", ".", "--prop=camel.component.http.connectionTimeToLive=1000"]
```

**b) The JVM remembers the address for one second.** It goes in an environment variable that the JVM reads at startup:

```yaml
    environment:
      JDK_JAVA_OPTIONS: "-Dsun.net.inetaddr.ttl=1"
```

Before adding b, try it: **add only a** and run `./verifica`. When we tested the lesson, with a alone the spread
stayed at `0/0/30`. The connection was already expiring, but the new connection went to the same address the JVM had
stored. Now add b and run again.

## 6. Check

```bash
./verifica
```

`verifica` restarts the API, empties the ERP, brings up the services with your `replicas.yaml` and sends the 30 orders
with a pause between each one, so that the router has a chance to open new connections. It is done when you see the six green checks:

```
✅ int-bd corre en 3 copias
✅ Arrancaron el router, int-rest, int-edi y las copias de int-bd
✅ ERP: tiene las 30 órdenes REGISTRADO, cada una una sola vez
✅ API: tiene las 30 órdenes (crecer int-bd no tocó a los demás)
   Reparto: practica-retail-l12-int-bd-1=12 practica-retail-l12-int-bd-2=12 practica-retail-l12-int-bd-3=6
✅ Entre todas las copias registraron 30 órdenes: ni una de más, ni una de menos
✅ (…) de 3 copias de int-bd registraron órdenes: el trabajo se repartió
```

**Your spread will be different**, and it changes on every run: when we tested it we saw 12/12/6 and 6/11/13. The one
that picks the replica is the network, and the network does not spread exactly. That is why `verifica` does not ask for
10/10/10: it asks that **at least two replicas receive work** and that **no order is lost or repeated**. It may happen
that one replica is left without orders in a run: if the other two shared the 30 between them, that is fine. Each replica
counts its own in its own log, so the spread is reported by the replicas, not by the router.

**Not working?** `./verifica solucion` tests ours, and `diff mio/replicas.yaml solucion/replicas.yaml` tells you
where they differ.

| What you see | What to do |
|---|---|
| `int-bd corre en 1 copia(s)` | The `deploy` → `replicas: 3` is missing, or it ended up under another service. Check the indentation |
| `2 de 3 copias no registraron nada` | You are missing a, b or both (step 5) |
| `No pude levantar los servicios. Revisa que mio/replicas.yaml sea YAML válido` | An indentation or quoting error; `verifica` shows you the last lines of the error |
| You put `CAMEL_COMPONENT_HTTP_CONNECTION_TIME_TO_LIVE` in `environment` and it does not spread | In this version that variable has no effect (we measured it: 0/29/0). Use the `--prop` from step 5 |
| `Tu ERP no tiene la columna correlacion` | Recreate the ERP as lesson 10 says |

---

## What this teaches beyond the course

**Work is spread per connection, and an eternal connection is not spread.** In Docker the DNS does the spreading. In
Kubernetes, a Service also picks a replica for each new connection, so the same problem shows up just the same. Who does
the spreading changes; the rule does not.

**The one second is there so that you can see it here**, not a figure for a real system. How long a connection should
live depends on how much traffic there is and on how often the replicas change. Closing it after every order also
spreads, but you pay the cost of opening a connection on every call.

## Exercise: growing without touching anyone

With your version finished, answer only with what you see on screen:

1. Change `replicas: 3` to `replicas: 5` and run `./verifica`. Which check turns red, and why? (Hint: `verifica`
   expects 3.) How many orders did each of the five record?
2. Go back to 3. Which files did you touch to go from one replica to three? Did you touch any route, any mapping or the
   lesson's `compose.yaml`?
3. Why can int-bd run as three replicas without an order being recorded twice? What would happen if each replica kept in
   its memory the orders it has already seen?

---

## What already works

With this lesson you close part 1. An order that the store creates already does all of this:

- it reaches the router over HTTP and the router answers with what each destination said;
- it reaches the API only once, even if it is resent, thanks to its key;
- it goes into the ERP complete or it does not go in, and int-bd can scale out to several replicas;
- it becomes an 850 that reaches the partner without being overwritten or left half-written, only if the store is the partner's;
- it carries a thread that follows it through all the services;
- and if one destination goes down, the others receive it all the same.

What it does not do yet: remember where each order stands. That starts in part 2.

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [Three replicas do not spread the work by themselves: the spread is per connection](../decisiones/15-repartir-por-conexion.md)
- [A single image; each service is a folder](../decisiones/09-una-imagen-un-servicio-por-carpeta.md)

## Review

1. What is a replica, and why can int-bd be copied without an order being recorded twice?
2. With `replicas: 3` and nothing else, how were the orders spread? Why?
3. What does `connectionTimeToLive` do, and what does `sun.net.inetaddr.ttl` do? Why is one of them alone not enough?
4. Why does `verifica` not ask for 10/10/10, and what does it ask for instead?
5. **The closing question:** if the router had replicas, where would what belongs to each order live? Write down your
   answer: you will compare it with lesson 15.

**Next:** in lesson 13: the shipment arrives. The warehouse drops a file, and now that
you know how to run replicas, the question is a different one: if several replicas read the same folder, who takes the
file?
