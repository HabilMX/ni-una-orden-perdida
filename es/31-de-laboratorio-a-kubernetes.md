# Lección 31 — De laboratorio a proyecto, en Kubernetes

> **Apéndice opcional.** La práctica terminó en la 30. Esta lección y la 32 son para quien trabaja con Kubernetes o
> quiere ver hasta dónde llega lo que hiciste. Se pueden **leer** sin correrlas.
>
> **Lo que vas a lograr:** llevar int-rest, el mismo de la 12, de la carpeta del laboratorio a una imagen de Camel
> Quarkus, correrlo en un Kubernetes de práctica y hacer que **sus copias suban solas** cuando llegan muchas órdenes,
> y bajen cuando se acaban.

**Antes de empezar, revisa tu equipo.** Lo medimos en una Mac con Apple M5 y Docker con 6 CPU y 16 GiB: la 31 pidió
unos 1.4 GiB de memoria más de lo que ya se usaba, 2.7 núcleos en el pico y unos 4.4 GB de imágenes. La 32 pide
bastante más. El detalle, y cómo se midió, está en
[`lecciones/31-de-laboratorio-a-kubernetes/CAPACIDAD.md`](../lecciones/31-de-laboratorio-a-kubernetes/CAPACIDAD.md).
Son cifras de una sola máquina: en la tuya pueden ser otras.

**Necesitas además:** la lección 12 terminada, y dos programas que hasta hoy no usaste:

| Programa | Para qué | Dónde se instala |
|---|---|---|
| `kind` | Un Kubernetes de práctica que corre dentro de Docker | [kind.sigs.k8s.io, «Installation»](https://kind.sigs.k8s.io/docs/user/quick-start/#installation) |
| `kubectl` | Hablar con ese Kubernetes | [kubernetes.io, «Install Tools»](https://kubernetes.io/docs/tasks/tools/) |

Esta lección **no** usa el ambiente de la 00: crea su propio clúster, con el nombre `ni-una-orden-perdida`, y lo borra
al terminar, salga bien o mal. Si no conoces Kubernetes, lee antes
[`conceptos/antes-de-empezar/08-kubernetes.md`](../conceptos/antes-de-empezar/08-kubernetes.md).

## Lo nuevo de hoy, en una frase

En la 12 pusiste `replicas: 3` a mano: tres copias siempre, haya mucho trabajo o nada. Hoy le dejas esa decisión a
Kubernetes, con un **HPA** (*HorizontalPodAutoscaler*): una pieza que mira cuánta CPU usan las copias de int-rest y
pone más cuando se acercan a su meta, o quita cuando sobran. Para que pueda medir, cada copia tiene que **reservar**
CPU: el HPA mide en porcentaje de lo reservado.

**La imagen:** las cajas de un supermercado. Con poca gente hay una abierta. El gerente mira las filas y, cuando cada
cajera atiende más de lo que le toca, abre otra, hasta las cuatro que tiene. Cuando se vacía la tienda, las va
cerrando. Pero para saber si una cajera «va al 150 %», primero hay que decir cuánto le toca a cada una. Eso es lo que
se reserva.

---

## 1. Prepara tu carpeta

```bash
cd lecciones/31-de-laboratorio-a-kubernetes
cp -R inicio mio
```

Antes de escribir, lee lo que ya está hecho:

| Archivo | Qué es | ¿Lo escribes? |
|---|---|---|
| `int-rest/int-rest.camel.yaml` | La ruta de int-rest de la 12, **sin cambiar una letra**, con sus mapeos y sus casos dorados | No |
| `Dockerfile` | De la carpeta a una imagen, en etapas: `camel export` la vuelve un proyecto de Camel Quarkus, se compila y queda una imagen para la JVM (y otra nativa, para la 32). Todo dentro de contenedores: no instalas Java ni Maven | No, léelo |
| `cluster/` | El clúster de kind, metrics-server (lo que mide la CPU de cada copia), la API de pedidos y un cliente que manda la carga | No |
| `mio/k8s/int-rest.yaml` | El Deployment (las copias) y el Service (el nombre fijo que reparte entre ellas) | **Sí, TODO 1 y 2** |
| `mio/k8s/hpa.yaml` | El HPA | **Sí, TODO 3 y 4** |

Fíjate en dos cosas del Dockerfile y del Deployment. La configuración no va en un `application.properties`: lo que se
decide al compilar va al `pom.xml` y lo que se decide al correr va en variables de entorno del Deployment
(`QUARKUS_SHUTDOWN_TIMEOUT`). Y la imagen no se baja de ningún registro: `kind load` la sube al nodo, por eso dice
`imagePullPolicy: Never`.

## 2. Predice

Contesta en un papel:

1. Si una copia de int-rest reserva 200 milinúcleos de CPU (la quinta parte de un núcleo) y está usando 300, ¿a qué
   porcentaje va? Con una meta de 50 %, ¿cuántas copias querría el HPA?
2. Si la copia **no** reserva CPU, ¿contra qué calcula el HPA el porcentaje?
3. La carga dura dos minutos y luego se acaba. ¿Cuándo crees que el HPA quita las copias de más?

Mira cómo está hoy (tarda unos minutos la primera vez: baja imágenes y construye):

```bash
./verifica inicio
```

Las primeras marcas salen verdes: el clúster se crea, int-rest se construye con `camel export` y la orden 1001 llega a
la API del clúster. Luego sale en rojo la de la CPU reservada, y Kubernetes **rechaza** el HPA: le falta el máximo de
copias.

## 3. Reserva, techo y meta

### TODO 1 y 2: lo que reserva cada copia, y su techo

En `mio/k8s/int-rest.yaml`, dentro del contenedor, agrega `resources`:

```yaml
resources:
  requests:            # lo que la copia RESERVA en el nodo
    cpu: 200m
    memory: 256Mi
  limits:              # el techo
    cpu: "1"
    memory: 512Mi
```

| | Qué significa | Si se pasa |
|---|---|---|
| `requests` | Kubernetes solo pone la copia en un nodo que tenga eso libre, y el HPA mide contra eso | — |
| `limits.cpu` | Más de un núcleo no se le da | Se frena |
| `limits.memory` | Más de 512 MiB no se le da | Kubernetes reinicia la copia |

`200m` se lee «200 milinúcleos»: un núcleo son `1000m`. Con `200m` reservados, una copia que usa `100m` va al 50 %.

### TODO 3 y 4: el HPA

En `mio/k8s/hpa.yaml`, debajo de `scaleTargetRef`:

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

Cada 15 segundos (el periodo que trae Kubernetes por omisión; se puede configurar), el HPA hace esta cuenta, y la
deja entre el mínimo y el máximo:

```
copias que quiere = copias que hay × (CPU promedio ÷ meta), redondeado hacia arriba
ejemplo: 1 copia al 150 % con meta de 50 % → quiere 3
```

El máximo no es un detalle: es lo que impide que una carga grande se coma el nodo entero. Y el `behavior.scaleDown`
que ya trae el archivo hace que baje a los 30 segundos sin carga, para que lo veas en la práctica. Por omisión espera
5 minutos, para no subir y bajar a cada rato.

## 4. Rompe uno a propósito

Con tus archivos terminados, quita `cpu: 200m` de `requests` (deja la memoria) y corre `./verifica`.

Antes, predice: el HPA está bien escrito y Kubernetes lo acepta. ¿Escala?

No. La verifica dice que int-rest no reserva CPU y luego que el HPA no logra leerla: si le preguntas tú
(`kubectl get hpa`), la meta sale como `<unknown>/50%`. El HPA no está roto: le falta contra qué medir. **Un
porcentaje sin base no es un número.** Regresa la línea.

## 5. Comprueba

```bash
./verifica
```

`verifica` crea el clúster, construye int-rest, lo despliega con **tus** archivos, manda la orden 1001 como control y
luego una carga de 150 órdenes por segundo durante dos minutos. Cuenta las copias **listas** del Deployment (no lo que
dice la ruta), revisa que el Service repartió entre ellas y que la API contestó todas las órdenes. Después espera a que
bajen. En la máquina de `CAPACIDAD.md` tardó unos 4 minutos; la tuya dirá. Está terminada cuando ves las marcas verdes:

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

En nuestras corridas el HPA subió a 4 copias en una y a 3 en otra; por eso la verifica pide «más que el mínimo» y no
un número exacto. Cuántas sube depende de tu máquina.

Mientras corre, en otra terminal puedes mirar al HPA trabajar. La verifica guarda la llave del clúster en un archivo
temporal, para no tocar tu configuración de `kubectl`; se la pides a kind:

```bash
kubectl --kubeconfig <(kind get kubeconfig --name ni-una-orden-perdida) get hpa,deploy -w
```

**¿No te corre?** `./verifica solucion` prueba la nuestra, y `diff -r mio solucion` te dice en qué son distintas.

| Lo que ves | Qué haces |
|---|---|
| `No encuentro «kind»` o `«kubectl»` | Instálalo con la liga de la tabla de «Antes de empezar» |
| `kind no pudo crear el clúster` | Revisa que Docker esté arriba y tenga memoria libre (ver `CAPACIDAD.md`) |
| `No se pudo construir int-rest` | La primera construcción baja dependencias: revisa tu conexión. Las líneas que siguen dicen dónde se detuvo |
| `Kubernetes rechazó mio/k8s/int-rest.yaml` | Es un error de YAML o de nombres: el mensaje de Kubernetes sale debajo |
| `int-rest no reserva CPU (resources.requests.cpu)` | Falta el TODO 1, o `resources` quedó fuera del contenedor |
| `Kubernetes rechazó mio/k8s/hpa.yaml (TODO 3 y 4)` | Falta `maxReplicas` o `metrics` está mal sangrado |
| `El HPA debe escalar a int-rest … con un máximo mayor que el mínimo` | Revisa `minReplicas`, `maxReplicas` y que la meta sea `type: Utilization` |
| `El HPA no logra leer la CPU de int-rest` | Casi siempre, falta `requests.cpu`. Kubernetes dice por qué en el mismo mensaje |
| `Con carga, int-rest se quedó en 1 copia(s)` | La meta no es un porcentaje de lo reservado, o es tan alta que nunca se alcanza |
| `Sin carga, int-rest sigue en … copias` | Cambiaste el `behavior.scaleDown`: regrésalo como venía |

---

## Lo que esto enseña fuera de la práctica

**La misma ruta, otro lugar.** int-rest pasó de la carpeta del laboratorio a una imagen de Camel Quarkus en
Kubernetes sin cambiar su YAML. Lo que cambió es cómo se empaqueta y quién decide cuántas copias hay.

**Escalar solo necesita una medida y una base.** El HPA no sabe nada de órdenes: sabe de CPU contra lo reservado. Si
la reserva está mal, la decisión de escalar también.

**El máximo protege al vecino.** Sin `maxReplicas`, una carga grande pone copias hasta que el nodo no aguanta. Y
recuerda la 24: si int-bd escalara solo, el tope hacia el ERP tendría que pensarse con el **máximo** de copias, no con
las de hoy.

**Escalar sin perder se diseña, y se prueba.** En esta corrida, todas las órdenes de la carga tuvieron respuesta
mientras aparecían copias nuevas: el Service no le manda trabajo nuevo a una copia que todavía no dice que está lista.
Al quitar una copia, Kubernetes le da una ventana de gracia y Quarkus intenta terminar lo que traía, pero la verifica
no prueba ese momento con órdenes en el aire. Para estar seguro de que no se corta nada al bajar, hay que probarlo con
carga, con los tiempos reales de cada servicio.

## Ejercicio: las cajas del supermercado

1. En papel: con `requests.cpu: 200m`, meta de 50 % y máximo 4, ¿cuántas copias quiere el HPA si 2 copias van al
   180 %? ¿Y si van al 20 %?
2. Cambia la meta a 80 % y corre `./verifica`. ¿Subió a menos copias? ¿Cuánto tardó en bajar?
3. Si mañana quisieras escalar int-bd así, ¿qué tendrías que recalcular de la lección 24, y con qué número: el mínimo
   o el máximo de copias?

---

## Por qué se hizo así

Lo que decidimos en esta lección, con lo que descartamos y qué nos haría cambiar de idea:

- [Kubernetes de práctica con kind, con versiones fijas, y un clúster que se borra al terminar](../decisiones/35-kubernetes-de-practica-con-kind.md)
- [A Kubernetes va int-rest, exportado con camel export a Camel Quarkus, sin cambiarle la ruta](../decisiones/36-int-rest-exportado-con-camel-export.md)
- [El HPA escala por CPU, medida como porcentaje de lo que cada copia reserva](../decisiones/37-el-hpa-escala-por-cpu-reservada.md)

## Repaso

1. ¿Qué hace un HPA, y qué mide?
2. ¿Qué diferencia hay entre `requests` y `limits`?
3. ¿Para qué sirve `maxReplicas`?
4. ¿Por qué la verifica cuenta las copias listas del Deployment y no lo que dice la ruta?
5. **La pregunta de cierre:** el HPA dice `<unknown>/50%`. Explica por qué, y qué le falta al Deployment.

**Siguiente:** en la lección 32 el mismo int-rest se compila también a nativo, y mides en tu máquina qué cambia:
cuánto tarda en construirse, en arrancar y cuánta memoria usa.
