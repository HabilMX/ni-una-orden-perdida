# Lesson 13 — The shipment arrives

> **What you will achieve:** receive the file the warehouse drops when an order ships, without taking it half-written,
> without taking it twice and without losing the one that arrives broken. That file becomes an 856 for the partner and
> the EMBARCADO status in the API and in the ERP.

**Before you start you need:** lesson 12 finished and the environment from lesson 00 turned on. This lesson uses two
new ERP tables, `embarque` and `entrega`. If your ERP is from before, recreate it once:

```bash
docker compose -f infra/compose.yaml up -d --wait --build --force-recreate -V
```

## What is new today, in one sentence

Up to here, the order **came** to your integration: the store called the router. With the shipment it is the other way
around: the warehouse does not call anyone, it **drops a file in a folder** and leaves. Someone has to go and see
whether there is anything new. That is called polling (*Polling Consumer*), and today you configure it.

**The picture:** the mailbox of an apartment building. The mail carrier leaves the letters and does not ring the bell.
The caretaker goes down every so often to check, takes only the sealed envelopes (not the letter someone is still
writing at the table), sets aside what has already been delivered and puts to one side what arrived torn. And there is
**a single caretaker**: if there were three, two of them could carry up the same letter.

This is part 2: the order has already been created; now it leaves the warehouse (today), is split into several (14),
has its state stored (15) and reaches the customer (16 and 17).

---

## 1. Set up your folder

```bash
cd lecciones/13-llega-el-embarque
cp -R inicio mio
```

Today there is a new service, `sondeo-bodega`. Look at the lesson's `buzon/` folder, which in the `compose.yaml` is
mounted inside the poller's container. All your work goes in **a single file**:
`mio/sondeo-bodega/sondeo-bodega.camel.yaml`, and inside it, only in the parameters of the `from`.

Also open a sample shipment, `datos/embarques/embarque-4001.json`: it carries the order, the carrier, the
**tracking number** (the number the carrier uses to follow the package) and which order lines shipped.

## 2. Predict

Answer on paper before running anything:

1. In lesson 12 you learned to run three replicas of a service. If three replicas checked the same folder, what could
   happen to a file?
2. The warehouse writes the file little by little. What happens if the poller takes it when it is halfway through?
3. A broken file arrives, one that is not valid JSON. What should happen to it: be deleted, stay where it is or be set
   aside?

Look at how it is today:

```bash
./verifica inicio
```

Several checks come out red. Read them: each one tells you which precaution the poller is missing. The poller in
`inicio/` reads everything there is in `buzon/` and sets nothing aside (`noop: true` leaves every file where it was).

## 3. What is already done: the channels are reused

Before writing, look at what you do not have to write. Open `mio/orden-router/orden-router.camel.yaml` and look for
`platform-http:/embarques`. It is the door through which the poller hands each shipment to the router. Further down is
the same `multicast` from lesson 10, with the same three channels: `direct:canal-api`, `direct:canal-erp` and
`direct:canal-socio`. The only thing that differs is the **step**: before, each channel called `/integrar`; now it
calls `/embarque`.

| Channel | Service | What it does with the shipment |
|---|---|---|
| API | int-rest | Tells the API that the order shipped, as an event with its key (`orden-4001-embarcada`) |
| ERP | int-bd | Writes the `embarque` row with the tracking number and moves the order to EMBARCADO |
| Partner | int-edi | Builds an **856**, the X12 ship notice, and drops it on the SFTP with the precautions of lesson 07 |

And the rule from lesson 11 still holds: only the franchises receive EDI. That is why today the orders come from
stores T-501 and T-502.

## 4. The poller's four precautions

Camel's `file` component already knows how to poll. Your job is to tell it **which precautions to take**, with four
parameters. Each TODO in the file tells you which one:

| TODO | Parameter | The precaution | In the building's mailbox |
|---|---|---|---|
| 1 | `include` | Take only the finished files: `embarque-<algo>.json`. The `.tmp` does not match | Only the sealed envelopes |
| 2 | `readLock` with `changed` | Wait for the file to stop growing before taking it | Wait for the mail carrier to let go of the letter |
| 3 | `move` and `moveFailed` (and remove `noop`) | Set aside what the router received in `.camel/` and what failed in `.error/` | What was delivered to one side, what is torn to the other |
| 4 | `delay` | How often to check the folder: every 2 seconds, that is, 2000 milliseconds | How often the caretaker goes down |

Two hints:

- `include` is a **regular expression** over the file name. The dot in `.json` has to be escaped, and in
  YAML inside double quotes the backslash is written twice: `\\.`
- For the name of the file that is set aside use `${file:name}`: that way `embarque-4001.json` ends up as
  `.camel/embarque-4001.json`.

Notice what you do **not** do: you do not write a loop, you do not read the folder yourself, you do not delete
anything. The four precautions are configuration, and the component does the rest.

## 5. Break one on purpose

With your poller finished, remove the `moveFailed` line and run `./verifica`. First, predict: where does the broken
file go if you do not tell it where to send it?

`Buzón: el embarque roto no está en .error/` comes out red. The file stays in `buzon/`, and in the poller's log you
will see it fail time after time: without `moveFailed`, Camel leaves it where it was and tries it again on the next
round. A broken file that is retried forever is not lost, but it does not warn anyone either: it fills the log and
hides everyone else's errors. Put the line back.

## 6. Check

```bash
./verifica
```

`verifica` creates orders 4001 and 4002 in the three destinations and then drops three things in the mailbox: the
shipment of the 4001, finished; that of the 4002, still as `.tmp`; and a broken one. It is done when you see the ten
green checks:

```
✅ Arrancaron el router, los tres canales y el sondeo de la bodega
✅ Antes del embarque: la 4001 está CREADO en la API y REGISTRADO en el ERP
✅ API: la 4001 está EMBARCADO con la guía PAQX770004001
✅ API: recibió el embarque de la 4001 una sola vez
✅ ERP: la 4001 está EMBARCADO y su fila de embarque tiene la guía
✅ Socio: el 856 de la 4001 está en su SFTP y el lector X12 no le encontró fallas
✅ Socio: el 856 lleva la guía (REF*CN) y la orden (PRF)
✅ Buzón: el embarque de la 4001 se apartó a .camel/
✅ Buzón: el embarque roto se apartó a .error/ (no se perdió ni se reintenta sin fin)
✅ Buzón: el .tmp que la bodega aún escribía no se tocó (la 4002 sigue CREADO)
```

Look at the proof in the folder itself: `ls -A buzon buzon/.camel buzon/.error`. And open the 856 that reached the
partner, `mio/salida/856-4001.edi`: look for the tracking number in the `REF*CN` segment and the order in `PRF`.

**Not working?** `./verifica solucion` tests ours, and
`diff mio/sondeo-bodega solucion/sondeo-bodega` tells you where they differ.

| What you see | What to do |
|---|---|
| `el sondeo tomó el .tmp` | The `include` pattern does not end in `\\.json`: with a bare `embarque-.*`, `embarque-4002.json.tmp` gets in too. Or the `include` is missing |
| `el embarque de la 4001 no está en .camel/` | `noop: true` was left in, or the `move` does not carry `${file:name}` |
| `el embarque roto no está en .error/` | `moveFailed` is missing |
| `le faltan las tablas embarque y entrega` | Your ERP is from before this lesson: recreate it as "Before you start" says |
| `El puerto 8080 está ocupado` | You left the services of another lesson running: `docker compose … down` in its folder |

---

## What this teaches beyond the course

**Many systems do not call: they drop files.** Warehouses, banks and old systems hand over their work as files in a
folder or on an SFTP, at whatever time they can. The integration adapts to what the other side knows how to do, not
the other way around.

**Whoever polls goes alone.** Polling is little work, and the heavy part (mapping, calling the destinations) is done
by the router and the channels, which can indeed scale out in replicas. That is why the `compose.yaml` gives the poller
`replicas: 1` and lets the rest grow on the other side of an HTTP call.

**Setting aside is better than deleting.** With `.camel/` and `.error/`, anyone can see what was processed and what
has to be reviewed, without opening a log.

## Exercise: the second lifecycle event without new routes

With your version finished:

1. In the router, how many channel routes are there for the shipment and how many for the creation? What changes
   between one call and the other? (Hint: look for `${variable.paso}`.)
2. Change the `delay` to 10000 and run `./verifica`. How long does the EMBARCADO take to show up now? What do you gain
   and what do you lose with a slower poller? Go back to 2000.
3. Open the lesson's `compose.yaml`. Which service has `replicas: 1`? What would happen to the shipment of the 4001
   if you gave it 3? Think about the API, the ERP and the partner separately: who would notice and who would not?
   (Hint: the API's key.)

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [One folder, a single poller](../decisiones/16-un-solo-sondeo-por-carpeta.md)

## Review

1. What is polling, and why is it needed with the warehouse and not with the store?
2. What is each of the four precautions for: `include`, `readLock`, `move` and `moveFailed`, and `delay`?
3. Why was the `.tmp` of the 4002 not taken? What would happen if the warehouse wrote directly with the final name?
4. What does a broken file do without `moveFailed`, and why is that a problem even though it is not lost?
5. **The closing question:** why does the poller run in a single replica, while the router and the channels can run in
   several? What would happen if all the replicas did it?

**Next:** in lesson 14, the warehouse no longer sends one file per order: it sends one with everything that shipped
during the morning. One file, several orders. How many 856s do you expect?
