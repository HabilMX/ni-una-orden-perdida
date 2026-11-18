# Lesson 31 — From lab to project, on Kubernetes

> **Optional appendix.** The course ended at lesson 30. This lesson and lesson 32 are for anyone who works with
> Kubernetes or wants to see how far what you built can go. They can be **read** without running them.
>
> **What you will achieve:** taking int-rest, the same one from lesson 12, from the lab folder to a Camel Quarkus
> image, running it on a practice Kubernetes and making **its replicas go up by themselves** when many orders arrive,
> and come down when they run out.

**Before you start, check your machine.** We measured it on a Mac with an Apple M5 and Docker with 6 CPUs and 16 GiB:
lesson 31 asked for about 1.4 GiB of memory on top of what was already in use, 2.7 cores at the peak and about 4.4 GB
of images. Lesson 32 asks for quite a bit more. The detail, and how it was measured, is in
[`lecciones/31-de-laboratorio-a-kubernetes/CAPACIDAD.md`](../lecciones/31-de-laboratorio-a-kubernetes/CAPACIDAD.md).
These are figures from a single machine: on yours they may be different.

**You also need:** lesson 12 finished, and two programs you have not used until today:

| Program | What for | Where to install it |
|---|---|---|
| `kind` | A practice Kubernetes that runs inside Docker | [kind.sigs.k8s.io, "Installation"](https://kind.sigs.k8s.io/docs/user/quick-start/#installation) |
| `kubectl` | Talking to that Kubernetes | [kubernetes.io, "Install Tools"](https://kubernetes.io/docs/tasks/tools/) |

This lesson does **not** use the environment from lesson 00: it creates its own cluster, named
`ni-una-orden-perdida`, and deletes it when it finishes, whether it goes well or badly. If you do not know Kubernetes,
first read [`conceptos/antes-de-empezar/08-kubernetes.md`](../conceptos/antes-de-empezar/08-kubernetes.md).

## What is new today, in one sentence

In lesson 12 you set `replicas: 3` by hand: three replicas always, whether there is a lot of work or none. Today you
leave that decision to Kubernetes, with an **HPA** (*HorizontalPodAutoscaler*): a piece that watches how much CPU the
replicas of int-rest use and adds more when they get close to their target, or removes some when there are too many. So
that it can measure, each replica has to **request** CPU: the HPA measures as a percentage of what is requested.

**The picture:** the checkout lanes of a supermarket. With few people there is one open. The manager watches the
lines and, when each cashier is serving more than her share, opens another one, up to the four the store has. When
the store empties, the manager closes them one by one. But to know whether a cashier "is at 150%", you first have to
say how much is each one's share. That is what gets requested.

---

## 1. Set up your folder

```bash
cd lecciones/31-de-laboratorio-a-kubernetes
cp -R inicio mio
```

Before writing, read what is already done:

| File | What it is | Do you write it? |
|---|---|---|
| `int-rest/int-rest.camel.yaml` | The int-rest route from lesson 12, **without changing a letter**, with its mappings and its golden test cases | No |
| `Dockerfile` | From the folder to an image, in stages: `camel export` turns it into a Camel Quarkus project, it is compiled, and the result is an image for the JVM (and a native one, for lesson 32). All inside containers: you do not install Java or Maven | No, read it |
| `cluster/` | The kind cluster, metrics-server (what measures the CPU of each replica), the orders API and a client that sends the load | No |
| `mio/k8s/int-rest.yaml` | The Deployment (the replicas) and the Service (the fixed name that spreads traffic among them) | **Yes, TODO 1 and 2** |
| `mio/k8s/hpa.yaml` | The HPA | **Yes, TODO 3 and 4** |

Notice two things about the Dockerfile and the Deployment. The configuration does not go in an
`application.properties`: what is decided at compile time goes in the `pom.xml` and what is decided at run time goes
in environment variables of the Deployment (`QUARKUS_SHUTDOWN_TIMEOUT`). And the image is not pulled from any
registry: `kind load` uploads it to the node, which is why it says `imagePullPolicy: Never`.

## 2. Predict

Answer on paper:

1. If a replica of int-rest requests 200 millicores of CPU (one fifth of a core) and is using 300, what percentage is it
   at? With a target of 50%, how many replicas would the HPA want?
2. If the replica does **not** request CPU, what does the HPA calculate the percentage against?
3. The load lasts two minutes and then ends. When do you think the HPA removes the extra replicas?

Look at how it is today (it takes a few minutes the first time: it pulls images and builds):

```bash
./verifica inicio
```

The first checks come out green: the cluster is created, int-rest is built with `camel export` and order 1001
reaches the cluster's API. Then the one for the requested CPU comes out red, and Kubernetes **rejects** the HPA: it is
missing the maximum number of replicas.

## 3. Request, ceiling and target

### TODO 1 and 2: what each replica requests, and its ceiling

In `mio/k8s/int-rest.yaml`, inside the container, add `resources`:

```yaml
resources:
  requests:            # lo que la copia RESERVA en el nodo
    cpu: 200m
    memory: 256Mi
  limits:              # el techo
    cpu: "1"
    memory: 512Mi
```

| | What it means | If it goes over |
|---|---|---|
| `requests` | Kubernetes only places the replica on a node that has that much free, and the HPA measures against it | — |
| `limits.cpu` | It is not given more than one core | It is throttled |
| `limits.memory` | It is not given more than 512 MiB | Kubernetes restarts the replica |

`200m` reads "200 millicores": one core is `1000m`. With `200m` requested, a replica that uses `100m` is at 50%.

### TODO 3 and 4: the HPA

In `mio/k8s/hpa.yaml`, below `scaleTargetRef`:

```yaml
minReplicas: 1
maxReplicas: 4
metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 50
```

Every 15 seconds (the period Kubernetes comes with by default; it can be configured), the HPA does this calculation,
and keeps the result between the minimum and the maximum:

```
copias que quiere = copias que hay × (CPU promedio ÷ meta), redondeado hacia arriba
ejemplo: 1 copia al 150 % con meta de 50 % → quiere 3
```

The maximum is not a detail: it is what keeps a large load from eating the whole node. And the `behavior.scaleDown`
that the file already includes makes it scale down after 30 seconds without load, so that you can see it in the
course. By default it waits 5 minutes, so as not to go up and down all the time.

## 4. Break one on purpose

With your files finished, remove `cpu: 200m` from `requests` (leave the memory) and run `./verifica`.

First, predict: the HPA is written correctly and Kubernetes accepts it. Does it scale?

No. `verifica` says that int-rest does not request CPU and then that the HPA cannot read it: if you ask it yourself
(`kubectl get hpa`), the target shows as `<unknown>/50%`. The HPA is not broken: it has nothing to measure against.
**A percentage without a base is not a number.** Put the line back.

## 5. Check

```bash
./verifica
```

`verifica` creates the cluster, builds int-rest, deploys it with **your** files, sends order 1001 as a control and
then a load of 150 orders per second for two minutes. It counts the **ready** replicas of the Deployment (not what the
route says), checks that the Service spread the load among them and that the API answered all the orders. Then it
waits for them to come down. On the machine in `CAPACIDAD.md` it took about 4 minutes; yours will tell. It is done
when you see the green checks:

```
✅ Clúster kind «ni-una-orden-perdida» creado (Kubernetes …)
✅ int-rest salió de camel export a una imagen de Camel Quarkus, y está en el clúster junto con la API
✅ Kubernetes aceptó mio/k8s/int-rest.yaml (el Deployment y el Service)
✅ Control: la orden 1001 pasó por int-rest y la API la registró una vez (la ruta no cambió ni una letra)
✅ Cada copia de int-rest reserva CPU: requests.cpu=200m
✅ Kubernetes aceptó mio/k8s/hpa.yaml
✅ El HPA escala a int-rest entre 1 y 4 copias, con meta de CPU de 50 %
✅ El HPA ya lee la CPU de int-rest: (…) % de lo reservado, sin carga
✅ Con carga, el HPA subió int-rest de 1 a (…) copias listas (máximo 4)
✅ (…) copias de int-rest atendieron órdenes de la carga: el Service repartió
✅ Las (…) órdenes de la carga llegaron a la API y cada una tuvo respuesta: ninguna se perdió mientras crecía
✅ Sin carga, int-rest volvió a 1 copia(s) en (…) s
```

In our runs the HPA went up to 4 replicas in one and to 3 in another; that is why `verifica` asks for "more than the
minimum" and not an exact number. How many it goes up to depends on your machine.

While it runs, you can watch the HPA work in another terminal. `verifica` stores the cluster's key in a temporary
file, so as not to touch your `kubectl` configuration; you ask kind for it:

```bash
kubectl --kubeconfig <(kind get kubeconfig --name ni-una-orden-perdida) get hpa,deploy -w
```

**Not working?** `./verifica solucion` tests ours, and `diff -r mio solucion` tells you where they differ.

| What you see | What to do |
|---|---|
| `No encuentro «kind»` or `«kubectl»` | Install it with the link in the table under "Before you start" |
| `kind no pudo crear el clúster` | Check that Docker is up and has free memory (see `CAPACIDAD.md`) |
| `No se pudo construir int-rest` | The first build downloads dependencies: check your connection. The lines that follow say where it stopped |
| `Kubernetes rechazó mio/k8s/int-rest.yaml` | It is a YAML or naming error: the message from Kubernetes comes out below |
| `int-rest no reserva CPU (resources.requests.cpu)` | TODO 1 is missing, or `resources` ended up outside the container |
| `Kubernetes rechazó mio/k8s/hpa.yaml (TODO 3 y 4)` | `maxReplicas` is missing or `metrics` is indented wrong |
| `El HPA debe escalar a int-rest … con un máximo mayor que el mínimo` | Check `minReplicas`, `maxReplicas` and that the target is `type: Utilization` |
| `El HPA no logra leer la CPU de int-rest` | Almost always, `requests.cpu` is missing. Kubernetes says why in the same message |
| `Con carga, int-rest se quedó en 1 copia(s)` | The target is not a percentage of what is requested, or it is so high that it is never reached |
| `Sin carga, int-rest sigue en … copias` | You changed the `behavior.scaleDown`: put it back the way it came |

---

## What this teaches beyond the course

**The same route, another place.** int-rest went from the lab folder to a Camel Quarkus image on Kubernetes without
changing its YAML. What changed is how it is packaged and who decides how many replicas there are.

**Scaling by itself needs a measure and a base.** The HPA knows nothing about orders: it knows about CPU against what
is requested. If the request is wrong, so is the scaling decision.

**The maximum protects the neighbor.** Without `maxReplicas`, a large load adds replicas until the node cannot take any
more. And remember lesson 24: if int-bd scaled by itself, the cap toward the ERP would have to be worked out with the
**maximum** number of replicas, not with today's.

**Scaling without losing is designed, and tested.** In this run, all the orders in the load got a response while new
replicas were appearing: the Service does not send new work to a replica that does not yet say it is ready. When a replica is
removed, Kubernetes gives it a grace window and Quarkus tries to finish what it was carrying, but `verifica` does not
test that moment with orders in flight. To be sure that nothing is cut off when scaling down, you have to test it
under load, with the real timings of each service.

## Exercise: the supermarket checkout lanes

1. On paper: with `requests.cpu: 200m`, a target of 50% and a maximum of 4, how many replicas does the HPA want if 2
   replicas are at 180%? And if they are at 20%?
2. Change the target to 80% and run `./verifica`. Did it go up to fewer replicas? How long did it take to come down?
3. If tomorrow you wanted to scale int-bd this way, what would you have to recalculate from lesson 24, and with which
   number: the minimum or the maximum number of replicas?

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [A practice Kubernetes with kind, with pinned versions, and a cluster that is deleted at the end](../decisiones/35-kubernetes-de-practica-con-kind.md)
- [int-rest goes to Kubernetes, exported with camel export to Camel Quarkus, without changing its route](../decisiones/36-int-rest-exportado-con-camel-export.md)
- [The HPA scales by CPU, measured as a percentage of what each replica requests](../decisiones/37-el-hpa-escala-por-cpu-reservada.md)

## Review

1. What does an HPA do, and what does it measure?
2. What is the difference between `requests` and `limits`?
3. What is `maxReplicas` for?
4. Why does `verifica` count the ready replicas of the Deployment and not what the route says?
5. **The closing question:** the HPA says `<unknown>/50%`. Explain why, and what the Deployment is missing.

**Next:** in lesson 32 the same int-rest is also compiled to native, and you measure on your machine what changes:
how long it takes to build, to start and how much memory it uses.
