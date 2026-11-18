# Lesson 03 — Translating the order

> **What you will achieve:** write the translator yourself that turns the store's order into the API's order,
> check it with test cases before running the route, and discover why a computer does not add up cents the way you
> do.

**Before you start you need:** lesson 02 finished and the environment from lesson 00 turned on:

```bash
docker compose -f infra/compose.yaml up -d --wait     # desde la carpeta raíz de la práctica
```

## What is new today, in one sentence

In lesson 02 the translator came ready-made. Today you write it yourself, and the API now asks for more: in each line
item, its **amount** (quantity × price), and in the order, the **total**. If the total does not match the line items
**to the cent**, the API rejects it.

**The translator is called a mapping** and it is written in **JSONata**, a small language for transforming one JSON into another.
You are going to read it before you write it: almost all of it makes sense at first sight.

---

## 1. Set up your folder

```bash
cd lecciones/03-traducir-la-orden
cp -R inicio mio
```

This time **you stay in the lesson folder**, you do not go into `mio/`: the testing tool is run from here.

## 2. Read the mapping you already know

Open `mio/mappings/api/pedido/v1/mapeo.jsonata`. It is the one from lesson 02:

```
{
  "pedido": orden,
  "tienda": tienda,
  "fecha": creada,
  "partidas": [renglones.{
    "posicion": renglon,
    "sku": sku,
    "cantidad": cantidad,
    "precio": precio
  }]
}
```

It reads like this: **on the left, the name the API wants; on the right, where it comes from in the store's order.**
`"pedido": orden` means "the `pedido` field is worth whatever the `orden` field carries".

The odd line is `renglones.{ … }`. The dot means **"for each one"**: for each order line, build
a line item with these fields. The outer square brackets make sure a list always comes out, even if the order carries a single order line.

Why does the file live in a `v1` folder? Because today you are going to write `v2` **next to it**, without touching
v1. If something goes wrong, the route can go back to the previous version by changing a single number.

## 3. Read the golden test cases

Open the folder `mio/mappings/api/pedido/v2/cases/`. It has three cases, and each one is a pair of files:

| File | What it is |
|---|---|
| `entrada.json` | An order from the store |
| `esperado.json` | Exactly what your mapping has to produce with that order |

These are called **golden test cases**: the correct result, written before programming. They are this lesson's error
control: you do not have to ask anyone whether your mapping came out right, the cases tell you.

Open `cases/dos-renglones/esperado.json` and compare it with what v1 produces. Which fields are new?

## 4. Predict

Open `cases/centavos/entrada.json`. It is order 1003, from a stationery shop:

| Order line | Quantity | Price |
|---|---|---|
| Paper clip | 3 | 0.10 |
| Rubber band | 7 | 0.70 |
| Notebook | 1 | 19.99 |

Before you go on, write on paper:

1. How much is the amount for the paper clip (3 × 0.10)?
2. What is the order's total?

Keep your answers: you will need them in step 6.

## 5. Write v2

Open `mio/mappings/api/pedido/v2/mapeo.jsonata`. It only has some instructions and a pair of empty braces `{ }`.

1. **Copy the content of v1 there**, without the comment at the top.
2. **Add the amount** in each line item. In JSONata multiplication is `*`, and inside `renglones.{ … }` you are
   already standing on one order line, so `cantidad` and `precio` are the ones of that order line.
3. **Add the total** of the order, after `"partidas"`. JSONata has a function `$sum( … )` that adds up a
   list of numbers. Hint: `renglones.(cantidad * precio)` produces the list of amounts.

When you have it, test it against the golden test cases, from the lesson folder:

```bash
../../herramientas/probar-mapeo mio/mappings/api/pedido/v2
```

The tool runs your mapping with each `entrada.json` and compares the result with its `esperado.json`. At the end it says
`Casos: 3 · pasan … · fallan …`. If a case fails, it shows you what it got; open it next to its `esperado.json` and look for the difference.

The first time it takes longer, because Camel downloads what it needs. Wait for the line with the cases to appear.

## 6. Compare with your prediction

If you wrote the amount as `cantidad * precio`, most likely **the `centavos` case failed**. Look at
what it got:

```
"importe": 0.30000000000000004      ← el clip
"importe": 4.8999999999999995       ← la liga
"total": 25.189999999999998
```

You predicted 0.30, and you were right. The computer was not. **It does not store decimals the way you write them:** it
stores them in binary, and 0.10 in binary has no exact ending, just as 1/3 in decimal is 0.3333… without end. When you
multiply, that little leftover bit peeks out.

For a person, 0.30000000000000004 and 0.30 are the same thing. **For the API, they are not:** it compares the total
against the sum of the line items to the cent, and if they do not match it rejects the order with a **422**:
`El total 25.189999999999998 no cuadra con las partidas, que suman 25.19` (the total does not match the line items,
which add up to 25.19). In a real sales system, one cent
of difference is an invoice that does not add up.

The solution is to **round to two decimals** with `$round(numero, 2)`. Apply it to the amount and to the total, and
test again until you see:

```
Casos: 3 · pasan 3 · fallan 0
```

Do you see why golden test cases are written before programming? If you had compared "by eye", 0.30000000000000004
looks like 0.30 and the error would have gone all the way to the API.

## 7. Switch the route to v2

Your new mapping exists, but the route still uses the old one. Open `mio/orden-a-api.camel.yaml` and look for the line:

```yaml
uri: jsonata:mappings/api/pedido/v1/mapeo.jsonata
```

Change it so that it points to v2. It is the only change to the route.

If you want to see it run, do the same as in lesson 02: from `mio/`, start the route with
`../../../herramientas/camel run orden-a-api.camel.yaml` and in another terminal send order 1003
(`-d @../../../datos/ordenes/orden-1003.json`). Then ask the API: `curl localhost:8081/pedidos/1003`.

## 8. Check

Stop the route with `Ctrl+C` if you left it running and, from the lesson folder:

```bash
./verifica
```

It is done when you see the four green checks:

```
✅ El mapeo v2 pasa todos sus casos dorados
✅ La ruta usa la versión 2 del mapeo
✅ La API aceptó la orden 1003 (201)
✅ El pedido quedó con total 25.19
```

The first two review your work; the last two are confirmed by **the API**. If the mapping passed its cases but the
route were still on v1, the API would receive an order without a total.

**Not working?** `./verifica solucion` tests ours, and
`diff mio/mappings/api/pedido/v2/mapeo.jsonata solucion/mappings/api/pedido/v2/mapeo.jsonata` tells you where they
differ.

| What you see | What to do |
|---|---|
| `Casos: 3 · pasan 2 · fallan 1` in `centavos` | Rounding is missing: go back to step 6 |
| `No hay casos en …` | You mistyped the folder: it must end in `pedido/v2` |
| `La API no aceptó la orden 1003: llegó 422` | The total does not match to the cent: check the `$round` calls from step 6 |
| `El pedido quedó con total ninguno` | The route is still on v1, which does not send a total: go back to step 7 |
| `El puerto 8080 está ocupado` | A route was left running: stop it with `Ctrl+C` |

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [JSONata for translating between formats](../decisiones/03-jsonata-para-traducir.md)
- [No golden test cases, no mapping](../decisiones/04-casos-dorados.md)

## Review

1. In `"pedido": orden`, which side is the API and which side is the store?
2. What does the dot do in `renglones.{ … }`?
3. What is a golden test case, and why is it written before the mapping?
4. Why does 3 × 0.10 not give exactly 0.30 on a computer?
5. What is the point of v2 living next to v1 and not on top of it?

**Next:** in lesson 04 the order reaches a system that does not speak JSON: the ERP, which stores everything in tables.
