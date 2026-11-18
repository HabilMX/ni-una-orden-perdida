# Lesson 11 — Not everything goes to everyone

> **What you will achieve:** the router decides, order by order, whether it goes to the partner or not, according to
> **which store** it comes from. The API and the ERP keep receiving all of them; the partner, only its own.

**Before you start you need:** lesson 10 finished and the environment from lesson 00 turned on (with the ERP recreated
in lesson 10).

## What is new today, in one sentence

In lesson 10 every order went to the three destinations. But the trading partner does not operate all the stores: only
the **franchises**, T-501 and T-502. If you send it the order of a company-owned store, it gets an order that is not
its own and that it might fulfill. Today the partner's channel **looks at the order before sending it**.

**The picture:** the mail carrier who delivers in a neighborhood. They do not leave every letter at every house: they
read the address on each one and decide.

This pattern is called **Content-Based Router**: the destination depends on the content of the message.

| | Multicast (lesson 10) | Content-Based Router (today) |
|---|---|---|
| Who does it go to? | To all the destinations, always | Only to those that meet a condition |
| Who decides? | Nobody: the list is fixed | A rule that reads the order |

Today you use the two together: the multicast keeps distributing to the three channels, and **inside** the partner's
channel a condition decides whether the order goes on or not.

---

## 1. Set up your folder

```bash
cd lecciones/11-no-todo-va-a-todos
cp -R inicio mio
```

## 2. Predict

Open the batch of 10 orders, the folder `datos/ordenes/lote-10/`, at the root of the course. It has ten orders and a `LEEME.md`. Without running
anything, open the orders, look at their `"tienda"` field and answer on paper:

1. How many of the ten come from a franchise (T-501 or T-502)?
2. With the router as it is today, how many files do you expect in the partner's mailbox? And when you finish?
3. How many orders should the API and the ERP have?

Check today's:

```bash
./verifica
```

Two red marks come out. The partner has **all ten** 850s, and the API and the ERP also have ten. Today the partner's
channel sends everything.

## 3. Read the partner's channel

Open `mio/orden-router/orden-router.camel.yaml` and look for the route `canal-socio`. It now has two parts:

| Route | What it does |
|---|---|
| `canal-socio` | **Decides** whether the order goes to the partner. It has the TODO |
| `al-socio` | **Sends** the order to int-edi and leaves the line with what it answered, like the channel in lesson 10 |

Separating them leaves the decision in a single place: if tomorrow who counts as a partner changes, you touch one line
and not the call.

## 4. Write the condition

In the TODO, replace the `to: direct:al-socio` with a `choice`. You already used one in lesson 09, in the router, to
decide where the thread came from:

```yaml
- choice:
    when:
      - expression:
          simple:
            expression: "…tu condición…"
        steps:
          - to:
              uri: direct:al-socio
    otherwise:
      steps:
        # … el canal contesta que no aplica
```

**The condition.** The order's store is already saved in `${variable.tienda}`. In Camel's `simple` language, `in` asks
whether a value is in a comma-separated list:

```
${variable.tienda} in 'T-501,T-502'
```

**The otherwise.** The orders that do not come from a franchise do not go to the partner, but the channel **has to say
so**: a `setBody` with `{"canal":"socio","resultado":"no aplica"}`. If the channel stayed silent, the store would not
know whether the router decided not to send it or forgot. `no aplica` ("does not apply") and "it failed" are not the
same thing, and the response must tell them apart.

## 5. Compare with your prediction

```bash
./verifica
```

Now the partner has four 850s, those of orders 2002, 2005, 2007 and 2010, and the store received `no aplica` for the
other six. Did you get the 4 right? The API and the ERP still have all ten: the condition lives only in the partner's
channel.

If you get `Socio: … y tiene «850-2002.edi 850-2007.edi»`, your condition leaves out T-502. Check the list.

## 6. Check

It is done when you see the five green checks:

```
✅ Arrancaron el router y los tres canales
✅ API: tiene las 10 órdenes del lote
✅ ERP: tiene las 10 órdenes REGISTRADO
✅ Socio: solo llegaron los 850 de franquicia (850-2002.edi 850-2005.edi 850-2007.edi 850-2010.edi)
✅ El router le dijo a la tienda «no aplica» en las 6 que no son de franquicia
```

The checks for the API, the ERP and the partner count **at the destination**: the API's record, the ERP's rows and the
files in the mailbox.

**Not working?** `./verifica solucion` tests ours, and
`diff mio/orden-router/orden-router.camel.yaml solucion/orden-router/orden-router.camel.yaml` tells you where they
differ.

| What you see | What to do |
|---|---|
| The partner has all ten | The condition is not there, or the `to` ended up outside the `when` |
| The partner has two | The list in the condition has a single store |
| `Esperaba «no aplica» en 6 respuestas y lo vi en 0` | The `setBody` of the `otherwise` is missing |
| `Tu ERP no tiene la columna correlacion` | Recreate the ERP as lesson 10 says |

---

## Exercise: the rule outside the route

Today the list of franchises is **written inside the route**. If the partner opens another franchise, someone has to
edit the YAML, review it and deploy it again in order to change a piece of business data. Move it to
**configuration**: a value that is changed without touching the route.

**1.** In your condition, replace the list with an environment variable that has a default value:

```
${variable.tienda} in '{{env:FRANQUICIAS:T-501,T-502}}'
```

`{{env:FRANQUICIAS:…}}` means: "use the environment variable `FRANQUICIAS`; if it does not exist, use what comes after
the colon". Run `./verifica`: it must still give five out of five, because it uses the default value.

**2.** Now change the rule **without touching the route**. Create `mio/franquicias.yaml` with this:

```yaml
services:
  orden-router:
    environment:
      FRANQUICIAS: "T-501"
```

It is a file that **is added on top of** the lesson's compose: it adds an environment variable to the router and
changes nothing else.

**3.** Empty the partner's mailbox, bring up the services with the two files, send the batch and look at the mailbox:

```bash
docker compose -f ../../infra/compose.yaml exec socio-sftp sh -c 'rm -f /home/socio/entrada/*'
(cd mio/int-edi && ../../../../herramientas/confiar-en-socio)
DONDE=mio docker compose -f compose.yaml -f mio/franquicias.yaml up -d
for f in ../../datos/ordenes/lote-10/orden-*.json; do
  curl -s -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' -d @$f; echo
done
docker compose -f ../../infra/compose.yaml exec socio-sftp ls /home/socio/entrada
DONDE=mio docker compose -f compose.yaml down
```

Before running it, predict: how many 850s do you expect in the mailbox, and how many responses with `no aplica`?

In the responses, look only at the line of the `socio` channel. The API and the ERP already had those orders from your
last `verifica`, so their lines say something else; for the partner, the mailbox started empty. When you finish, run
`./verifica` to leave everything as it was.

**4.** Answer: what changed and what did not? Who should be able to change `FRANQUICIAS`: the person who programs the
route or the person who runs the business?

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [Who receives what is decided by the content, in a single place](../decisiones/14-quien-recibe-se-decide-en-un-lugar.md)

## Review

1. What is the difference between a Multicast and a Content-Based Router? Give an example of when you would use each
   one.
2. What does `in` do in the condition `${variable.tienda} in 'T-501,T-502'`?
3. Why does the channel answer `no aplica` instead of staying silent?
4. Why does the decision live in `canal-socio` and the call in `al-socio`?
5. What do you gain by moving the list of franchises to an environment variable?

**Next:** in lesson 12 a service stops being a single one: you run several replicas of it so that it can handle more
orders.
