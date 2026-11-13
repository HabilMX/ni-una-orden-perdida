# Qué pide el apéndice a tu equipo (medido)

Cifras medidas el **12 de noviembre de 2026** corriendo los `verifica` de la 31 y la 32 con `solucion/`, en esta
máquina. Son de esta máquina: en la tuya pueden ser otras. Sirven para decidir si tu equipo alcanza, no como meta.

**Máquina:** Mac con Apple M5 (arm64), macOS 27.0.1. Docker 29.2.1 dentro de colima 0.10.3, con una máquina virtual
de **6 CPU, 16 GiB de memoria y 80 GB de disco**. kind v0.33.0 (Kubernetes v1.37.0), kubectl v1.37.2, Camel 4.22.1,
Quarkus 3.40.1.2, Mandrel 25.0.5.

## En corto

| | Lección 31 (kind + HPA) | Lección 32 (compilar a nativo) |
|---|---|---|
| **Memoria** | ~1.4 GiB más de lo que ya usabas (pico, con carga y 3 copias) | ~4.7 GiB más de lo que ya usabas (pico, al compilar) |
| **CPU** | ~2.7 núcleos en el pico de la carga; ~0.1 núcleo con el clúster quieto | Todos los que haya: el compilador usó ~4.6 de 6 |
| **Disco** | ~4.4 GB de imágenes y hasta ~2.6 GB de caché, más ~2.2 GB mientras el clúster está arriba | ~0.4 GB más que la 31 (la imagen nativa y su caché) |
| **Tiempo** | ~4 minutos el `verifica` (con las imágenes ya construidas) | ~7 minutos el `verifica` (construye las dos desde cero) |

Para el recuadro, en una frase: **lo medido sugiere que hacen falta unos 6 GiB de memoria libres para Docker (además
de lo que ya uses), 4 núcleos y unos 12 GB de disco libres**. La parte que más pide es la compilación nativa de la 32.
Es un cálculo a partir de estas cifras: no lo probamos en una máquina con menos.

## Cómo se midió

| Qué | Con qué |
|---|---|
| Memoria de la máquina | `free -m` dentro de la máquina virtual de Docker (`colima ssh`), cada 2 s. Se reporta el pico menos lo que ya se usaba al empezar |
| CPU y memoria del clúster | `docker stats` del contenedor que **es** el nodo de kind (`ni-una-orden-perdida-control-plane`), cada 3 s. Incluye todo lo que corre en el clúster. 100 % = un núcleo |
| Memoria y CPU de la compilación nativa | Lo que reporta el propio compilador al terminar (`Peak RSS` y `CPU load`). `docker stats` no la ve: corre dentro de BuildKit, no como contenedor |
| Disco | `docker images` (tamaño de cada imagen), `docker buildx du` (el caché de Maven), `docker system df` antes y después, y `du -sh /var/lib/containerd` dentro del nodo de kind |
| Tiempo | El reloj de cada `verifica`, de principio a fin |

## Lección 31, con detalle

| Medida | Cifra |
|---|---|
| Memoria usada de la máquina virtual: al empezar → pico | 2 609 MiB → 3 998 MiB (+1 389 MiB) |
| Nodo de kind, memoria: quieto con todo listo → pico con carga | 861 MiB → 1 395 MiB |
| Nodo de kind, CPU: quieto → pico con carga | 12 % → 267 % (unos 2.7 núcleos) |
| Copias de int-rest que subió el HPA con 150 órdenes por segundo | de 1 a 3 en una corrida y de 1 a 4 en otra (máximo 4); las cifras de memoria y CPU de esta tabla son de la de 3 |
| Disco dentro del nodo (`/var/lib/containerd`) mientras el clúster está arriba | 2.2 GB (se libera al borrar el clúster) |
| `verifica` completo, con las imágenes ya construidas | 248 s y 249 s (dos corridas) |

Imágenes que se quedan en tu Docker después de la 31 (además de las de las lecciones 00 a 30):

| Imagen | Tamaño (`docker images`) | Para qué |
|---|---|---|
| `kindest/node:v1.37.0` | 1.30 GB | El nodo del clúster |
| `quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:25.0.5.0-Final-java25` | 1.92 GB | Compilar (JVM y nativo) |
| `ni-una-orden-perdida/int-rest:jvm` | 655 MB | int-rest para la JVM (comparte capas con su base) |
| `ni-una-orden-perdida/api-demo:1.5.0` | 529 MB | La API dentro del clúster (comparte capas con la de la lección 00) |
| `fortio/fortio:1.76.0` | 25 MB | El cliente que manda la carga |
| Caché de construcción (incluye el de Maven, 157 MB) | hasta ~2.6 GB | Que la segunda construcción no baje ni repita nada |

¿Por qué la 32 da otro tamaño para la misma imagen? Son dos herramientas que no miden lo mismo: `docker images`
suma lo que la imagen ocupa en tu disco, con sus capas ya desempacadas, y `docker image inspect` (el que usa el
`medir` de la 32) da el tamaño de su contenido. Las dos son correctas; para comparar JVM contra nativo, usa siempre
la misma.

## Lección 32, con detalle

| Medida | Cifra |
|---|---|
| Memoria usada de la máquina virtual: al empezar → pico | 2 583 MiB → 7 410 MiB (+4 827 MiB) |
| Compilación nativa: lo que reporta el compilador | Peak RSS 4.58 GB · CPU load 4.57 (de 6 núcleos) |
| Techo de memoria del compilador (`MEMORIA_NATIVO` del Dockerfile) | 5g |
| Construir la imagen JVM / la nativa, desde cero | 122 s / 202 s |
| Imagen nativa (`ni-una-orden-perdida/int-rest:nativo`) | 161 MB (`docker images`) |
| `verifica` completo (las dos construcciones desde cero, más las mediciones) | 407 s |

Las cifras de JVM contra nativo (arranque y memoria del servicio) no van aquí: esas las mide el alumno en la 32.

## Lo que no se midió

- **En x86:** estas cifras son de arm64. El workflow `.github/workflows/apendice.yml` deja las mismas medidas de una
  máquina x86 de GitHub en el resumen de su corrida; todavía no se ha corrido.
- **Con menos memoria:** no probamos qué pasa con una máquina virtual de 8 GiB. Con el techo de 5g, el compilador
  está diseñado para fallar con un mensaje claro antes que dejar sin memoria a lo demás; si eso pasa, se puede bajar
  `MEMORIA_NATIVO` (más lento).
- **Con otras cosas corriendo:** durante las mediciones estaba arriba el ambiente de la lección 00 (Kafka, el ERP, la
  base de estado, el SFTP y la API) y otro clúster kind ajeno a la práctica. Ya estaba en la memoria «al empezar».
