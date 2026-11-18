# Lesson 14 — One file, several orders

> **What you will achieve:** split the file the warehouse drops with several shipments, so that each order gets its
> own 856 and its EMBARCADO status, and so that one bad shipment does not stop the others.

**Before you start you need:** lesson 13 finished and the environment from lesson 00 turned on.

## What is new today, in one sentence

In lesson 13 the warehouse dropped one file per shipment. In real life it is almost never like that: the warehouse
gathers what shipped during the morning and drops **a single file, a batch**, with five shipments inside. Today you
split it into its parts, one per order. The pattern is called *Splitter*.

**The picture:** the box that arrives at the office with the mail for the whole floor. Nobody hands the entire box to
the first desk: it is opened, each envelope is taken out and carried to its addressee. And if an envelope carries a
name nobody knows, it is set aside and the others are handed out.

---

## 1. Set up your folder

```bash
cd lecciones/14-un-archivo-varias-ordenes
cp -R inicio mio
```

Open the good batch, `datos/embarques/lote-bodega-0001.json`. At the top it carries the batch's data (number,
warehouse, when it was generated) and then a list, `embarques`, with five elements. Each element is a shipment like
the one in lesson 13: order, carrier, tracking number and order lines.

The bad batch is `lote-bodega-0002.json`: five shipments, and the third one belongs to order **9999**, which the store
never created. The router is not going to accept it.

## 2. Predict

Before opening the route, answer on paper:

1. Batch 0001 carries five shipments. How many 856s do you expect on the partner's SFTP?
2. In batch 0002, the third shipment fails. What happens to the fourth and the fifth? And to the first and the second,
   which already went through?
3. When batch 0002 is finished, where should the file go: to `.camel/`, to `.error/` or to neither of the two?

Look at how it is today:

```bash
./verifica inicio
```

Today the poller sends the router **the complete batch as if it were one shipment**. The router looks for a single
order in the body, does not find it and rejects it. The checks for the API, the ERP and the partner come out red.

## 3. What you are going to write

Open `mio/sondeo-bodega/sondeo-bodega.camel.yaml`. The `from` already carries the four precautions of lesson 13; only
the `include` changed, which now takes the `lote-bodega-<algo>.json` files. Your work is in the three TODOs of the
steps:

| TODO | What you do | What for |
|---|---|---|
| 1 | Wrap the steps in a `split` with the JSONPath expression `$.embarques[*]` | One message for each shipment in the list |
| 2 | Write `stopOnException: false` in the `split` | So that one bad shipment does not stop the others |
| 3 | Move inside the `split` the steps that save the order and call the router, and add a `marshal` to JSON before the call | So that each part travels to the router by itself |

Inside the `split`, the steps go in their own `steps:`, with more indentation. This is what the skeleton looks like;
the inner steps are the ones that are already in the file:

```yaml
- split:
    jsonpath:
      expression: "$.embarques[*]"
    stopOnException: false
    steps:
      - setVariable: …   # la orden de esta parte
      - marshal:
          json: {}
      - …                # quitar encabezados, Content-Type, llamar al router, anotar
```

Two new things:

- **Each part arrives as data, not as text.** JSONPath hands you each shipment as a map (keys and values). That is why
  the order is no longer taken with `$.orden` but with `${body[orden]}`, and before sending it to the router you have
  to turn it into JSON text with `marshal`. Without the `marshal`, the router receives something that is not JSON.
- **`stopOnException: false` is already the default value.** You write it anyway: whoever reads the route sees that it
  was thought about, not that it was forgotten.

## 4. Break one on purpose

With your `split` finished, change `stopOnException` to `true` and run `./verifica`. First, predict: how many of the
four good shipments of batch 0002 arrive?

The check for batch 0002 comes out red: only two arrive, the 4001 and the 4002. The `split` goes in order; when the
third one (the 9999) fails it stops, and the 4004 and the 4005 are left without notice, even though they left the
warehouse. Because of an error in an order that does not even exist, two customers do not know where their package is.
Go back to `false`.

## 5. Check

```bash
./verifica
```

`verifica` creates orders 4001 to 4005, drops batch 0001 in the mailbox and checks; then it cleans the destinations
and drops batch 0002. It is done when you see the seven green checks:

```
✅ Arrancaron el router, los tres canales y el sondeo de la bodega
✅ Lote 0001: las 5 órdenes quedaron EMBARCADO en la API y en el ERP
✅ Socio: 5 archivos 856, uno por orden, y los 5 pasan el lector X12
✅ Buzón: el lote 0001 se apartó a .camel/
✅ Lote 0002: los otros 4 embarques pasaron (4001, 4002, 4004 y 4005 EMBARCADO en la API y el ERP)
✅ Sondeo: anotó en su bitácora que el embarque de la 9999 no pasó
✅ Buzón: el lote 0002 se apartó a .error/ para que alguien vea el embarque malo
```

Notice the last two: batch 0002 ended up in `.error/` **even though four of its five shipments went through**. The
`split` went on with the others, but at the end it remembers that there was an error, and the poller treats the file
as failed. The file in `.error/` says "review this one"; the log says which:

```bash
DONDE=mio docker compose -f compose.yaml logs sondeo-bodega | grep "no aceptó"
```

**Not working?** `./verifica solucion` tests ours, and
`diff mio/sondeo-bodega solucion/sondeo-bodega` tells you where they differ.

| What you see | What to do |
|---|---|
| `solo 0 de 5 órdenes quedaron EMBARCADO` | The steps that call the router ended up outside the `split`, or the `split` is missing. Check the indentation: they have to be inside its `steps:` |
| The log says the router did not accept any shipment | The `marshal` to JSON is missing: the router receives a map, not text |
| `el malo detuvo a los demás` | `stopOnException` was left at `true` |
| `Al ERP le falta la tabla embarque` | Recreate the ERP as lesson 13 says |

---

## What this teaches beyond the course

**Batches are the rule, not the exception.** Banks, warehouses and old systems hand over their work in large files,
once a day or every hour. Splitting the file and treating each part as if it had arrived by itself is the first thing
an integration with them does.

**A batch is not a transaction.** In lesson 05 you learned "all or nothing" inside the ERP. Here it is the other way
around: each shipment belongs to a different customer, and one of them failing is no reason not to notify the other
four. Which is better, all or nothing or one by one, depends on whether the parts depend on one another.

## Exercise: the batch with its error

With your version finished:

1. Open `buzon/.error/lote-bodega-0002.json`. If you were the person looking after the warehouse, how would you know
   which of the five shipments to review? Is the file enough, or do you need the log?
2. Run `./verifica` again without changing anything. Was any 856 or any EMBARCADO duplicated? Why not? (Hint:
   `verifica` cleans the destinations before each batch, and the API recognizes the key of each shipment.)
3. Imagine the warehouse fixes the 9999 and sends the complete batch 0002 again. What would happen to the four orders
   that were already EMBARCADO? Write down your answer: the subject of "when are two messages the same one?" is
   lesson 18.

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [A file from the warehouse is split into its shipments, and a bad one does not stop the others](../decisiones/17-partir-el-archivo.md)

## Review

1. What does a Splitter do, and why is it needed with a batch from the warehouse?
2. Why, inside the `split`, is the order read with `${body[orden]}` and not with `$.orden`?
3. What is the `marshal` for before calling the router?
4. What does `stopOnException: false` do? Why is it written if it is already the default value?
5. **The closing question:** batch 0002 ended up in `.error/` even though four shipments did go through. Is that a
   problem? What does that file tell you, and what does the log tell you?

**Next:** in lesson 15 the closing question of lesson 12 comes back: if the router has replicas, where does what belongs
to each order live? Today the router remembers nothing; tomorrow it stores every order, every channel and every
attempt in its own database.
