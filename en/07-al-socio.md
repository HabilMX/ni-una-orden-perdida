# Lesson 07 — To the partner

> **What you will achieve:** leave the 850 from lesson 06 in the trading partner's mailbox with four precautions: that
> the partner does not pick up a half-written file, that the same order always has the same name, that a second
> send does not erase the first and that the route only connects to the server whose key is in `known_hosts`.

**Before you start you need:** lesson 06 finished and the environment from lesson 00 turned on:

```bash
docker compose -f infra/compose.yaml up -d --wait     # desde la carpeta raíz de la práctica
```

## What is new today, in one sentence

The partner does not have an API: it has an **SFTP mailbox**, a folder on its server to which you upload files and
from which it reads them **whenever it wants**. It does not tell you when it reads, nor does it answer you. That is
why what you leave there has to be complete and cannot get in the way of what was already there.

**SFTP, the bare minimum:** a program for copying files to another computer over the network securely. It asks for a
user and a password (or a key), and before connecting it checks that the server is who it says it is. That check
is step 2.

---

## 1. Set up your folder

```bash
cd lecciones/07-al-socio
cp -R inicio mio
cd mio
```

The route uses the mapping and the template for the 850 from lesson 06, which already come inside `mio/`.

## 2. Trust the partner

The first time you talk to a server you have to know that it is **your partner's** and not another one posing as
it. Every server has a **public key**, like a fingerprint. You write it down once in a file called
`known_hosts`, and from then on the route only connects if the server presents that same key.

```bash
../../../herramientas/confiar-en-socio
```

In real life, the partner sends you its key through another channel (an email, a portal) and you write it down by
hand. Here the script takes it straight from the partner's container, which stands in for its administrator.

⚠️ If you shut the environment down with `down` and bring it up again, the partner's server gets a brand-new key: run
the script again. `known_hosts` is yours and git does not track it.

## 3. Read the route

Open `orden-al-socio.camel.yaml`. It is the route from lesson 06 with a different output station:

```yaml
uri: sftp://socio@socio-sftp:22/entrada
```

| Part | What it is |
|---|---|
| `socio@` | The user with which you enter the mailbox |
| `socio-sftp:22` | The partner's server and its port |
| `/entrada` | The folder where the partner collects what is sent to it |

Below come its parameters:

| Parameter | What it does |
|---|---|
| `knownHostsFile: known_hosts` and `strictHostKeyChecking: "yes"` | It only connects if the server's key is the one you wrote down in step 2 |
| `fileName: "850-${variable.orden}.edi"` | The final name. Order 1001 is always called `850-1001.edi` |

That name is called **deterministic**: it depends only on the order. If it were named with the time or with a random
number, the same order sent twice would leave two files, and the partner would fulfill it twice.

## 4. Predict

Answer on paper:

1. If you send order 1001 twice, how many files do you expect in the partner's mailbox?
2. If the second time the order carries **a different quantity** (5 mugs instead of 2), what does the file contain at the end?
3. If someone swaps the partner's server for another one with a different key, what do you think the route does?

## 5. Run it twice

Put order 1001 in `entrada/` and run the route. `--max-messages=1` stops it on its own after one order:

```bash
mkdir -p entrada
cp ../../../datos/ordenes/orden-1001.json entrada/
../../../herramientas/camel run orden-al-socio.camel.yaml --max-messages=1
```

Ask the partner's mailbox what it has:

```bash
docker compose -f ../../../infra/compose.yaml exec socio-sftp ls /home/socio/entrada
docker compose -f ../../../infra/compose.yaml exec socio-sftp cat /home/socio/entrada/850-1001.edi
```

Now send the **same order with a different quantity**, `orden-1001-cambiada.json`, and look at the mailbox again:

```bash
cp ../../../datos/ordenes/orden-1001-cambiada.json entrada/
../../../herramientas/camel run orden-al-socio.camel.yaml --max-messages=1
docker compose -f ../../../infra/compose.yaml exec socio-sftp cat /home/socio/entrada/850-1001.edi
```

Look for the first `PO1`. It says `PO1*1*5*…`: **the second send erased the first one without warning**. There is a
single file, as you predicted in question 1, but it is no longer the order the partner had. If it had already read it,
it now has two different versions of order 1001 and does not know which one counts.

## 6. Add the two missing precautions

In the route there is a **TE TOCA** ("your turn") comment. Two more parameters of the `sftp:` station go there:

**Temporary name.** Uploading a file takes time. If the partner reads its folder right in the middle, it takes a
truncated file. The solution is to write it under another name and rename it at the end, which is instantaneous:

```yaml
tempFileName: "${file:onlyname.noext}.tmp"
```

While it is being written it is called `850-1001.tmp`; when it finishes, it is renamed to `850-1001.edi`. If the
partner only collects `.edi` files, it is very unlikely to take an incomplete one: the rename is the last thing that
happens. It is not a total guarantee (a partner that collects any file, or a badly configured shared folder, breaks
it), and that is why in lesson 21 the confirmation that it arrived well comes from the 997, not from the file.

**Do not overwrite.** By default, if the file already exists, Camel replaces it. You want the opposite: for it to fail
and for someone to decide.

```yaml
fileExist: Fail
```

Mind the indentation: both go at the same level as `fileName`.

Empty the mailbox and repeat step 5:

```bash
docker compose -f ../../../infra/compose.yaml exec socio-sftp rm /home/socio/entrada/850-1001.edi
```

Now the second send does not go through. In the route's terminal you see:

```
File already exist: entrada/850-1001.edi. Cannot write new file.
```

And the changed order **stayed in your `entrada/` folder**, without being set aside in `.camel/` like the ones that
go out fine. It was not lost: it is waiting for someone to decide what to do with it. You will solve that in lesson 18.

## 7. Change the key

Now question 3. Back up your `known_hosts`, put in a fake key and send an order:

```bash
cp known_hosts known_hosts.bueno
echo "socio-sftp ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIHVuYWxsYXZlZGVvdHJvc2Vydmlkb3JxdWVub2Vz" > known_hosts
cp ../../../datos/ordenes/orden-1003.json entrada/
../../../herramientas/camel run orden-al-socio.camel.yaml --max-messages=1 --max-seconds=40
```

Among the errors you will find:

```
HostKey has been changed: socio-sftp
```

The route **refused to connect**: the server presented a key different from the one written down. It may be that the
partner changed it (and it would have to let you know through another channel), or that someone is in the middle
posing as it. In both cases, the right thing is to send it nothing. Put your good key back:

```bash
mv known_hosts.bueno known_hosts
```

## 8. Check

```bash
cd ..
./verifica
```

⚠️ `verifica` **empties the partner's mailbox** before testing.

It is done when you see the seven green checks:

```
✅ Llegó 850-1001.edi al SFTP del socio
✅ No quedó ningún archivo .tmp
✅ Lo que recibió el socio es el 850 correcto
✅ Un segundo envío de la 1001 no pisó el archivo que ya estaba
✅ La ruta escribe con nombre temporal
✅ La ruta intentó conectar y el servidor no presentó la llave anotada
✅ Con otra llave en known_hosts, la ruta se negó a conectar
```

The fifth one is different from the rest: the `.tmp` lives for a few milliseconds and there is no way to see it from
outside, so `verifica` checks it in your route. The sixth looks in the log for the `HostKey has been changed` from
step 7: it shows that the route did try to connect and that it was the key that rejected it, not some other failure.
The rest are confirmed by the partner's mailbox.

**Not working?** `./verifica solucion` tests ours, and
`diff mio/orden-al-socio.camel.yaml solucion/orden-al-socio.camel.yaml` tells you where they differ.

| What you see | What to do |
|---|---|
| `PISÓ el archivo del socio` | `fileExist: Fail` is missing |
| `no usa nombre temporal` | `tempFileName` is missing |
| `HostKey has been changed` in every run | You recreated the environment: run `confiar-en-socio` again (step 2) |
| `No pude hablar con el SFTP del socio` | Turn on the environment: `docker compose -f infra/compose.yaml up -d --wait` |

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [Four precautions when leaving a file for the partner](../decisiones/08-entrega-segura-al-sftp.md)

## Review

1. Why is the file written under a temporary name and renamed at the end?
2. What makes the name `850-1001.edi` deterministic, and what would happen if it carried the time?
3. Without `fileExist: Fail`, what happened to the first send? Why is that worse than an error?
4. What is `known_hosts` for, and what does the route do if the server presents a different key?
5. The order that could not be sent stayed in `entrada/`. Why is that better than deleting it?

**Next:** you now have the three destinations: the API, the ERP and the partner. In lesson 08 you bring them together:
the order comes in through a **router** that distributes it among services that call each other by name.
