# Lesson 00 — Environment ready

> **What you will achieve:** bring up, with a single command, the four systems you will integrate with throughout the
> course, and check that each one answers. You do not program anything yet: you set up the workshop.

## The story in one sentence

You are going to connect an **online store** with the systems it needs in order to sell: an **orders API**, the
company's **ERP** and a **trading partner** that receives files. Those systems already exist and they are not yours:
your job is to make the information travel between them without getting lost. In this lesson you turn them on.

## Before you start you need

| What | What for | How you check that you have it |
|---|---|---|
| A terminal | Typing the commands | On Mac, the *Terminal* app; on Windows, *Ubuntu* inside WSL; on Linux, whichever one it comes with |
| **git** | Downloading the course repository | `git --version` answers with a number |
| **Docker** with Compose | Running each system in its own container, without installing it on your machine | `docker compose version` answers with a number |
| Free memory for Docker | The four systems and Camel run at the same time | `docker compose up --wait` finishes without your machine freezing |

You do not install Java, or Maven, or Camel, or any database. **Everything runs inside Docker.**

If any of these words is new to you (terminal, git, container), first read the cards in
[`conceptos/antes-de-empezar/`](../conceptos/antes-de-empezar/). They are short.

⚠️ **Work inside your home folder** (`~`), never in `/tmp`. On Mac with colima, Docker only sees the folders in your
home.

---

## 1. Download the course

```bash
cd ~                                  # ve a tu carpeta personal
git clone https://github.com/HabilMX/ni-una-orden-perdida.git   # baja una copia a tu máquina
cd ni-una-orden-perdida               # entra a la carpeta que se creó
```

## 2. Predict

Open `infra/compose.yaml` with your editor. It is the list of the systems you are going to bring up. Without running
anything, answer on paper:

1. How many services are there, and what is each one called?
2. Which port number does each one use on your machine? *(Hint: look for `ports:`. The number on the left is the one
   on your machine; the one on the right, the one inside the container.)*
3. Which one do you think will take the longest to start the first time, and why?

## 3. Bring it up

From the root folder of the course:

```bash
docker compose -f infra/compose.yaml up -d --wait
```

- `-f infra/compose.yaml` tells Docker which list of services to use.
- `up` starts them.
- `-d` leaves them running in the background, so you get your terminal back.
- `--wait` waits until all of them say they are healthy.

**The first time it takes several minutes**, mostly because of the orders API: Docker builds it from its code before
starting it. You will know it is done when `--wait` gives you the terminal back and `docker compose ps` says `healthy`
for each system. After that it is much faster.

## 4. Compare with your prediction

```bash
docker compose -f infra/compose.yaml ps
```

You should see four services, all `healthy`:

| Service | What it is in the story | On your machine | What it is, technically |
|---|---|---|---|
| `api-pedidos` | The company's order system, which you talk to over the internet | `localhost:8081` | A REST API built with Quarkus. It stores in memory: when you restart it, it starts empty |
| `erp` | The **ERP**: the central system where the company records what it sells | `localhost:13306` | A MariaDB database with two tables (header and line items) and a **stored procedure** (`registrar_pedido`): a program stored inside the database |
| `socio-sftp` | The **trading partner**'s mailbox, where files are left for it | `localhost:2222` | An **SFTP** server, for copying files securely, with the folders `entrada/` and `salida/` |
| `estado` | The notebook where your integration writes down how each order is doing | `localhost:15432` | A PostgreSQL database with three tables. You do not touch it until lesson 15 |

Did you get right which one took the longest?

## 5. Check

```bash
cd lecciones/00-ambiente
./verifica
```

It is done when you see the nine green checks:

```
✅ estado está arriba
✅ erp está arriba
✅ socio-sftp está arriba
✅ api-pedidos está arriba
✅ La base de estado tiene sus tres tablas
✅ El ERP tiene su procedimiento registrar_pedido
✅ El ERP rechaza lo que no cuadra
✅ El SFTP del socio tiene entrada/ y salida/
✅ La API de pedidos contesta y exige su llave (400 sin ella)
```

Notice the seventh. `verifica` asks the ERP to record an order that does not exist, and **expects the ERP to refuse**. The ninth does the same with the API: it sends it an order without
its key and expects it to reject it.
The systems in this course do not say yes to everything, just like real ones: they validate, they reject and sometimes they answer late.
If a destination accepted anything, your integration would seem to work even if it were wrong.

## 6. Shut it down and turn it back on

```bash
docker compose -f infra/compose.yaml down        # apaga y quita los contenedores
docker compose -f infra/compose.yaml up -d --wait
```

Shutting down with `down` erases what was inside: the environment comes back clean every time. At the start of each
lesson you will turn it on this way.

## If something fails

| What you see | What is happening | What to do |
|---|---|---|
| `port is already allocated` | You already have something using that port. That is why the course uses 15432 and 13306 instead of the usual ones (5432 and 3306), but another program may be on 8081 or 2222 | Stop that program, or ask in the channel how to change the port |
| `Cannot connect to the Docker daemon` | Docker is not running | Open Docker Desktop, or on Mac `colima start` |
| A service stays in `starting` | It is short on memory or it is still building | Wait one more minute; if it does not change, `docker compose -f infra/compose.yaml logs <servicio>` |
| `Source file does not exist` | You cloned the repo outside your home (Mac with colima) | Clone it inside `~` |

## A note about passwords

In `infra/compose.yaml` the passwords are written in plain sight (`practica`). Here that is acceptable because it is a
practice environment on your machine. **In a real system this is never done:** passwords arrive through environment
variables or a secrets manager, and they are not stored in the repository.

---

## Why it was done this way

Every decision in the course has its card: what we decided, why, what we ruled out and what would make us
change our mind. They are in [`decisiones/`](../decisiones/), and each lesson links you to its own. Today there is
none yet: the environment is the floor, not a design decision.

## Review

1. What does each of the four pieces do in the store's story?
2. What do `up`, `-d` and `--wait` mean?
3. Why is it good that the ERP rejects an order that does not exist?
4. Why does the state database use port 15432 on your machine and not the usual 5432? What would happen if it used 5432
   and you already had a PostgreSQL installed?
5. What happens to the ERP's data when you shut the environment down with `down` and turn it back on? Why is that
   convenient in a course?

**Next:** in lesson 01 you run your first Camel route. It does not use these systems yet: it moves a file from
one folder to another.
