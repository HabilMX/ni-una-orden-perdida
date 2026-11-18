# Lesson 29 — Do not lose when consuming

> **What you will achieve:** having an event that an integration read but did not finish (it delivered the order, but
> could not leave its outcome) **be read again**, instead of being considered read. And having a second read not
> create the order twice in the API.

**Before you start you need:** lessons 18 and 28 finished, the environment from lesson 00 turned on and Kafka up
(`docker compose -f infra/compose.yaml --profile kafka up -d --wait`).

## What is new today, in one sentence

In lesson 28 each group keeps its count of how far it has read: the **offset**. Until today Kafka commits that count
by itself (`autoCommitEnable`, which comes turned on), with what the consumer has already read, whether it finished or
not. And when an event fails halfway, Camel notes it in the log and goes on with the next one: the count moves
**past** the one that failed. The API already has the order, but the router never finds out how it went, and nobody
reads that event again. Today the offset is committed **by hand**, and only when the work is finished.

**The picture:** a bookmark. If you move it forward every time you turn the page, even if you fell asleep halfway
through the chapter, tomorrow you start at the next chapter and what you did not read you never read. If you move it
only when you have **finished** the chapter, the worst that happens is that you reread a few pages.

```
leer el evento → entregarlo al canal → publicar el desenlace → confirmar el offset
                                                                ↑ si algo falla antes de aquí, se vuelve a leer
```

---

## 1. Set up your folder

```bash
cd lecciones/29-no-perder-al-consumir
cp -R inicio mio
```

Your work goes in the three bridges: `mio/int-rest/kafka.camel.yaml`, `mio/int-bd/kafka.camel.yaml` and
`mio/int-edi/kafka.camel.yaml`. All three are changed the same way.

First, open `mio/orden-router/desenlaces.camel.yaml`: the router **already** reads the `desenlaces` topic this way.
It serves as your model.

## 2. Predict

Answer on paper:

1. The API received order 2001 and answered `201` to int-rest, but Kafka did not accept the outcome that int-rest
   tried to publish. With automatic offset commit (auto-commit), does the router find out that order 2001 arrived? Does anyone read that
   event again?
2. If you commit the offset only at the end, and int-rest is killed after delivering but before committing, what
   happens when it comes back? Is order 2001 created twice in the API? (Hint: lesson 18.)
3. If an event can never be finished, what happens to the ones that come behind it in its partition?

Look at how it is today:

```bash
./verifica inicio
```

`verifica` sets a trap for Kafka: it makes the `desenlaces` topic accept nothing. It sends three orders, sees that
the API and the ERP receive them and that the router does not find out about any of them. Then it removes the trap.
With automatic offset commit (auto-commit), the router is left without knowing about **any** of the 6 operations: the events were already
committed.

## 3. Commit when you have finished

In each bridge there are three TODOs.

### TODO 1: the three consumer options

In the `from: kafka:ordenes`, next to `groupId`:

```yaml
autoCommitEnable: false      # Kafka ya no confirma solo
allowManualCommit: true      # el puente confirma cada evento cuando terminó con él
breakOnFirstError: true      # si un evento falla, se vuelve a leer ése, no se sigue con el siguiente
```

The three go together:

| If this is missing… | What happens |
|---|---|
| `autoCommitEnable: false` | Kafka commits what was read by itself, and your commit by hand changes nothing |
| `allowManualCommit: true` | Camel gives you nothing to commit with (TODO 2 finds nothing) |
| `breakOnFirstError: true` | Camel goes on with the next event and, when it commits that one, it also commits the one that failed |

That last row is the one that gets forgotten. Committing an event tells Kafka "I have read up to here" in its
partition: committing the next one covers the one that failed.

### TODO 2: storing what to commit with

With `allowManualCommit`, Camel leaves in the `CamelKafkaManualCommit` header the object that commits **this** event.
Store it in a variable, as the first step, because the delivery to the channel clears the headers:

```yaml
- setVariable:
    name: confirmar
    expression:
      simple:
        expression: "${header.CamelKafkaManualCommit}"
```

### TODO 3: committing at the end

At the end of the steps, after the `filter` (that is, when the outcome is already in Kafka, or the event was not for
this channel), call `commit()` on that variable. Camel has a step, `script`, that evaluates an expression only for
what it does:

```yaml
- script:
    expression:
      simple:
        expression: "${variable.confirmar.commit()}"
```

Not before this: what is committed is not read again. Repeat the three TODOs in the other two bridges.

## 4. Break one on purpose

With your three bridges finished, in the int-rest one move the `script` from TODO 3 **above** the `filter`, so that it
commits before delivering. Run `./verifica`.

First, predict: the three options are still set. Is what failed read again?

No. The check for the 6 operations comes out red: the router does not find out about the API's operations. The event
was already committed when its outcome failed, so `breakOnFirstError` goes back to the committed offset, which is
already **after** it. **The options say how the commit is done; the place of the `script` says when.** And the when
is what matters. Put it back at the end.

## 5. Check

```bash
./verifica
```

`verifica` runs three experiments. In the first it sets the trap on `desenlaces`, sends orders 2001, 2003 and 2004, and
removes it. In the second it stops int-rest, sends orders 2006, 2008 and 2009, makes the API slow (2.5 s per order),
turns int-rest on and **kills** it in the middle of a delivery, to turn it on again. In the third it sets the trap
again and sends order 4002, which is a franchise order and goes to the partner. It is done when you see the nine
green checks:

```
✅ Arrancaron el router, los tres canales con su puente y el sondeo
✅ La 4001 llegó a sus tres canales
✅ Control: la API tiene (…) y el ERP (…) de las 3, y el router no se enteró de ninguna (sus desenlaces no se pudieron escribir)
✅ Kafka volvió a aceptar y el router se enteró de las 6 operaciones: lo que falló se volvió a leer
✅ En la API se creó cada una UNA vez (3 creaciones para 3 órdenes)
✅ Tras matar int-rest, la API tiene las 3 y las 3 operaciones quedaron aceptadas
✅ Cada una se creó UNA vez en la API (…)
✅ Control: el 850 de la 4002 ya está en el socio y el router no se enteró (su desenlace no se pudo escribir)
✅ int-edi releyó la 4002, reconoció su 850 como el mismo y quedó aceptada: un solo 850 en el socio
```

The third one is a **control**: before removing the trap, `verifica` checks that something really failed. If the
router already knew about the orders, the fourth check would prove nothing. The eighth is another control, the one for order 4002.

Notice the third one: the API may have 1, 2 or 3 of the orders. With your version, an event that cannot be finished
**stops its partition**: Camel retries it, and what comes behind it in that partition waits. That is the cost of not
losing.

The seventh check has two forms. If the int-rest cut left an event uncommitted, int-rest read it again and the API
recognized it by its idempotency key (lesson 18): it answered `200`, not `201`, and created nothing new. If the cut
fell right after committing, there was nothing to reread. Both are correct.

The last two are the partner's, and that destination has no idempotency key: the SFTP does not overwrite a file
that is already there (lesson 07), so the second delivery of the same 850 fails. The one that recognizes it is
int-edi, which already comes that way in your `mio/`: if the file exists, it reads it and compares it with the one it
was going to leave. Since the name and the content come from the order, control number included, a repeat is
**identical**: it answers that it was already there, and the operation is accepted. If it were different, it would be a
conflict, as in lesson 18: it answers `409` and overwrites nothing.

**Not working?** `./verifica solucion` tests ours, and `diff -r mio solucion` tells you where they differ.

| What you see | What to do |
|---|---|
| `Kafka no levantó` | Turn it on by hand with the command from "Before you start" |
| `No arrancaron todas las rutas` | Check the YAML of your bridges: the log that comes out below says which file and which line |
| `El router se enteró de … de 6 operaciones: … se perdieron` | One of the three options from TODO 1 is missing, or the `script` is not at the end |
| `Control: esperaba al menos 1 en la API y en el ERP y 0 enteradas en el router` | If the router did find out, the trap was not set: run `verifica` again. If the API has 0, check that the bridge is still delivering |
| `En la API hubo … creaciones para 3 órdenes` | The API created one of them twice: did you change the key that int-rest sends it? (lesson 18) |
| `La 4002 quedó «…» en el canal del socio` | int-edi does not recognize its own 850 when it rereads it: compare your `mio/int-edi/int-edi.camel.yaml` with the one in `solucion/` |
| `Tras matar int-rest, la API tiene … de 3` | When it came back, int-rest did not reread what it had not committed: check the TODO 1 options in its bridge |
| `El puerto 8080 está ocupado` | The services from another run were left up: `DONDE=mio docker compose -f compose.yaml down` |

---

## What this teaches beyond the course

**"Read" is not "finished".** Auto-commit says the first. What matters is the second, and only whoever did the
work knows it.

**What gets lost is not always what you expect.** In our runs, killing the consumer halfway did not lose events even
with auto-commit: when it came back, Kafka gave it what had not been committed yet. What did get lost was the
event whose outcome could not be written. That is why `verifica` provokes both things.

**Not losing forces you to tolerate repeats.** If an event can be read twice, whoever receives it has to recognize
it. The idempotency key from lesson 18 is what makes this lesson work. And when the destination cannot recognize a
repeat, like a folder of files, the channel recognizes it: it compares what it finds with what it was going to leave.

**Not losing has a cost.** An event that cannot be finished stops its partition. That is why the bridge leaves an
outcome even if the channel does not answer ("unknown"): the one that retries the delivery is the router, not the
bridge.

## Exercise: the bookmark

1. On paper, for each case say whether the event is lost, is repeated or comes out fine, with auto-commit and
   with yours: (a) the channel delivers and the outcome is published; (b) the channel delivers and the outcome cannot
   be published; (c) the bridge is killed after delivering and before publishing.
2. Open `mio/orden-router/desenlaces.camel.yaml`. Where does the router commit? What would happen if it committed
   before recording the outcome in the database?
3. Explain why the bridge does not retry the delivery itself when the channel does not answer. (Hint: the partition.)

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [The offset is committed by hand, after leaving the outcome, and what fails is read again](../decisiones/33-el-offset-se-confirma-despues-del-desenlace.md)

## Review

1. What is the offset, and what does automatic offset commit (auto-commit) do?
2. What is each of the three options from TODO 1 for?
3. Why does the `commit()` go at the end and not at the beginning?
4. What keeps an order that is read twice from being created twice in the API?
5. **The closing question:** Kafka did not accept the outcome of order 2001. Explain what happens to that event with
   automatic commit and with yours, and what your version costs the partition.

**Next:** in lesson 30 the gap is on the other side: the router stores the order and then publishes. What if Kafka
is down at exactly that moment?
