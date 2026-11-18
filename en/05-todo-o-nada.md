# Lesson 05 — All or nothing

> **What you will achieve:** make sure that when an order line is wrong, the ERP is not left with half an order:
> either everything is stored, or nothing is. You achieve it by adding **a single step** to the route from lesson 04.

**Before you start you need:** lesson 04 finished and the environment from lesson 00 turned on:

```bash
docker compose -f infra/compose.yaml up -d --wait     # desde la carpeta raíz de la práctica
```

## What is new today, in one sentence

In lesson 04 your route writes to the ERP in several steps: the header, then each order line and at the end the procedure.
If a step in the middle fails, the earlier ones **are already written**. Today you wrap all those steps in a
**transaction**: a package that the ERP commits whole or rolls back whole.

**The picture:** a bank transfer. An amount is subtracted from one account and added to the other. If the bank did the
subtraction and then the addition failed, the money would disappear. That is why the two operations go together:
either both happen, or neither does.

| Word | What it means |
|---|---|
| **Transaction** | A group of writes that the database treats as a single one |
| **Commit** | When it finishes well, the database stores the whole group at once |
| **Roll back** | If something fails, the database erases everything the group had written, as if it had never happened |

---

## 1. Set up your folder

```bash
cd lecciones/05-todo-o-nada
cp -R inicio mio
```

`inicio/` is the route from lesson 04 already finished, split and `CALL` included, plus three new pieces that come ready-made.

## 2. Meet the order that is going to fail

Open `datos/ordenes/orden-1004-renglon-invalido.json`, in the root folder of the course. It has two order lines:

| Order line | Product | Quantity | Price |
|---|---|---|---|
| 1 | White mug | 1 | 89.50 |
| 2 | Blue tablecloth | **0** | 249.00 |

The ERP does not accept quantity 0: the `pedido_detalle` table has a rule (`CHECK (cantidad > 0)`) that rejects that
row. It is like a real system, which does not let you record "zero tablecloths".

## 3. Predict

Your route writes the header first, then order line 1 and then order line 2. Answer on paper:

1. When order 1004 arrives, at which step does the route fail?
2. At the end, what is left in `pedido_cabecera` and what is left in `pedido_detalle` for order 1004?
3. With which status is the header left?

## 4. Check it

Run `verifica` with the route as it comes, without changing anything:

```bash
./verifica
```

**Two red marks** will come out:

```
❌ Quedó la cabecera de la 1004 sin su detalle completo: falta la transacción
❌ Quedaron renglones sueltos de la 1004
```

Ask the ERP what it kept:

```bash
docker compose -f ../../infra/compose.yaml exec erp \
  mariadb -uintegracion -ppractica erp -e "SELECT * FROM pedido_cabecera; SELECT * FROM pedido_detalle WHERE pedido='1004';"
```

The header of order 1004 is there, in `RECIBIDO`, with a total of 89.50, and it has **one** detail row: the mug. The
tablecloth never went in and the procedure never ran. It is an order that the ERP believes exists, that nobody is going
to fulfill and that someone will have to find and delete by hand.

Did you get it right? Notice that the store did find out about the error (the route answered it with a 500), but that
does not clean up what had already been written.

## 5. Read the new pieces

Open `mio/orden-al-erp.camel.yaml`. At the top, next to the `erp` connection you already know, there are two new beans:

| Bean | What it does | In the bank picture |
|---|---|---|
| `transacciones` (a `DataSourceTransactionManager`) | It opens, commits or rolls back the transaction on the `erp` connection | The teller who carries out the operation |
| `todoONada` (a `SpringTransactionPolicy`) | The rule you are going to use in the route: "if there is already an open transaction, join it; if not, open one" (`PROPAGATION_REQUIRED`) | The bank's policy: the two operations go together |

And in `mio/dependencias.txt` there are two more pieces that Camel downloads at startup: `camel-spring` and
`spring-jdbc`. They are the ones that know how to handle transactions. You do not write Java: everything is declared in
the YAML.

## 6. Add the step

Look in the route for the comment that says **TE TOCA** ("your turn"). A single step goes there:

```yaml
- transacted:
    ref: todoONada
```

`transacted` means: **from here to the end of the route, everything happens inside a transaction**, with the
`todoONada` rule. If any step below fails, everything that was written from this point on is rolled back.

Notice **where** it goes: before writing the header. What would happen if you put it after the header's
`INSERT`?

This pattern has a name in the EIPs: **Transactional Client**. The route does not only send messages: it asks the
destination to treat several writes as a single one.

## 7. Check

```bash
./verifica
```

⚠️ Just as in lesson 04, `verifica` **empties the ERP's tables** before testing.

It is done when you see the six green checks:

```
✅ El mapeo al ERP pasa sus casos dorados
✅ La ruta arrancó
✅ La orden 1004 (con un renglón de cantidad 0) fue rechazada (500)
✅ No quedó la cabecera de la 1004: se deshizo
✅ No quedó ningún renglón de la 1004
✅ La orden buena 1001 sí quedó completa y REGISTRADO
```

The last one matters as much as the others: the transaction must not get in the way of the good orders.

Query the ERP again as in step 4. Now nothing is left of order 1004: no header, no mug.

**Not working?** `./verifica solucion` tests ours, and
`diff mio/orden-al-erp.camel.yaml solucion/orden-al-erp.camel.yaml` tells you where they differ.

| What you see | What to do |
|---|---|
| The same two red marks from step 4 | The `transacted` step is not there, or it is after the header |
| `La ruta no arrancó` | Check the indentation of the step: `transacted` goes at the same level as the other steps |
| `El puerto 8080 está ocupado` | A route was left running: stop it with `Ctrl+C` |

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [In the ERP, all or nothing](../decisiones/06-todo-o-nada-en-el-erp.md)

## Review

1. What is a transaction, and what do commit and roll back mean?
2. Without a transaction, what was left in the ERP with order 1004, and why is it a problem?
3. Why does the `transacted` step go before the header's `INSERT`?
4. What is each of the two new beans for?
5. The store received a 500. What should it do with that order? *(There is no answer yet: in the third part of the
   course, "Make it not fail", you will decide what gets retried and what gets set aside.)*

**Next:** in lesson 06 the order travels to the trading partner, which does not receive JSON or tables: it receives an
EDI file, a text format that companies have been using for decades to talk to each other.
