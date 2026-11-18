# Not One Order Lost

**Por Dorian Chávez, fundador de Hábil y arquitecto de integración.**

**Who it is for:** anyone who already uses the terminal and git, reads JSON and knows basic SQL, and wants to learn
systems integration. You do not need to know Java or Camel: the routes are YAML and you learn them here.

**What you will know by the end:** how to make sure, with your own hands, that no order is lost, that everything
is traceable, that a retry does not create a duplicate and that a destination that is down does not take the others with it.

**What you need before you start:** terminal and git, reading JSON and basic SQL, and Docker with Compose. Of Docker,
`docker compose up` and `down` are enough; the rest comes written out. It works on Linux, macOS and Windows with WSL.

## The project

A made-up store sells, and every order has to reach three systems that look nothing alike: a REST API, an ERP on
MariaDB and a trading partner that receives EDI X12 documents over SFTP. Everything runs on your machine with Docker.
It is an open, free course on systems integration with [Apache Camel](https://camel.apache.org/).

## The lessons

There are 31 lessons, from 00 to 30, all on Docker Compose, plus an optional read-only appendix. They are done in
order: each one starts from what the previous one left. Start with [00](00-ambiente.md), which brings up the
environment.

### Part 1 — Make it arrive

| # | Lesson |
|---|---|
| 00 | [Environment ready](00-ambiente.md) |
| 01 | [My first route runs](01-mi-primera-ruta.md) |
| 02 | [The order reaches the API](02-la-orden-llega-a-la-api.md) |
| 03 | [Translating the order](03-traducir-la-orden.md) |
| 04 | [To the ERP](04-al-erp.md) |
| 05 | [All or nothing](05-todo-o-nada.md) |
| 06 | [Building the EDI](06-armar-el-edi.md) |
| 07 | [To the partner](07-al-socio.md) |
| 08 | [The router](08-el-router.md) |
| 09 | [The thread between services](09-el-hilo-entre-servicios.md) |
| 10 | [All the channels](10-todos-los-canales.md) |
| 11 | [Not everything goes to everyone](11-no-todo-va-a-todos.md) |
| 12 | [Growing in replicas](12-crecer-en-replicas.md) |

### Part 2 — Make it move forward

| # | Lesson |
|---|---|
| 13 | [The shipment arrives](13-llega-el-embarque.md) |
| 14 | [One file, several orders](14-un-archivo-varias-ordenes.md) |
| 15 | [State lives outside the process](15-el-estado-vive-fuera-del-proceso.md) |
| 16 | [The delivery arrives](16-llega-la-entrega.md) |
| 17 | [The delivery arrived before the tracking number](17-la-entrega-llego-antes-que-la-guia.md) |

### Part 3 — Make it not fail

| # | Lesson |
|---|---|
| 18 | [When are two messages the same one?](18-cuando-dos-mensajes-son-el-mismo.md) |
| 19 | [The destination does not answer](19-el-destino-no-contesta.md) |
| 20 | [Accepted, rejected or unknown](20-aceptado-rechazado-o-no-se-sabe.md) |
| 21 | [Sent is not confirmed](21-enviado-no-es-confirmado.md) |
| 22 | [Taking a channel down](22-tirar-un-canal.md) |
| 23 | [Reprocessing what failed](23-reprocesar-lo-que-fallo.md) |
| 24 | [Do not knock the destination down](24-no-tumbar-al-destino.md) |
| 25 | [Waiting for the channels](25-esperar-a-los-canales.md) |
| 26 | [Following an order](26-seguir-una-orden.md) |
| 27 | [What the audit trail must not show](27-lo-que-el-rastro-no-debe-mostrar.md) |

### Part 4 — Make it scale

| # | Lesson |
|---|---|
| 28 | [One topic, one group per integration](28-un-topico-un-grupo-por-integracion.md) |
| 29 | [Do not lose when consuming](29-no-perder-al-consumir.md) |
| 30 | [Do not lose when publishing](30-no-perder-al-publicar.md) |

### Optional appendix

The course ends at lesson 30. The appendix takes one integration to Kubernetes and compares it on the JVM and as a
native executable; it asks for a machine with more memory and disk, and says so at the start.

| # | Lesson |
|---|---|
| 31 | [From lab to project, on Kubernetes](31-de-laboratorio-a-kubernetes.md) |
| 32 | [JVM versus native, measured](32-jvm-contra-nativo.md) |

The design decisions, with their reasons, are in [`decisiones/`](../decisiones/README.md).
