# Lesson 10 — All the channels

> **What you will achieve:** the router sends the same order, **at once**, to the three destinations (the API, the ERP
> and the partner), and answers the store with what each one said. And you see with your own eyes what happens when
> one of the three is down.

**Before you start you need:** lessons 05, 07 and 09 finished and the environment from lesson 00 turned on.

⚠️ **This lesson adds a column to the ERP** (`correlacion`, to store the thread from lesson 09). If your environment
was already up from before, recreate the ERP once, from the root of the course:

```bash
docker compose -f infra/compose.yaml up -d --wait --force-recreate -V erp
```

`--force-recreate` creates it again and `-V` erases its data, so it starts with the new tables. If you forget,
`verifica` warns you and gives you this same command.

## What is new today, in one sentence

In lesson 08 the router passed the order to **one** service. Today there are four services and the router distributes
**three copies** of the order, one per destination, all at the same time:

```
                                        ┌→ int-rest → API de pedidos
  tienda → POST /ordenes → [router] ────┼→ int-bd   → ERP
                                        └→ int-edi  → SFTP del socio (850)
```

int-bd is your lesson 05 behind the router, and int-edi is your lesson 07. You do not write them again: they come
ready-made.

**The picture:** a notice that the head office sends to three branches. It does not wait for the first one to answer
before sending it to the second: it sends it to all three at once and then gathers the three answers.

This pattern has a name in the EIPs: **Multicast**. One same order goes to several fixed destinations.

---

## 1. Set up your folder

```bash
cd lecciones/10-todos-los-canales
cp -R inicio mio
```

Now `mio/` has four folders, one per service: `orden-router`, `int-rest`, `int-bd` and `int-edi`.

## 2. Read the router

Open `mio/orden-router/orden-router.camel.yaml`. You already know everything at the top: the thread from lesson 09 and
the `ce-*` envelope. Further down there is something new: **three channel routes**, `canal-api`, `canal-erp` and
`canal-socio`. Each one calls its service and is built to leave **a single line** with what happened, whether its
service answers or not:

| If the service… | The line is… |
|---|---|
| answers, even with an error | `{"canal":"erp","codigo":200}`, with the HTTP code it gave |
| does not even answer (it is down) | `{"canal":"erp","error":"UnknownHostException"}`, with the name of the error |

For that they use `doTry` and `doCatch`: it **tries** the call and, if it fails, **catches** the error and turns it
into a line.

Why so much care? Because when we tested this lesson we saw that, without the channel routes, when a service was down
the router returned **the complete order** to the store, with the customer's name and email. An error response must
never carry personal data.

Today the main route only calls one of the three:

```yaml
- to:
    uri: direct:canal-api
```

`direct:` is a call to another route **in the same file**, with no network in between. The TODO above says what is
missing.

## 3. Predict

Answer on paper:

1. With the router as it is, if you send order 1003, which destinations receive it?
2. Once you send to all three: if the ERP is down, do the API and the partner receive the order or not?
3. In that case, which HTTP code do you think the store receives?

Check the first one:

```bash
./verifica
```

Four red marks come out. The API has the order; the ERP and the partner have nothing.

## 4. Write the multicast

Replace the `to: direct:canal-api` with a `multicast` that calls the three channels:

```yaml
- multicast:
    parallelProcessing: true
    aggregationStrategy: "#juntarRespuestas"
    steps:
      - to:
          uri: direct:canal-api
      # … los otros dos canales
```

| Parameter | What it does |
|---|---|
| `parallelProcessing: true` | All three at once. Without this, one after the other: the partner would wait for the ERP to finish |
| `aggregationStrategy: "#juntarRespuestas"` | How the three answers are gathered. `juntarRespuestas` is a bean that is already declared at the top, and it puts them in a list |

After the multicast, the body is that list of three lines. Answer the store with a `setBody`:

```
{"orden":"${variable.orden}","canales":[${join(",")}]}
```

`${join(",")}` joins the three lines of the list, separating them with commas. Use the canonical notation, as in the
other `setBody` steps of the file (`expression:` → `simple:` → `expression:`).

## 5. Bring it up and send it an order

Before bringing things up by hand, **write down the partner's key in the int-edi folder**: it is the service that
connects to the partner's SFTP, as in lesson 07.

```bash
(cd mio/int-edi && ../../../../herramientas/confiar-en-socio)
DONDE=mio docker compose -f compose.yaml up -d
DONDE=mio docker compose -f compose.yaml logs -f
```

Wait for the four `Started` lines. In another terminal, from the root of the course:

```bash
curl -s -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' -d @datos/ordenes/orden-1001.json
```

The store receives what each channel said:

```json
{"orden":"1001","canales":[{"canal":"api","codigo":201},{"canal":"erp","codigo":200},{"canal":"socio","codigo":200}]}
```

The channels work at once, but the router **waits for all three to finish** before answering, and the list comes out
in the order in which they are written in the YAML (api, erp, socio), not in the order in which they finished. Notice
the logs: the router's three `contestó` (answered) lines come out at almost the same time, and their order can indeed
change from one run to the next.

If the partner's channel says `500`, check the int-edi log. If you see `reject HostKey`, you forgot to write down the
key.

## 6. Shut down a channel

Now question 2. With everything up, shut down **only** int-bd and send another order:

```bash
DONDE=mio docker compose -f compose.yaml stop int-bd
curl -s -w ' %{http_code}\n' -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' \
  -d @datos/ordenes/orden-1003.json
```

```
{"orden":"1003","canales":[{"canal":"api","codigo":…},{"canal":"erp","error":"UnknownHostException"},{"canal":"socio","codigo":200}]} 200
```

- The API and the partner **did** receive the order. A channel that is down does not stop the other two.
- The ERP's line says `UnknownHostException`: the router looked for `int-bd` by its name and nobody answered.
- The store received a **200**, even though one destination does not have the order.

If you repeat this step with the same order, the partner answers `500`: it already has that 850 and does not
overwrite it, as in lesson 07.

Go back to your prediction 3. And now the question the lesson leaves you with: **what do we do about the ERP, which
does not have the order?** Do we send it again? When? And if the API already has it: do we send it there again too?
Write down your answer: you will compare it with what you build in lessons 19 and 22.

Shut everything down:

```bash
DONDE=mio docker compose -f compose.yaml down
```

## 7. The thread at the three destinations

The thread from lesson 09 travels in the envelope to the three channels. But each destination stores it differently:

| Destination | Where the thread ends up |
|---|---|
| API | In its log: `correlacion=<id>` |
| ERP | In the new column `pedido_cabecera.correlacion` |
| Partner | **Nowhere in the file.** int-edi writes it down in **its own** log, and the 850 goes out the same as in lesson 07 |

Why do we not send it to the partner? Because the partner is only sent the fields that the X12 format agrees on with
them. The thread is ours, it belongs to our integration; putting it in a field the partner did not ask for is not
done. With the partner, the thread already exists: it is the **control numbers** of the 850 that you saw in lesson 06.
When the partner answers with its acknowledgment of receipt, the 997, its `AK1` cites the `GS06` (the group's) and its
`AK2` the `ST02` (the transaction's). The `ISA13`, the one for the whole envelope, is cited by another acknowledgment,
the `TA1`. You use that in lesson 21.

**Not every destination can store your ID.** That is why, later on, a state database will tie the thread to the
control number.

## 8. Check

```bash
./verifica
```

`verifica` restarts the API, empties the ERP and the partner's mailbox, writes down the partner's key in
`mio/int-edi/`, brings up the four services and sends order 1003 with a thread of its own. It is done when you see the
six green checks:

```
✅ Arrancaron el router y los tres canales
✅ La tienda mandó la 1003 y el router contestó lo que dijo cada canal: api=201 erp=200 socio=200
✅ API: tiene el pedido 1003, con el hilo tienda-…
✅ ERP: tiene el pedido 1003 REGISTRADO, con el hilo tienda-…
✅ Socio: tiene el 850 de la 1003 en su SFTP
✅ int-edi anotó el hilo (al socio no se le manda: con él, el hilo es el número de control del X12)
```

Three of the six are confirmed by the destination itself: the API's record, the ERP's table and the partner's mailbox.

**Not working?** `./verifica solucion` tests ours, and
`diff mio/orden-router/orden-router.camel.yaml solucion/orden-router/orden-router.camel.yaml` tells you where they
differ.

| What you see | What to do |
|---|---|
| `Tu ERP no tiene la columna correlacion` | Recreate the ERP with the command at the beginning |
| Red only on the ERP, or only on the partner | That channel is not in your `multicast` |
| `El router no contestó que los tres canales la recibieron` and only one line appears | The `setBody` with `${join(",")}` is missing, or the multicast is still a single `to` |
| `El puerto 8080 está ocupado` | The services were left up: `DONDE=mio docker compose -f compose.yaml down` |

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [The correlation thread travels with the order, but does not leave the house](../decisiones/12-el-hilo-de-correlacion.md)
- [The order goes out to all the channels, and the router reports what happened](../decisiones/13-multicast-que-no-inventa.md)

## Review

1. What does a multicast do, and what changes with `parallelProcessing: true`?
2. What is the `juntarRespuestas` bean for, and what does `${join(",")}` do?
3. Why does each channel leave a line even if its service is down? What would have leaked otherwise?
4. With the ERP down, which destinations received the order and which code did the store receive? Why is that code a
   problem?
5. Why is the thread not sent to the partner, and which number plays that role with them?

**Next:** in lesson 11 not every order goes to every destination: the router decides which ones, according to what
the order carries.
