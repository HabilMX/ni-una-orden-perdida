# Kubernetes en una página

**Para qué te sirve aquí:** la práctica corre en tu laptop con Docker Compose, pero en una empresa las integraciones
suelen correr en Kubernetes. Lo que aprendes con Compose se traduce casi pieza por pieza, y en la lección 31 das ese paso.

## Lo que ya conoces de la práctica

En la lección 12 pediste tres copias de int-bd con `deploy.replicas: 3`, y el router las siguió llamando por un solo
nombre: `int-bd`. Eso, con otros nombres, es casi todo Kubernetes.

## Una imagen de la vida diaria

Kubernetes es el **gerente de turno** de una cocina. Tú le dices «quiero tres cocineros de tacos siempre» y él se
encarga: si uno se va, llama a otro; si llega mucha gente, puede llamar a más; y los meseros piden «tacos» sin saber
qué cocinero los hace.

## El concepto

**Kubernetes** administra contenedores en varias máquinas a la vez. Tú le describes en YAML **cómo quieres que esté**
el sistema, y él trabaja todo el tiempo para que esté así.

| En Compose | En Kubernetes | Qué es |
|---|---|---|
| un servicio | un **Deployment** | «quiero N copias de este contenedor» |
| una copia | un **Pod** | la unidad que corre: uno o más contenedores juntos |
| el nombre del servicio | un **Service** | un nombre fijo que reparte entre las copias |
| `deploy.replicas` | `replicas` del Deployment | cuántas copias |
| una carpeta montada | un **ConfigMap** montado | la configuración que el contenedor ve como archivos |

Además, Kubernetes puede **escalar solo**: con un HorizontalPodAutoscaler sube o baja el número de copias según la
carga. Ahí es donde importa que un servicio arranque rápido (ficha [nativo](07-nativo.md)).

Un detalle que ya mediste: un Service elige una copia para cada **conexión** nueva, igual que el DNS de Docker. Una
conexión que nunca se cierra nunca se reparte (ficha [15](../../decisiones/15-repartir-por-conexion.md)).

## Lo que necesitas saber para la práctica

- No necesitas Kubernetes para ninguna lección de la 00 a la 30.
- En la 31 llevas una integración a un proyecto de Camel Quarkus con su Deployment.
- Las copias no deben guardar estado propio en su disco: si una muere, otra toma su lugar. Por eso el estado de las
  órdenes vive en una base de datos (lección 15).

## Compruébalo

Pregunta: en Compose tienes `int-bd` con `deploy.replicas: 3`. ¿Qué dos objetos de Kubernetes necesitas para que el
router la siga llamando `int-bd` y haya tres copias?

Respuesta: un **Deployment** con `replicas: 3` y un **Service** llamado `int-bd` que apunte a sus Pods.

## Si quieres ir más lejos

- Kubernetes, conceptos básicos (en español): https://kubernetes.io/es/docs/concepts/
- Kubernetes, Services: https://kubernetes.io/docs/concepts/services-networking/service/
- Kubernetes, escalado horizontal: https://kubernetes.io/docs/concepts/workloads/autoscaling/horizontal-pod-autoscale/
