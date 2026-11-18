# Lesson 28 — One topic, one group per integration

> **What you will achieve:** having the router stop calling each channel and waiting for its response. Instead, it
> publishes each lifecycle event of the order to a Kafka **topic**, and each integration reads it at its own pace. If the
> partner is stopped for an hour, the API and the ERP carry on, and when the partner comes back it catches up by
> itself: nobody resends anything to it.

**Before you start you need:** lessons 12, 17 and 27 finished and the environment from lesson 00 turned on. And Kafka,
which from today on is turned on separately (once):

```bash
docker compose -f infra/compose.yaml --profile kafka up -d --wait
```

## What is new today, in one sentence

Up to lesson 27, the router talks to each channel over HTTP. If int-edi is stopped, the router retries, opens the
circuit breaker and in the end leaves the operation in reconciliation: someone has to do a rerun by hand when int-edi
comes back. The router carries the burden of each channel being up. Today that changes: the router **publishes** one
Kafka event per lifecycle event to the `ordenes` topic, and each integration **reads** it when it can. Two ideas make it work: the
**key** of the event and the **group** of each integration.

**The picture:** the neighborhood newspaper. The delivery person leaves one copy at each house, not one for the whole
street. If the people in one house went on vacation, the newspapers pile up at the door and, when they come back, they
read them in sequence; the other houses did not wait for anyone. And in a house with three siblings, **one** newspaper
arrives: whoever grabs it first reads it, not all three.

| In the picture | In the course |
|---|---|
| Each day's newspaper | An event: one lifecycle event of an order |
| Each house | A group: `int-rest`, `int-bd`, `int-edi` |
| The siblings in one house | The 3 replicas of int-bd, which share their group |
| The newspapers piled up at the door | What the group has not read yet: Kafka keeps it for the group |

---

## 1. Set up your folder

```bash
cd lecciones/28-un-topico-un-grupo-por-integracion
cp -R inicio mio
```

Starting today there are new pieces. Read them before writing:

| File | What it does | Do you write it? |
|---|---|---|
| `orden-router/reparto.camel.yaml` | It no longer calls the channels: it publishes an event to the `ordenes` topic | **Yes, TODO 1** |
| `int-rest/kafka.camel.yaml`, `int-bd/…`, `int-edi/…` | The **bridge** of each integration: it reads the topic and hands the event to its usual route (`/integrar`, `/embarque`, `/entrega`) | **Yes, TODO 2** |
| `orden-router/desenlaces.camel.yaml` | What each channel answered comes back through another topic, `desenlaces`, and only the router reads it | No, read it |

What each channel does with its destination **did not change**: only how the work reaches it. The router is still the
only one that writes to the state database, and it still decides which channels each order applies to (lesson 11):
the event carries the operation and the attempt for each channel it applies to, and a bridge that does not find its
own lets it pass.

Two things you will notice starting today:

- **The router's circuit breaker (lesson 22) is gone.** Its job was to not keep insisting on a channel that is down;
  now the router does not talk to the channel, so there is nobody to insist on. Kafka keeps the event until the
  channel reads it.
- **The rerun (lesson 23) answers `202` `enviado` ("sent")** as soon as Kafka has received the event, not when the
  channel has finished.

Open `infra/compose.yaml` and find `KAFKA_NUM_PARTITIONS: 3`, and in the lesson's `compose.yaml`, `replicas: 3`.

## 2. Predict

Answer on paper:

1. The three integrations read with the **same** group, `integraciones`. How many of them receive order 4001?
2. If int-edi is stopped and orders 4002 and 4003 arrive, what happens with the API and the ERP? And what happens
   with the partner's 850s when int-edi comes back?
3. The topic has 3 partitions and int-bd runs in 3 replicas. If an event **carries no key**, can the lifecycle events of the
   same order land in different partitions? Why would that matter?

Look at how it is today:

```bash
./verifica inicio
```

The first delivery check comes out red: order 4001 reaches **one** of its three channels, and the other two stay in
`enviada`. With a single group, Kafka treats int-rest, int-bd and int-edi as three replicas of the same thing, and
gives each event to only one. And the last check too: no event carries the order as its key.

## 3. Set the key and separate the groups

### TODO 1: the key of the event

In `mio/orden-router/reparto.camel.yaml`, find TODO 1, right before the `to: kafka:ordenes`. Camel's kafka component
takes the key from the `CamelKafkaKey` header. The order is already in the `orden` variable:

```yaml
- setHeader:
    name: CamelKafkaKey
    expression:
      simple:
        expression: "${variable.orden}"
```

Kafka sends everything that carries the same key to the **same partition**, and within a partition the ordering is
preserved. That way, the creada, the embarcada and the entregada lifecycle events of order 4001 are read in the sequence in
which they were published. A partition is read by a single replica of each group at a time: if the group rebalances (a
replica goes down or another one arrives), the partition is reassigned to another replica, which keeps reading it in the same sequence. Without a key, each
event lands anywhere, and two different replicas could handle two lifecycle events of the same order, each at its own pace.

### TODO 2: one group per integration

In the three `kafka.camel.yaml` files, change `groupId: integraciones` to the name of the integration:

| File | `groupId` |
|---|---|
| `mio/int-rest/kafka.camel.yaml` | `int-rest` |
| `mio/int-bd/kafka.camel.yaml` | `int-bd` |
| `mio/int-edi/kafka.camel.yaml` | `int-edi` |

With that you have the two ways of distributing that Kafka offers, at the same time:

| | Between different groups | Between replicas of the same group |
|---|---|---|
| Who receives an event | **All** the groups, each one its own copy | **One** single replica |
| The pattern is called | *Publish-Subscribe* | *Competing Consumers* |
| In the course | The API, the ERP and the partner receive each order | The 3 replicas of int-bd are assigned the 3 partitions |

Each group keeps **its own count** of how far it has read: the *offset*. That is why a stopped group does not stop
the others, and when it comes back it continues where it left off.

## 4. Break one on purpose

With your version finished, remove the `setHeader` for `CamelKafkaKey` and run `./verifica`.

First, predict: the three integrations have their group. Do the orders arrive?

They arrive, and almost all the checks come out green. Only the last one comes out red: the events no longer carry
the order as their key. With one order at a time nothing is noticeable, and that is why this error is treacherous: it
shows up the day two lifecycle events of the same order arrive together and two different replicas of int-bd handle them. The
state machine from lesson 17 parks the one that arrives ahead of time, but the key keeps it from happening. **That it
works today does not prove that the ordering is guaranteed.** Put the key back.

## 5. Check

```bash
./verifica
```

`verifica` recreates Kafka empty, sends order 4001 with everything up, **stops int-edi**, sends orders 4002 and 4003,
checks that the API and the ERP carried on and that the partner does not have its 850s, turns int-edi back on and
waits for it to catch up. At the end it reads the whole topic with a separate reader to check the key of each event.
It is done when you see the seven green checks:

```
✅ Arrancaron el router (que publica en Kafka), los tres canales con su puente y el sondeo
✅ La 4001 llegó por Kafka a sus tres canales: la API, el ERP y el socio
✅ Con int-edi detenido, la API y el ERP recibieron la 4002 y la 4003 (API 2 de 2, ERP 2 de 2)
✅ El socio todavía no tiene sus 850, y Kafka se los guarda: el grupo int-edi va 2 eventos atrás
✅ int-edi volvió y se puso al día solo: 2 archivos 850 en el socio, y el router no reenvió nada (2 intentos para 2 operaciones)
✅ Los tres grupos (int-rest, int-bd, int-edi) quedaron al día: 0 eventos por leer
✅ Los 3 eventos del tópico ordenes llevan la orden como llave
```

Read the fifth one: `2 intentos para 2 operaciones` ("2 attempts for 2 operations"). Up to lesson 27, a channel that
was down ended in reconciliation and a rerun by hand. Today nobody did anything: int-edi read what Kafka kept for it.

See for yourself how far each group has read, with the services up:

```bash
docker compose -f ../../infra/compose.yaml exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --all-groups
```

The `LAG` column is what each group still has to read in each partition.

**Not working?** `./verifica solucion` tests ours, and `diff -r mio solucion` tells you where they differ.

| What you see | What to do |
|---|---|
| `Kafka no levantó` | Turn it on by hand with the command from "Before you start" and try again |
| `A la base de estado le faltan tablas de lecciones anteriores` | Your database is from before: recreate it with the command that comes in the message (`--force-recreate -V estado`) |
| `La 4001 no llegó a sus tres canales` with one of them in `enviada` | That channel does not have its own group: check the `groupId` in its `kafka.camel.yaml` |
| `Con int-edi detenido, la API y el ERP debían seguir` | Check the `groupId` of int-rest and int-bd: if they share a group with another one, they take events away from each other |
| `el grupo int-edi va -1 atrás` | The `int-edi` group does not exist: int-edi reads with another group name |
| `De … eventos del tópico ordenes, … llevan la orden como llave` | TODO 1 is missing, or the `setHeader` ended up after the `to: kafka:ordenes` |
| `El puerto 8080 está ocupado` | The services from another run were left up: `DONDE=mio docker compose -f compose.yaml down` |

---

## What this teaches beyond the course

**Publishing is not calling.** Whoever calls needs the other side to be up; whoever publishes only needs the topic to
be up. The router stopped carrying the availability of each channel, and that is what is called **decoupling**.

**The group decides who receives.** The same topic serves to distribute work among replicas (one group) and to notify
several systems (several groups). Getting the group wrong is the most common mistake when starting with Kafka, and it
gives no error: the events simply reach someone else.

**The key decides the ordering.** Kafka does not promise ordering across the whole topic, only within a partition. If
something has to be read in sequence, it has to carry the same key.

**What has not been read is kept.** A stopped consumer loses nothing as long as Kafka retains the event: when it
comes back, it continues from its offset. That changes what "the partner was down for an hour" means.

## Exercise: the neighborhood newspaper

1. With your services up, stop int-bd (`DONDE=mio docker compose -f compose.yaml stop int-bd`), send an order and run
   the `kafka-consumer-groups.sh` command. Which group has `LAG`? Turn it on and run it again.
2. On paper: if tomorrow the partner asks to also receive the delivery notices, and someone in finance wants a new
   system that reads **all** the orders, how many groups are there? What do you change in the router for the new
   system?
3. The 3 replicas of int-bd are assigned 3 partitions. What would happen if you ran 5 replicas? (Hint: a partition is read
   by a single replica of each group.)

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [One Kafka event per lifecycle event in a topic, with the order as the key, and one consumer group per integration](../decisiones/32-un-topico-un-grupo-por-integracion.md)

## Review

1. What is a topic, and what changes for the router when it publishes instead of calling?
2. What does the key of an event do? Why is the key the order?
3. What is the difference between two replicas of the same group and two different groups?
4. What is the offset, and why does a stopped group not stop the others?
5. **The closing question:** the partner was stopped for an hour. Explain, step by step, why no rerun was needed
   today and why one was needed up to lesson 27.

**Next:** in lesson 29 the problem moves to the other side: what happens if int-bd reads an event and does not manage
to leave its outcome? Does Kafka consider the event read?
