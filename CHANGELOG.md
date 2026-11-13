# Cambios

Aquí se anota qué trae cada versión de la práctica y qué se corrigió después. La forma sigue
[Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/): lo más nuevo arriba.

## [1.0.0] — la práctica completa

### Lecciones
- **Que llegue (00–12):** el ambiente con un solo comando y la orden que llega, un destino a la vez, a la API, al ERP
  en MariaDB y al socio por SFTP con EDI X12; luego el router, el hilo de correlación, todos los canales, el filtro
  por canal y un servicio en varias copias.
- **Que avance (13–17):** el embarque (856) y la entrega (214), el lote de la bodega, el estado fuera del proceso y la
  entrega que llega antes que la guía.
- **Que no falle (18–27):** idempotencia, reintentos, los tres desenlaces (aceptado, rechazado o no se sabe), el acuse
  997, un canal caído que no frena a los otros, el reproceso a mano con rastro, un tope hacia el destino, esperar a
  todos los canales, seguir una orden por su número y un rastro sin datos personales.
- **Que escale (28–30):** Kafka con un grupo de consumidores por integración, el offset que se confirma después del
  desenlace y un outbox para no perder al publicar. Aquí termina la práctica.
- **Apéndice opcional (31–32):** una integración llevada a Kubernetes con un autoescalado que se ve subir y bajar, y
  la misma en JVM y en nativo, medida por el alumno. Pide un equipo con más memoria y disco; lo dice al principio.

### Decisiones
- 39 fichas en [`decisiones/`](decisiones/README.md), cada una con el problema, qué decidimos, por qué, qué
  descartamos y qué nos haría reabrirla.

### Cómo se comprueba
- Cada lección trae un `verifica` que mide el destino (la bitácora de la API, las filas del ERP, el archivo en el
  SFTP, la base de estado, los offsets de Kafka), no lo que dice la ruta. Con `inicio/` sale en rojo y con
  `solucion/` en verde.
- La verificación automática corre el `verifica` de cada lección 00–30 con su solución en cada cambio. El apéndice
  tiene su propio flujo, de disparo manual.
