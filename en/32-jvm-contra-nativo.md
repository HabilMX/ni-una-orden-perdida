# Lesson 32 — JVM versus native, measured

> **Optional appendix.** It can be **read** without running it.
>
> **What you will achieve:** building the same int-rest in two ways, for the JVM and as a native executable, and
> measuring on your machine what changes: how long each one takes to build, to be ready to serve and how much memory
> it uses. And measuring it **well**: so that each figure says what it claims to measure.

**Before you start, check your machine.** Compiling to native is the heaviest thing in the whole course. On the
machine where we measured it (Mac with an Apple M5, Docker with 6 CPUs and 16 GiB), the compiler got to use 4.6 GB of
memory and 4.6 cores, and the native build took 202 seconds from scratch. The detail is in
[`lecciones/31-de-laboratorio-a-kubernetes/CAPACIDAD.md`](../lecciones/31-de-laboratorio-a-kubernetes/CAPACIDAD.md).

**You also need:** lesson 31 read (this one uses its `Dockerfile`) and, if you do not know native mode,
[`conceptos/antes-de-empezar/07-nativo.md`](../conceptos/antes-de-empezar/07-nativo.md). You do not need kind or the
environment from lesson 00: the lesson brings its own `compose.yaml` with the API, the two int-rest services and a
load client.

## What is new today, in one sentence

Until today, every service in the course ran on the JVM. Camel Quarkus can also be compiled **to native**: an
executable that starts without a JVM. A lot is said about how much faster it starts and how much memory it saves.
Today you do not believe it: **you measure it**, with a fixed method, on your machine. The new difficulty is not
native: it is **measuring well**.

**The picture:** comparing two cars. If you measure one with the engine already warm and the other cold, or for one
you count from when the engine starts and for the other from when the driver gets in, the result says nothing about
the cars. To compare, both go around the same track, with the same load and the same stopwatch.

| What is measured | With what | Why this way |
|---|---|---|
| Building | The clock, with `--no-cache` | So that neither reuses what was left from the previous time |
| What Quarkus says | The `started in …` line in its log | To compare it with the next one |
| Until healthy | From when the container is **requested** until `/q/health/ready` answers | It is what someone waits for when they need the service instance to serve |
| Memory at rest | `docker stats`, already started, with no work | What it takes up while waiting |
| Memory under load | `docker stats` every second **during** a load of 100 orders per second, 20 s; the highest | What it takes up while working |
| Image | `docker image inspect` | What it weighs to pull it |

The two containers have the same ceiling (1 CPU and 512 MiB) and are measured **one at a time**, so that one does not
take CPU away from the other.

---

## 1. Set up your folder

```bash
cd lecciones/32-jvm-contra-nativo
cp -R inicio mio
```

Your work is a script: `mio/medir`. It already builds the two images, brings up each mode, launches the load and
writes the table to `mio/cifras.md`. It is missing the three functions that **measure**. Open it and read it from top
to bottom before writing.

Also open `compose.yaml`: the two int-rest services come from the same `Dockerfile` from lesson 31, with two
different `target` values (`jvm` and `nativo`), and they listen on `8091` and `8092`.

## 2. Predict

Write on paper, **before** measuring:

1. How many times faster do you think native starts? And how much less memory does it use at rest?
2. Which of the two do you think takes longer to **build**?
3. Quarkus says in its log `started in 0.05s`. Is that how long the service instance takes to be able to serve an order?

Look at how it is today (it builds the two images from scratch; on the machine in `CAPACIDAD.md` it took about 7
minutes):

```bash
./verifica inicio
```

The images are built, start and deliver their order to the API. But in `cifras.md` the figures for `hasta sano`
("until healthy"), `memoria en reposo` ("memory at rest") and `memoria con carga` ("memory under load") are missing:
the functions that measure still answer `—`.

## 3. The three measurements

### TODO 1: the memory of a container

`docker stats` gives it like this: `145.8MiB / 512MiB`. Ask for a single reading, only that column, and keep the
first number, in MiB:

```bash
memoria() {
  docker stats --no-stream --format '{{.MemUsage}}' "$1" 2>/dev/null | awk '{
    v=$1; n=v+0
    if (v ~ /GiB/) n=n*1024; else if (v ~ /KiB/) n=n/1024
    printf "%.1f\n", n }'
}
```

`n=v+0` turns `145.8MiB` into `145.8`: awk takes the leading digits. The rest is changing units.

### TODO 2: until healthy

Take the time **before** requesting the container and ask every 50 ms for `/q/health/ready`, for at most 60 seconds:

```bash
hasta_sano() {
  local modo=$1 t0 p
  p=$(puerto "$modo"); t0=$(ms)
  C up -d --force-recreate "int-rest-$modo" >/dev/null 2>&1
  for _ in $(seq 1 1200); do
    curl -sf -o /dev/null --max-time 1 "localhost:$p/q/health/ready" && { echo $(( $(ms) - t0 )); return 0; }
    sleep 0.05
  done
  echo "—"
}
```

Why is the `started in` from the log not enough? Because that is what Quarkus says about itself: it does not count
how long the container takes to be born. Whoever waits for the service instance to serve waits for both things.

### TODO 3: the memory under load

The load runs in the background. **While** it is still alive, take the memory every second and keep the highest:

```bash
while kill -0 "$carga" 2>/dev/null; do
  m=$(memoria "$cont"); awk -v a="$m" -v b="$tope" 'BEGIN{exit !(a+0 > b+0)}' && tope=$m
  sleep 1
done
```

`kill -0` kills nothing: it only asks whether the process is still alive. It goes before the `wait`.

## 4. Break one on purpose

With your `medir` finished, change TODO 3: delete the `while` and, after the `wait`, take **one** single reading
(`tope=$(memoria "$cont")`). Before running it, copy your `mio/cifras.md` somewhere else. Run `./verifica`.

First, predict: does `verifica` detect it?

No: it comes out green. `memoria con carga` is still a number, and `verifica` only asks that it be one. Compare with
the copy you saved: the new figure is the memory **after** the load, and it can come out lower than the true one. **A
badly made measurement does not fail: it returns a number and sounds like a fact.** That is why `verifica` does not
judge your figures against any threshold (card 39), and that is why the method is written next to the table. Put the
`while` back.

## 5. Check

```bash
./verifica
```

`verifica` runs your `mio/medir`, which builds the two images from scratch and measures each one. Then it sends an
order to each image and checks in the API's log that it arrived, checks that the native one carries no JVM and
checks that `cifras.md` was written **in this run** with the six figures for each mode. It is done when you see the
green checks:

```
✅ La imagen jvm arrancó, contestó y su orden (…) llegó a la API
✅ La imagen nativo arrancó, contestó y su orden (…) llegó a la API
✅ Control: la imagen nativa no trae JVM; corre un ejecutable de (…) MB
✅ Cifras de jvm medidas: construir (…)s · Quarkus (…)s · sano (…) ms · reposo (…) MiB · carga (…) MiB · imagen (…) MB
✅ Cifras de nativo medidas: construir (…)s · Quarkus (…)s · sano (…) ms · reposo (…) MiB · carga (…) MiB · imagen (…) MB
```

The figures are **yours**. On the machine where we tested it, the JVM was healthy in about 1,700 ms with about 108
MiB at rest, and native in about 240 ms with about 16 MiB. In exchange, native took longer to build. On your machine
they will be different, and that is fine: compare against yours, not against ours.

**Not working?** `./verifica solucion` tests ours, and `diff mio/medir solucion/medir` tells you where they differ.

| What you see | What to do |
|---|---|
| `El puerto … está ocupado` | Shut down what you left running and try again |
| `Tu mio/medir terminó con error` | Run `./mio/medir` by itself: it tells you at which step it stopped |
| `No existe la imagen ni-una-orden-perdida/int-rest:nativo: no se construyó` | Read `mio/build-nativo.log`. If it ran out of memory, check `CAPACIDAD.md`: the `Dockerfile` lets you lower `MEMORIA_NATIVO`, and it takes longer |
| `La imagen … no entregó su orden … a la API` | The lines that follow are the int-rest log: they say why |
| `Tu medir no escribió mio/cifras.md` or `no se escribió en esta corrida` | `medir` stopped before writing the table: run it by itself |
| `A las cifras de … les falta (o no es un número): hasta sano` | TODO 2 is missing, or `/q/health/ready` never answered within 60 s |
| `… les falta …: memoria en reposo, memoria con carga` | TODO 1 is missing (and with it, TODO 3 does not measure either) |

---

## What this teaches beyond the course

**A figure without its method cannot be compared.** "It starts in 50 ms" can be what the program says about itself or
what whoever uses it waits for. Both are true and they are not the same figure.

**Native trades one cost for another.** It starts fast and uses little memory, and it takes longer to build and asks
for a big machine to compile. It pays off where many replicas are started often (like the peaks in lesson 31); in a
service that runs for weeks without restarting, the difference in startup is barely noticed.

**Published figures are from another machine.** Those in an article, ours and yours are from different machines. They
are useful for knowing what to expect; to decide, you measure on the machine where it is going to run.

**A green test does not prove that you measured well.** `verifica` can check that there is a number, not that the
number is the right one. The method is what holds it up.

## Exercise: the same track

1. Write in `mio/cifras.md`, below the table, your predictions from step 2 and where you were wrong.
2. Run `./mio/medir` twice in a row. How much do `hasta sano` and `memoria con carga` change from one run to the
   other? What does that tell you about comparing a single run?
3. On paper: your team has one service that scales from 1 to 10 replicas at the morning peaks, and another that runs
   without restarting all month. With **your** figures, which one would you compile to native and why?

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [int-rest is exported with camel export to Camel Quarkus, without changing its route](../decisiones/36-int-rest-exportado-con-camel-export.md)
- [Native is compiled with Mandrel inside a container](../decisiones/38-el-nativo-se-compila-en-un-contenedor.md)
- [JVM versus native is measured on your machine, with a fixed method and without thresholds](../decisiones/39-se-mide-en-tu-maquina-sin-umbrales.md)

## Review

1. What is a native executable, and what is it missing that the JVM does have?
2. Why are `hasta sano` and `started in` different figures?
3. Why is the memory under load read every second during the load, and not once at the end?
4. Why are the two containers measured one at a time and with the same ceiling?
5. **The closing question:** someone tells you "native starts 30 times faster". What do you ask them before
   believing it?

**The course ends here.** If you got this far, an order now crosses the API, the ERP and the partner without getting
lost, and you know where to look when something fails.
