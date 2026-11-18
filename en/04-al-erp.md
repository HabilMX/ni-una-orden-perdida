# Lesson 04 — The order reaches the ERP

> **What you will achieve:** record the order in the company's ERP, which does not receive JSON but rows in tables:
> a header, one detail row for each line item, and at the end a procedure that checks that everything adds up.

**Before you start you need:** lesson 03 finished and the environment from lesson 00 turned on:

```bash
docker compose -f infra/compose.yaml up -d --wait     # desde la carpeta raíz de la práctica
```

## What is new today, in one sentence

Until now the destination was an API that receives a complete JSON in one go. The ERP is different: it stores
information in **tables**, like spreadsheets, and each order takes up **several rows in two tables**. Today your route
splits the order into pieces and writes it row by row.

**Three words you are going to use:**

| Word | What it is | A picture |
|---|---|---|
| **ERP** | The central system where the company records what it sells, buys and invoices | The company's ledger |
| **SQL** | The language for talking to a database: `INSERT` adds a row, `SELECT` queries them | The instructions you dictate to whoever keeps the ledger |
| **Stored procedure** | A program that lives **inside** the database and is called with `CALL` | The accountant who reviews the page before signing it |

---

## 1. Set up your folder

```bash
cd lecciones/04-al-erp
cp -R inicio mio
```

## 2. Get to know the ERP's tables

The ERP has two tables. This is how order 1001 would look once recorded:

**`pedido_cabecera`** — one row per order

| pedido | tienda | fecha | total | estatus |
|---|---|---|---|---|
| 1001 | T-014 | 2026-11-12 10:15:00 | 428.00 | REGISTRADO |

**`pedido_detalle`** — one row for each line item of the order

| pedido | posicion | sku | cantidad | precio |
|---|---|---|---|---|
| 1001 | 1 | SKU-0001 | 2 | 89.50 |
| 1001 | 2 | SKU-0042 | 1 | 249.00 |

The `pedido` column of the detail is what **ties** each row to its header. That is why the header is written
first: the ERP does not accept a detail row for an order that does not exist yet.

And the `estatus` starts at `RECIBIDO`. It only changes to `REGISTRADO` when the `registrar_pedido` procedure checks
that the sum of the detail equals the total of the header. If you want to see how it is built, it is in
`infra/mariadb/01-erp.sql`; you do not need to change it.

## 3. Read the mapping and the route

**The mapping comes ready-made:** `mio/mappings/erp/pedido/v1/mapeo.jsonata`. It turns the order into a JSON with two
parts, `cabecera` and `detalle`, which have exactly the names of the columns. Test it as in lesson 03:

```bash
../../herramientas/probar-mapeo mio/mappings/erp/pedido/v1
```

Notice the date. The store sends `2026-11-12T10:15:00-06:00` and the mapping leaves it as `2026-11-12 10:15:00`. It
removes the time zone (`-06:00`) because the ERP's column is of type `DATETIME`, which stores date and time **without a zone**.
The agreement with the ERP is that this time is the store's; the data type does not know that, whoever reads it does.

**The route:** open `mio/orden-al-erp.camel.yaml`. Above the route there is a **bean** called `erp`: it is the
connection to the database, with its address, user and password. The `sql:` steps use it with `dataSource: "#erp"`.

The route already does this:

```
POST /ordenes  →  traducir  →  guardar cabecera y detalle en variables  →  INSERT de la cabecera  →  (te toca)
```

Look at the `INSERT` for the header:

```yaml
uri: sql:INSERT INTO pedido_cabecera (pedido, tienda, fecha, total) VALUES (:#pedido, :#tienda, :#fecha, :#total)
```

Each `:#nombre` is a slot that Camel fills with the field of that name **carried by the message body**. That is why,
just before, the route sets the header as the body with `setBody`.

One more thing: the MariaDB driver, the piece that teaches Camel to talk to that database, is not included.
The `dependencias.txt` file asks for it, and `herramientas/camel` downloads it at startup.

## 4. Predict

The route in `inicio/` only writes the header. Before you complete it, answer on paper:

1. If you send order 1001 with the route as it is, which rows are left in each table, and with which status?
2. If you added the `CALL registrar_pedido` **without** writing the detail, what would the procedure answer?

You can check the first one right now. From `mio/`, start the route:

```bash
cd mio
../../../herramientas/camel run orden-al-erp.camel.yaml
```

In another terminal, from the same `mio/` folder, send the order and ask the ERP:

```bash
curl -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' -d @../../../datos/ordenes/orden-1001.json

docker compose -f ../../../infra/compose.yaml exec erp \
  mariadb -uintegracion -ppractica erp -e "SELECT * FROM pedido_cabecera; SELECT * FROM pedido_detalle;"
```

Compare the two answers. **Your route** answered the store `{"pedido":"1001","erp":"REGISTRADO"}`. **The ERP**
says something else: the header is in `RECIBIDO` and the detail is empty. The route is not lying on purpose: its last
step writes that response without asking the ERP anything. That is why in this course what counts is what the
destination stored, never what the route says.

An order with a header and no detail is half an order. For the ERP it exists, but it cannot be fulfilled or invoiced.

## 5. Write the detail and the closing step

Stop the route with `Ctrl+C`. In `mio/orden-al-erp.camel.yaml` there are two comments that say **TE TOCA** ("your turn").

**The detail.** The list of rows is in `${variable.detalle}`, and you need one `INSERT` for each. That is what the
**`split`** step is for: it takes a list, splits it into its elements and runs its steps once for each element,
with that element as the body.

```yaml
- split:
    expression:
      simple:
        expression: "${variable.detalle}"
    steps:
      - to:
          uri: sql:INSERT INTO pedido_detalle (…) VALUES (…)
          parameters:
            dataSource: "#erp"
```

Fill in the columns and the slots yourself. Use the header's `INSERT` as a model; the detail's columns are
in the table in step 2.

**The closing step.** After the `split`, call the procedure:

```yaml
uri: sql:CALL registrar_pedido(:#pedido)
```

Watch the body: after the `split`, what does it carry? The `:#pedido` slot needs a body that has the `pedido`
field. Which step of the route already did that before the header's `INSERT`?

This pattern has a name in the EIPs: **Splitter**. An order with several line items is split into smaller messages,
one per line item, because that is how the destination receives them.

## 6. Compare with your prediction

Run the route again and send order 1001. First, empty the ERP to start clean:

```bash
docker compose -f ../../../infra/compose.yaml exec erp \
  mariadb -uintegracion -ppractica erp -e "DELETE FROM pedido_detalle; DELETE FROM pedido_cabecera;"
```

Query the two tables as in step 4. Now the header should say `REGISTRADO` and the detail should have two rows.

And question 2? If you call the procedure with no detail, it answers `El total no cuadra con el detalle` (the total
does not match the detail): the sum of zero rows is 0, and the total is 428. **The ERP does not trust what you send
it: it checks it.** Just like the API in lesson 03, a destination that validates is what makes an error visible.

## 7. Check

Stop the route with `Ctrl+C` and, from the lesson folder:

```bash
cd ..
./verifica
```

⚠️ `verifica` **empties the ERP's tables** before testing: it is a practice ERP and that way it starts from scratch every time.

It is done when you see the six green checks:

```
✅ El mapeo al ERP pasa sus casos dorados
✅ La ruta arrancó
✅ El ERP tiene las 2 cabeceras
✅ El pedido 1001 tiene sus 2 renglones de detalle
✅ El pedido 1003 tiene sus 3 renglones de detalle
✅ El procedimiento marcó los 2 pedidos REGISTRADO
```

`verifica` sends two orders, 1001 and the one with the cents (1003), and asks the ERP what it stored. As always, the
destination confirms it, not your route.

**Not working?** `./verifica solucion` tests ours, and
`diff mio/orden-al-erp.camel.yaml solucion/orden-al-erp.camel.yaml` tells you where they differ.

| What you see | What to do |
|---|---|
| `Al pedido 1001 le faltan renglones de detalle` | The `split` does not run or a slot is missing: check that it goes through `${variable.detalle}` |
| `Hay pedidos sin REGISTRADO` | The `CALL` is missing, or the body does not carry `pedido`: review step 5 |
| `No pude hablar con el ERP: ¿corriste la 00?` | Turn on the environment: `docker compose -f infra/compose.yaml up -d --wait` |
| `El puerto 8080 está ocupado` | A route was left running: stop it with `Ctrl+C` |

---

## Why it was done this way

Today you wrote the order in three separate steps. The card that explains why that is not enough, and what was
decided, belongs to the next lesson: [In the ERP, all or nothing](../decisiones/06-todo-o-nada-en-el-erp.md).

## Review

1. Why does an order take up rows in two tables, and which column ties them together?
2. Why is the header written before the detail?
3. What does a `split` do, and what does the body carry inside each pass?
4. Where does Camel get the value of `:#sku` from?
5. What does `registrar_pedido` check before marking an order `REGISTRADO`?

**Next:** what happens if an order line in the middle is wrong and the ERP rejects it? The header is already written and the
detail is half done. In lesson 05 you will make the ERP store **all or nothing**.
