# A Kubernetes va int-rest, exportado con camel export a Camel Quarkus, sin cambiarle la ruta y sin application.properties

> **Dónde aparece:** lecciones 31 y 32 (apéndice opcional).

## El problema

El apéndice lleva UNA integración de la práctica de `camel run` a un proyecto que se puede compilar, empaquetar y
desplegar, para verla escalar sola (31) y para medirla en JVM y en nativo (32). Hay que escoger cuál, y cómo pasarla
a proyecto sin reescribirla: la promesa de la práctica es que el mismo YAML corre sobre Camel Quarkus. Además, el
proyecto que sale de `camel export` trae un `application.properties`, y la práctica no los usa.

## Qué decidimos

- **El servicio es int-rest, tal como quedó en la lección 12** (`lecciones/31-…/int-rest/`): recibe la orden por HTTP,
  la traduce con JSONata (`mappings/api/pedido/v2`) y la entrega a la API de pedidos. La ruta no cambia ni una letra.
- **`camel export --runtime=quarkus` corre dentro de la construcción de la imagen** (`lecciones/31-…/Dockerfile`,
  etapa `exportar`), con la misma imagen `apache/camel-jbang:4.22.1` de todas las lecciones. El proyecto no se guarda
  en el repo: se genera cada vez desde la carpeta del laboratorio, que es la única fuente.
- **Tres ajustes al proyecto que sale, y nada más:**
  1. Se exporta solo la ruta (`camel export int-rest.camel.yaml`) y los mapeos se copian con su carpeta a
     `src/main/resources/mappings/`. Sin decirle qué archivos, `camel export` deja todos sueltos en un solo
     directorio: la ruta ya no encuentra su mapeo y los casos dorados chocan entre sí.
  2. Se borra el `application.properties` que deja `camel export` (sale vacío). Lo que se decide **al compilar**
     va en el `pom.xml` con `--build-property` (por ejemplo `quarkus.native.resources.includes=mappings/**`, para
     que el nativo guarde los mapeos). Lo que se decide **al correr** va en variables de entorno del Deployment
     (por ejemplo `QUARKUS_SHUTDOWN_TIMEOUT`).
  3. Se agrega la salud de Camel (`camel-quarkus-microprofile-health`) con `quarkus:add-extension`: da
     `/q/health/ready` y `/q/health/live`, que Kubernetes usa para saber si una copia ya puede recibir órdenes y si
     sigue viva, y que la 32 usa para medir el arranque.
- **Su destino, la API de pedidos, corre dentro del clúster** con el nombre `api-pedidos`, el mismo que en Compose.

## Por qué

- **int-rest no guarda estado:** cualquier copia atiende cualquier orden, así que se puede tener una o cuatro sin
  cambiar nada. Es lo que pide un HPA.
- **Gasta CPU en cada orden** (traducir con JSONata y llamar a la API), así que la carga se nota en la métrica que
  mira el HPA.
- **Su destino es uno solo y cabe en el clúster** (la API de la lección 00, que ya es Quarkus). int-bd pediría
  MariaDB y transacciones, int-edi un SFTP y plantillas, y el router PostgreSQL y Kafka: más piezas que levantar en el
  clúster para enseñar lo mismo.
- **Todas sus extensiones funcionan en nativo** (Platform HTTP, HTTP, JSONata, YAML DSL, MicroProfile Health), así
  que la 32 mide el mismo servicio de la 31, no otro.
- **Que el proyecto salga de `camel export` cada vez** hace que lo que se despliega sea lo que se probó con
  `camel run`. Guardar el proyecto generado en el repo abriría la puerta a que la ruta del proyecto y la del
  laboratorio se separen sin que nadie lo note.
- **Quarkus lee su configuración de variables de entorno** con la regla de nombre en mayúsculas y `_` en vez de
  `.`; por eso un Deployment puede configurarlo todo sin un archivo dentro de la imagen.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| int-bd (la 12 lo hace crecer en réplicas) | Su destino es MariaDB con transacciones: habría que llevar el ERP al clúster, y su CPU la marca la base, no la ruta |
| El router | Necesita PostgreSQL y Kafka; además guarda estado y sondea, y dos copias sondeando piden otro cuidado (ficha 34) |
| Guardar en el repo el proyecto ya exportado | Duplica la ruta y los mapeos, y se pueden desincronizar del laboratorio |
| Dejar el `application.properties` que genera `camel export` | La práctica no los usa: la configuración va donde se ve, en el Deployment o en el YAML |
| Exportar con `--observe` para tener salud | Trae además métricas y OpenTelemetry: más extensiones y más memoria para medir en la 32 |
| Mover el destino a una variable (`{{env:API_URL}}`) | Cambiaría la ruta de la 12; con un Service llamado `api-pedidos`, la ruta funciona igual |

## Qué nos haría reabrirla

- Que `camel export` empiece a copiar los recursos con su carpeta, o a agregar extensiones de Quarkus con `--dep`
  (hoy solo las agrega con su versión exacta): se quitarían los ajustes 1 y 3.
- Que la práctica pase a llevar a Kubernetes un servicio con estado (el router): pediría un StatefulSet o un solo
  sondeo, y otra lección.

## Fuentes

- Camel CLI (JBang), incluido `camel export`: https://camel.apache.org/manual/camel-jbang.html
- Camel Quarkus, primeros pasos: https://camel.apache.org/camel-quarkus/latest/first-steps.html
- Camel Quarkus, JSONata en nativo y `quarkus.native.resources.includes`: https://camel.apache.org/camel-quarkus/latest/reference/extensions/jsonata.html
- Camel Quarkus, MicroProfile Health: https://camel.apache.org/camel-quarkus/latest/reference/extensions/microprofile-health.html
- Quarkus, configuración por variables de entorno: https://quarkus.io/guides/config-reference
- Quarkus, apagado ordenado (`quarkus.shutdown.timeout`): https://quarkus.io/guides/lifecycle
- Kubernetes, pruebas de salud (probes): https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#container-probes
