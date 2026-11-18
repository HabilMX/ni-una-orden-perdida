# Lesson 06 — Building the EDI

> **What you will achieve:** turn the order into the document the trading partner expects, an **X12 850**, and
> check with a reader in Spanish that it is well built. Today you do not need the environment: only Docker.

**Before you start you need:** lesson 03 finished. This lesson uses the same idea of mapping and golden test cases.

## What is new today, in one sentence

The API speaks JSON and the ERP speaks tables. The trading partner speaks **EDI**: a text format that companies have
been using since before the internet existed to send each other purchase orders, invoices and ship notices. The one
this partner uses is called **X12**, and the purchase order is document **850**.

This is what an 850 order looks like:

```
ISA*00*          *00*          *ZZ*TIENDANORTE    *ZZ*SOCIO01        *261112*1015*U*00401*000001001*0*P*>~
GS*PO*TIENDANORTE*SOCIO01*20261112*1015*1001*X*004010~
ST*850*0001~
BEG*00*SA*1001**20261112~
N1*ST*Tienda T-014*92*T-014~
PO1*1*2*EA*89.50**VN*SKU-0001~
PID*F****Taza blanca~
PO1*2*1*EA*249.00**VN*SKU-0042~
PID*F****Mantel azul~
CTT*2~
SE*9*0001~
GE*1*1001~
IEA*1*000001001~
```

It has no field names like JSON does: **each piece of data counts by its position**. It looks unreadable, but today
you are going to learn to read it.

---

## 1. Set up your folder

```bash
cd lecciones/06-armar-el-edi
cp -R inicio mio
```

## 2. Predict

Look at the 850 above. Without looking anything up, answer on paper:

1. There is a `*` and a `~` on every line. What do you think each one separates?
2. On which line do you think the order number (1001) goes? And the order lines?
3. The words `TIENDANORTE` and `SOCIO01` appear twice. On the first line they carry extra spaces at the end, and
   on the second they do not. Why do you think that is?

## 3. Read it with the reader

The course comes with a reader that explains an X12 in Spanish. From the lesson folder:

```bash
../../herramientas/leer-x12 ../../datos/edi/850-ejemplo.edi
```

Compare with your prediction:

- **`~` closes a segment.** A segment is like a line of the document, and its name is its first letters
  (`ISA`, `BEG`, `PO1`…).
- **`*` separates the elements** of a segment. The reader numbers them: in `BEG*00*SA*1001**20261112`, element 03
  is `1001`, the order number. Two `*` in a row mean that this element is empty.
- **The order lines** are the pairs `PO1` (quantity, unit, price and product) and `PID` (description).

The document goes **inside two envelopes**, like a letter inside a parcel:

| Opens | Closes | What it is |
|---|---|---|
| `ISA` | `IEA` | The outer envelope: who sends, to whom and a control number |
| `GS` | `GE` | A group of documents of the same type (here, purchase orders) |
| `ST` | `SE` | The document: the 850 order |

Each envelope is closed by repeating its control number, and `SE` also **counts** how many segments the
document has, from `ST` to `SE`. That way the partner knows whether something got lost along the way.

And the spaces from question 3? The `ISA` is **fixed width**: it always measures 106 characters, and each piece of data
takes up an exact place. `TIENDANORTE` has 11 letters and its place is 15 wide, so it is padded with 4 spaces.

At the end, the reader runs six checks (the width of the ISA, the control numbers, the SE count…). If any one
fails, it comes out red.

## 4. The mapping calculates, the template arranges

The route `mio/orden-a-edi.camel.yaml` builds the 850 in two steps, each one with a single job:

| Piece | Its job | Where it lives |
|---|---|---|
| **The mapping** (JSONata, as in lesson 03) | **It calculates** everything: the padding to 15, the 9-digit control number (1001 → `000001001`), the dates, the prices with two decimals, the `SE` count and the clean text | `mio/mappings/edi/850/v1/` · comes ready-made |
| **The template** (Mustache) | **It arranges** what already comes calculated into its place in the document. It calculates nothing | `mio/templates/edi/850/v1/850.mustache` · **your turn** |

Test it as in lesson 03, and open `mio/mappings/edi/850/v1/cases/dos-renglones/esperado.json` to see which data
reaches the template:

```bash
../../herramientas/probar-mapeo mio/mappings/edi/850/v1
```

**Why separate the two jobs?** Because each one is tested on its own. The calculation has its golden test cases; the
template only has to put each piece of data in its place. If you mixed the calculations into the template, there would
be no way to test them.

## 5. Finish the template

Open `mio/templates/edi/850/v1/850.mustache`. It already has the envelope, the start of the document and who it goes to:

```
ISA*00*          *00*          *ZZ*{{{body.isa.emisor}}}*ZZ*{{{body.isa.receptor}}}*…~
GS*PO*{{{body.gs.emisor}}}*…~
ST*850*{{{body.st.control}}}~
BEG*00*SA*{{{body.beg.orden}}}**{{{body.beg.fecha}}}~
N1*ST*{{{body.tienda.nombre}}}*92*{{{body.tienda.clave}}}~
```

Each `{{{ … }}}` is a slot that Mustache fills with a piece of data from the mapping. You are missing two parts. The
full instructions are in `LEEME.md`, next to the template.

**The order lines.** You need one `PO1` and one `PID` for each order line, and the order lines are in `body.lineas`. In Mustache,
a **section** repeats what it has inside once for each element of a list:

```
{{#body.lineas}}
… lo que va por cada renglón, usando {{{n}}}, {{{cantidad}}}, {{{precio}}}, {{{sku}}} y {{{descripcion}}} …
{{/body.lineas}}
```

Inside the section you are already standing on one order line, just as inside `renglones.{ … }` in JSONata. Use the example
850 and the reader to find out in which position each piece of data goes.

**The closing.** After the order lines: `CTT` (how many order lines), `SE` (the segment count and the control number of the
`ST`), `GE` and `IEA`. All the numbers already come calculated in `body.ctt`, `body.se.segmentos` and the control numbers.

Two rules:

- **Do not write comments inside the template:** where there was a comment, Mustache leaves an empty line, and an
  empty line is an error in an X12.
- **Use triple braces `{{{ }}}`.** In step 7 you will see why.

## 6. Run it and read it

From `mio/`, put two orders in `entrada/` and start the route:

```bash
cd mio
mkdir -p entrada
cp ../../../datos/ordenes/orden-1001.json ../../../datos/ordenes/orden-1005-caracteres.json entrada/
../../../herramientas/camel run orden-a-edi.camel.yaml
```

When you see `Generé el 850 de la orden 1005`, stop it with `Ctrl+C` and read what came out:

```bash
../../../herramientas/leer-x12 salida/850-1001.edi
diff salida/850-1001.edi ../../../datos/edi/850-ejemplo.edi
```

If the reader gives the six green checks and `diff` prints nothing, your 850 for order 1001 is identical to the example.

## 7. The characters that break a document

Open `datos/ordenes/orden-1005-caracteres.json`, in the root folder of the course. Its two products have awkward
names:

| Product in the order | How it comes out in the 850 |
|---|---|
| `Taza & plato` | `Taza & plato` |
| `Juego 4*6 ~ cristal` | `Juego 4 6   cristal` |

**Predict:** why did the second name change? Think about what the partner would do when reading `PID*F****Juego 4*6 ~ cristal~`.

The `*` and the `~` are the document's separators, and so is the `>`: the `ISA` declares them (the `>` is its last
element). If they go inside a piece of data, the partner would read one element too many and a segment cut in half.
That is why the mapping replaces them with spaces before they reach the template, and the golden
case `caracteres-especiales` checks that it does.

Now the `&`. **It is not a separator**: X12 accepts it inside a piece of data, and the partner would read it fine. Its
risk is a different one, and it comes from the template. Change **only** `{{{descripcion}}}` to `{{descripcion}}`, with
double braces, and run the route again. Look at the `PID` of order 1005:

```
PID*F****Taza &amp; plato~
```

Mustache was born to build web pages, and with double braces it "protects" the text as if it were HTML: the `&`
becomes `&amp;`. On a web page that is the right thing. In an X12, the partner would receive a product called
"Taza &amp; plato". Triple braces tell Mustache: **put the data in as is**. Put the triple braces back.

## 8. Check

From the lesson folder:

```bash
cd ..
./verifica
```

`verifica` empties `mio/entrada/` and `mio/salida/`, generates the 850 for orders 1001 and 1005, and checks them with
the reader. It is done when you see the five green checks:

```
✅ El mapeo del 850 pasa sus casos dorados
✅ El 850 de la 1001 es un X12 bien armado (el lector no encontró fallas)
✅ El 850 de la 1005 es un X12 bien armado (el lector no encontró fallas)
✅ El 850 de la 1001 es idéntico al de ejemplo
✅ El 850 de la 1005 (con & y *) es el esperado
```

**Not working?** `./verifica solucion` tests ours, and
`diff mio/templates/edi/850/v1/850.mustache solucion/templates/edi/850/v1/850.mustache` tells you where they differ.

| What you see | What to do |
|---|---|
| `trae &amp;: usa llaves TRIPLES` | Some brace was left double: go back to step 7 |
| `no pasa la revisión del lector` | Run `leer-x12` on that file: the check in red says what is missing (almost always the closing) |
| `no es idéntico al de ejemplo` | Compare them with `diff`: it is usually an element in another position or one `*` too few |

---

## What we learned building this lesson

Two real mistakes that came up while building it, and why they did not reach you:

- **A function that "cleans" can break the fixed width.** JSONata has `$trim`, which removes spaces. It also collapses
  repeated spaces into a single one, and that would have erased the padding of the `ISA`. That is why the mapping uses
  `$pad`, which pads, and never `$trim` on a fixed-width field.
- **Counting by hand fails.** When we wrote the example 850, the `SE` said 10 segments where there were 9. The reader
  flagged it in red. That is why the count is done by the mapping and checked by a tool, not by a person.

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [The EDI is built with a logic-less template](../decisiones/07-mustache-sin-logica-para-el-edi.md)

## Review

1. In an X12, what does the `*` separate and what does the `~` separate?
2. Which are the three envelopes of the document, and which segment opens and closes each one?
3. Why does the `ISA` carry padding spaces?
4. What does the mapping do and what does the template do? Why is it better not to mix them?
5. Why can a `*` not go inside a product name, and what happens to an `&` if you use double braces?

**Next:** in lesson 07 the 850 no longer stays in `salida/`: it travels to the partner's SFTP mailbox, taking care
that the partner does not pick up a half-written file.
