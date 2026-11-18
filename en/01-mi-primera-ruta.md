# Lesson 01 — My first route runs

> **What you will achieve:** run a Camel route, predict what it does before you see it and put a name to its three
> pieces. It is the only lesson in which you write nothing from scratch: the route comes ready-made.

**Before you start you need:** lesson 00 finished and Docker working. Nothing else: you do not install Java or Camel.

**What Camel is, in one sentence:** a program that moves information from one system to another by following the
instructions you write for it. Those instructions are called a **route**. Today the route moves a file from one folder
to another, which is the simplest thing there is. In the following lessons, the same idea will move a store's orders
between systems that do not know each other.

⚠️ **Work inside your home folder** (`~`, for example `~/practicas/`), never in `/tmp`. On Mac with colima,
Docker can only see the folders that are inside your home. If you cloned the repo somewhere else, the route fails with
`Source file does not exist`.

---

## 1. Set up your folder

Everything of yours goes in `mio/`. It is a copy of `inicio/` and git does not track it, so you can break whatever you
want in there.

```bash
cd lecciones/01-mi-primera-ruta   # entra a la carpeta de la lección
cp -R inicio mio                  # copia inicio/ completa (-R: con todo lo de adentro) y la llama mio/
cd mio                            # entra a tu copia
```

## 2. Read the route and predict

Open `primera-ruta.camel.yaml` with your editor. It is written in **YAML**, a text format where indentation
(the spaces at the start of each line) shows what goes inside what, like folders inside folders. Without the
comments, it is this:

```yaml
- route:
    id: primera-ruta
    from:
      uri: file:entrada
      parameters:
        include: ".*\\.json"
      steps:
        - log:
            message: "Llegó la orden del archivo ${header.CamelFileName}: ${body}"
        - to:
            uri: file:salida
```

**Before you run it, write your answers on paper** (there are no wrong answers; what matters is comparing them
afterwards):

1. If you put a file `orden-1001.json` in the `entrada/` folder, what do you think will show up on the screen?
2. Where will the file end up?
3. What will be left in `entrada/` when the route is done with it?

## 3. Run it

Put an order in `entrada/` and start the route:

```bash
mkdir -p entrada                                     # crea la carpeta entrada/ (si ya existe, no pasa nada)
cp ../../../datos/ordenes/orden-1001.json entrada/   # copia la orden de ejemplo; ../ sube una carpeta
../../../herramientas/camel run primera-ruta.camel.yaml
```

The order is a **JSON** file: text with name and value pairs (`"orden": "1001"`). Open it before you run:
it is what is going to travel.

The first time it takes a while, because Docker downloads the Camel image. When you see a line with
`Started primera-ruta`, the route is already listening. A few seconds later the announcement of the order appears. To
stop it, press `Ctrl+C`.

## 4. Compare with your prediction

Look at your three answers and check the folders:

```bash
ls salida/       # lista lo que hay en salida/
ls -a entrada/   # -a muestra también lo escondido (lo que empieza con punto)
```

- **The screen:** the log says `Llegó la orden del archivo orden-1001.json:` ("the order from file orden-1001.json arrived") and then the whole order. `${header.CamelFileName}`
  was replaced by the file name and `${body}` by its content.
- **The file:** it is in `salida/`, identical to how it came in.
- **`entrada/`:** it looks empty, but `ls -a` shows a hidden folder, `.camel/`, and your order is inside it.
  **Camel does not delete what it reads:** it sets it aside so as not to process it again. Had you predicted that?

If any answer did not match, write down why you think that happened. That note is worth more than having been right.

## 5. Name what you saw

The route has three pieces. Name them with a picture and then with their formal name:

| What you see in the YAML | The picture | Its name in the integration patterns (EIP) |
|---|---|---|
| The whole route: from `from` to `to`, passing through `log` | **The pipe** | *Pipes and Filters* |
| `file:entrada` and `file:salida`, where things come in and go out | **The stations** | *Message Endpoint* |
| The order that travels, with its file name and its content | **The letter** | *Message* |

The EIPs (*Enterprise Integration Patterns*) are names that any integration team in the world recognizes. You do not
need to memorize them today: a new one appears in each lesson, right when you use it.

**Exercise:** in your `mio/primera-ruta.camel.yaml`, write above each part a comment with its name
(`# estación`, `# tubería`, `# carta`: station, pipe, letter). The letter has a catch: it is not written in the YAML. Why?

## 6. Change it

Now modify the route. After each change, put the order back in `entrada/` and run again:

1. **Change the log message** so that it says `Recibí la orden ${header.CamelFileName}`. Before you run: what do you
   expect to see?
2. **Change the output station** to `file:procesadas`. Which new folder do you expect to see?
3. **Put a file that is not `.json`** in `entrada/`, for example `nota.txt`. Does the route pick it up? Which line of
   the YAML decides that?

## 7. Check

From the lesson folder, not from `mio/`:

```bash
cd ..
./verifica
```

⚠️ `verifica` **empties and refills** `mio/entrada/` and `mio/salida/` to test from scratch. It does not touch your route.

It is done when you see the five green checks:

```
✅ La ruta arrancó
✅ El log anunció la orden
✅ La orden llegó a salida/
✅ entrada/ quedó vacía
✅ Salió idéntica a como entró
```

If you changed the output to `procesadas/` in step 6, put it back to `salida/` before checking.

**Not working?** Try ours: `./verifica solucion`. If ours comes out green and yours does not, compare the two
with `diff mio/primera-ruta.camel.yaml solucion/primera-ruta.camel.yaml`. Using ours is not cheating: it is so that
a mistake today does not hold you back in the following lessons.

## 8. The map of the trip

**You do not have to understand this today.** The map of what you are going to build is the syllabus in the
[course README](../README.md): it follows the order in which the order travels, from being created to not being lost.
Each piece arrives in its lesson; today you only need the route, the endpoint and the message.

*Why Camel:* we start from premises, not from tastes. We look for something lightweight, that scales per service, that
is defined by configuration and that has an open license. At the end of the course you will measure for yourself what
it gains and what it gives up. The details are in [Why Camel](../conceptos/por-que-camel.md).

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [Apache Camel as the integration engine](../decisiones/01-apache-camel-como-motor.md)
- [Routes in YAML, in their canonical form](../decisiones/02-rutas-en-yaml-canonico.md)

## Review

Without looking at the YAML, answer:

1. What does `from` do, and what does `to` do?
2. What is the letter in this route, and why is it not written in the YAML?
3. What does Camel do with a file it has already read?
4. If you wanted the route to pick up only `.xml` files, which line would you change?
5. You put `nota.txt` in `entrada/`. Did the route pick it up? Which part of the YAML decided that?

**Next:** in lesson 02, the input station stops being a folder: the order arrives over HTTP and goes out to
an API.
