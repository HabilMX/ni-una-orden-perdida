# Ni una orden perdida — lecciones

Son 31 lecciones, de la 00 a la 30, todas en Docker Compose, más un apéndice opcional de solo lectura. Se hacen en
orden: cada una parte de lo que dejó la anterior. Empieza por la [00](00-ambiente.md), que levanta el ambiente.

### Parte 1 — Que llegue

| # | Lección |
|---|---|
| 00 | [Ambiente listo](00-ambiente.md) |
| 01 | [Mi primera ruta corre](01-mi-primera-ruta.md) |
| 02 | [La orden llega a la API](02-la-orden-llega-a-la-api.md) |
| 03 | [Traducir la orden](03-traducir-la-orden.md) |
| 04 | [Al ERP](04-al-erp.md) |
| 05 | [Todo o nada](05-todo-o-nada.md) |
| 06 | [Armar el EDI](06-armar-el-edi.md) |
| 07 | [Al socio](07-al-socio.md) |
| 08 | [El router](08-el-router.md) |
| 09 | [El hilo entre servicios](09-el-hilo-entre-servicios.md) |
| 10 | [Todos los canales](10-todos-los-canales.md) |
| 11 | [No todo va a todos](11-no-todo-va-a-todos.md) |
| 12 | [Crecer en réplicas](12-crecer-en-replicas.md) |

### Parte 2 — Que avance

| # | Lección |
|---|---|
| 13 | [Llega el embarque](13-llega-el-embarque.md) |
| 14 | [Un archivo, varias órdenes](14-un-archivo-varias-ordenes.md) |
| 15 | [El estado vive fuera del proceso](15-el-estado-vive-fuera-del-proceso.md) |
| 16 | [Llega la entrega](16-llega-la-entrega.md) |
| 17 | [La entrega llegó antes que la guía](17-la-entrega-llego-antes-que-la-guia.md) |

### Parte 3 — Que no falle

| # | Lección |
|---|---|
| 18 | [¿Cuándo dos mensajes son el mismo?](18-cuando-dos-mensajes-son-el-mismo.md) |
| 19 | [El destino no contesta](19-el-destino-no-contesta.md) |
| 20 | [Aceptado, rechazado o no se sabe](20-aceptado-rechazado-o-no-se-sabe.md) |
| 21 | [Enviado no es confirmado](21-enviado-no-es-confirmado.md) |
| 22 | [Tirar un canal](22-tirar-un-canal.md) |
| 23 | [Reprocesar lo que falló](23-reprocesar-lo-que-fallo.md) |
| 24 | [No tumbar al destino](24-no-tumbar-al-destino.md) |
| 25 | [Esperar a los canales](25-esperar-a-los-canales.md) |
| 26 | [Seguir una orden](26-seguir-una-orden.md) |
| 27 | [Lo que el rastro no debe mostrar](27-lo-que-el-rastro-no-debe-mostrar.md) |

### Parte 4 — Que escale

| # | Lección |
|---|---|
| 28 | [Un tópico, un grupo por integración](28-un-topico-un-grupo-por-integracion.md) |
| 29 | [No perder al consumir](29-no-perder-al-consumir.md) |
| 30 | [No perder al publicar](30-no-perder-al-publicar.md) |

### Apéndice opcional

La práctica termina en la 30. El apéndice lleva una integración a Kubernetes y la compara en JVM y en nativo; pide un
equipo con más memoria y disco, y lo dice al principio.

| # | Lección |
|---|---|
| 31 | [De laboratorio a proyecto, en Kubernetes](31-de-laboratorio-a-kubernetes.md) |
| 32 | [JVM contra nativo, medido](32-jvm-contra-nativo.md) |

Las decisiones de diseño, con su porqué, están en [`decisiones/`](../decisiones/README.md).
