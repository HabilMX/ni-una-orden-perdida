# El HPA escala por CPU, medida como porcentaje de lo que cada copia reserva, y baja rápido en la práctica

> **Dónde aparece:** lección 31 (apéndice opcional).

## El problema

En la lección 12, el número de copias de int-bd lo escribías tú (`deploy.replicas: 3`). Si la carga sube a media
noche, nadie está ahí para cambiar el 3 por un 5, y si baja, las copias de más siguen ocupando memoria. Kubernetes
puede decidirlo solo con un HorizontalPodAutoscaler (HPA), pero hay que decirle **qué mirar**, **contra qué meta** y
**entre cuántas copias**. Y si una de esas tres cosas está mal, el HPA no avisa con un error: simplemente no escala.

## Qué decidimos

- **La métrica es la CPU**, con `type: Utilization`: el HPA compara el promedio de CPU de las copias contra un
  porcentaje de lo que cada una **reserva** (`resources.requests.cpu`). Por eso el Deployment reserva `200m` (la quinta
  parte de un núcleo) y pone su techo en `limits` (`1` núcleo, `512Mi`).
- **La meta es 50 %**: el HPA busca que cada copia use, en promedio, la mitad de lo que reservó. La otra mitad es
  holgura para el pico mientras llega la copia nueva.
- **Entre 1 y 4 copias** (`minReplicas`, `maxReplicas`). El máximo es un techo para no comerse el nodo.
- **Baja rápido en la práctica:** `behavior.scaleDown.stabilizationWindowSeconds: 30`. Por omisión espera 5 minutos
  sin carga antes de quitar copias; aquí son 30 s, para que el alumno lo vea bajar sin esperar tanto.
- **Al quitar una copia, se cierra con orden:** `QUARKUS_SHUTDOWN_TIMEOUT=10s` en el Deployment. La copia deja de
  aceptar órdenes nuevas y espera a que terminen las que traía.
- **La carga del `verifica` abre una conexión por orden** (`-keepalive=false`). Un Service reparte por conexión, como
  el DNS de Docker (ficha 15): con conexiones que no se cierran, las copias nuevas no recibirían nada.
- **El `verifica` mide el destino:** cuenta las copias **listas** del Deployment (no lo que dice la ruta ni lo que
  quiere el HPA), cuántas copias atendieron órdenes según su bitácora, y que la API registró cada orden de la carga
  que el cliente dice que mandó.

## Por qué

- **Sin `requests.cpu`, el HPA no tiene contra qué calcular el porcentaje** y se queda en `<unknown>`
  («missing request for cpu»). Es el error más común al empezar, y por eso es el primer TODO de la lección.
- **La regla del HPA es una proporción:** copias que quiere = copias que hay × (uso promedio ÷ meta), redondeado
  hacia arriba y dentro del mínimo y el máximo. Con una meta en porcentaje de lo reservado, la misma regla sirve para
  una copia chica o una grande.
- **La CPU es la métrica que ya trae un clúster con metrics-server.** Escalar por órdenes por segundo o por
  mensajes en espera pide un adaptador de métricas más, otra pieza para la misma lección.
- **Bajar rápido en producción es un riesgo:** si la carga va y viene, las copias suben y bajan a cada rato. Los
  5 minutos por omisión existen por eso. En la práctica lo acortamos para verlo, y lo decimos en el archivo.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Escalar por memoria | En la JVM, la memoria sube con la carga y suele no bajar al terminar: el HPA podría no volver a bajar |
| Escalar por órdenes por segundo (métrica propia) | Pide exponer la métrica y un adaptador (Prometheus Adapter o KEDA): más piezas para la misma idea |
| Meta de 80 % o más | Deja poca holgura: mientras la copia nueva arranca, las que hay se saturan |
| Sin máximo, o un máximo alto | En un nodo de laptop, cada copia de la JVM reserva memoria; muchas copias tumbarían el nodo |
| Dejar la ventana de bajada por omisión (5 minutos) | El `verifica` tardaría 5 minutos más en ver la bajada; en producción sí se deja |

## Qué nos haría reabrirla

- Que la carga real de una integración no sea de CPU sino de espera (un destino lento): la CPU no subiría y habría
  que escalar por órdenes en espera o por tiempo de respuesta.
- Que se lleve el nativo al clúster: arranca más rápido, y se podría bajar la holgura (subir la meta).
- Que el clúster tenga varios nodos: entonces importan también `requests.memory` para que el planificador acomode
  las copias, y un PodDisruptionBudget para no quedarse sin ninguna al mantener un nodo.

## Fuentes

- Kubernetes, HorizontalPodAutoscaler (la regla, `behavior`, la ventana de estabilización): https://kubernetes.io/docs/concepts/workloads/autoscaling/horizontal-pod-autoscale/
- Kubernetes, recorrido del HPA paso a paso: https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale-walkthrough/
- Kubernetes, requests y limits de CPU y memoria: https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/
- Kubernetes, Services (reparto por conexión): https://kubernetes.io/docs/concepts/services-networking/service/
- Quarkus, apagado ordenado: https://quarkus.io/guides/lifecycle
- fortio, el generador de carga: https://github.com/fortio/fortio
