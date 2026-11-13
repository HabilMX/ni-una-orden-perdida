# Decisiones de diseño

Cada decisión de la práctica tiene su ficha, y cada ficha se lee sola, sin haber hecho la lección. Todas tienen la
misma forma: **el problema, qué decidimos, por qué, qué descartamos, qué nos haría reabrirla y sus fuentes**.
Arriba de cada una dice en qué lección aparece.

Una decisión no es una verdad: es la mejor respuesta que encontramos con lo que sabemos hoy. Por eso cada ficha dice
qué la haría cambiar. Si encuentras un caso que la contradiga, abre un issue.

| # | Decisión | Lección |
|---|---|---|
| 01 | [Apache Camel como motor de integración](01-apache-camel-como-motor.md) | 01 |
| 02 | [Rutas en YAML, en su forma canónica](02-rutas-en-yaml-canonico.md) | 01 en adelante |
| 03 | [JSONata para traducir entre formatos](03-jsonata-para-traducir.md) | 03 |
| 04 | [Sin casos dorados no hay mapeo](04-casos-dorados.md) | 03 en adelante |
| 05 | [La API recibe una llave de idempotencia](05-idempotency-key-hacia-la-api.md) | 02 |
| 06 | [En el ERP, todo o nada](06-todo-o-nada-en-el-erp.md) | 05 |
| 07 | [El EDI se arma con una plantilla sin lógica](07-mustache-sin-logica-para-el-edi.md) | 06 |
| 08 | [Cuatro cuidados al dejar un archivo al socio](08-entrega-segura-al-sftp.md) | 07 |
| 09 | [Una sola imagen; cada servicio es una carpeta](09-una-imagen-un-servicio-por-carpeta.md) | 08 |
| 10 | [Los servicios se hablan por nombre, sobre HTTP](10-servicios-por-nombre-sobre-http.md) | 08 |
| 11 | [CloudEvents en modo binario como sobre](11-cloudevents-como-sobre.md) | 08, 09 |
| 12 | [El hilo de correlación viaja con la orden, pero no sale de casa](12-el-hilo-de-correlacion.md) | 09, 10 |
| 13 | [La orden sale a todos los canales, y el router cuenta lo que pasó](13-multicast-que-no-inventa.md) | 10 |
| 14 | [Quién recibe qué se decide por el contenido, en un solo lugar](14-quien-recibe-se-decide-en-un-lugar.md) | 11 |
| 15 | [Tres copias no reparten solas: el reparto es por conexión](15-repartir-por-conexion.md) | 12 |
| 16 | [Una carpeta, un solo sondeo](16-un-solo-sondeo-por-carpeta.md) | 13 |
| 17 | [Un archivo de la bodega se parte en sus embarques, y uno malo no detiene a los demás](17-partir-el-archivo.md) | 14 |
| 18 | [Guardar antes de contestar](18-guardar-antes-de-contestar.md) | 15 en adelante |
| 19 | [El número de control del X12 lo asigna el router](19-el-numero-de-control-lo-asigna-el-router.md) | 15 en adelante |
| 20 | [El webhook de la paquetería contesta 202 en cuanto guardó](20-el-webhook-contesta-202.md) | 16 |
| 21 | [Lo que llega antes de tiempo se estaciona, no se rechaza](21-estacionar-lo-que-llega-antes.md) | 17 |
| 22 | [Un mensaje se reconoce por su llave, y su contenido por su huella](22-la-llave-y-la-huella.md) | 18 en adelante |
| 23 | [Se reintenta en el lugar, solo en el router, con espera creciente](23-reintentar-en-el-lugar.md) | 19 en adelante |
| 24 | [Tres desenlaces, decididos por la evidencia: aceptado, rechazado o no se sabe](24-tres-desenlaces.md) | 20 en adelante |
| 25 | [El 997 se correlaciona por el grupo y el documento, y solo él confirma](25-el-997-se-correlaciona-por-grupo-y-documento.md) | 21 en adelante |
| 26 | [Un cortacircuito por canal, en el router](26-un-cortacircuito-por-canal.md) | 22 en adelante |
| 27 | [El reproceso a mano es un intento más, con nombre y motivo](27-el-rerun-es-un-intento-manual.md) | 23 en adelante |
| 28 | [Un tope por copia hacia el ERP, contando lo que va en curso](28-un-tope-por-copia.md) | 24 en adelante |
| 29 | [Juntar es preguntarle a la base, y un vencido avisa sin cerrar](29-juntar-es-preguntarle-a-la-base.md) | 25 en adelante |
| 30 | [El recorrido es una vista por número de orden, y los cuerpos van enteros aparte](30-el-recorrido-es-una-vista.md) | 26 en adelante |
| 31 | [El rastro se guarda con los datos personales tapados; al destino sale completo](31-el-rastro-se-guarda-tapado.md) | 27 en adelante |
| 32 | [Un evento por momento en un tópico, con la orden como llave, y un grupo por integración](32-un-topico-un-grupo-por-integracion.md) | 28 en adelante |
| 33 | [El offset se confirma a mano, después de dejar el desenlace, y lo que falla se vuelve a leer](33-el-offset-se-confirma-despues-del-desenlace.md) | 29 en adelante |
| 34 | [Lo que hay que publicar se guarda en la misma transacción (outbox), y un sondeo lo publica](34-el-outbox-publica-lo-guardado.md) | 30 |
| 35 | [Kubernetes de práctica con kind, con metrics-server y versiones fijas, y un clúster que se borra al terminar](35-kubernetes-de-practica-con-kind.md) | 31 (apéndice) |
| 36 | [A Kubernetes va int-rest, exportado con camel export a Camel Quarkus, sin cambiarle la ruta y sin application.properties](36-int-rest-exportado-con-camel-export.md) | 31 y 32 (apéndice) |
| 37 | [El HPA escala por CPU, medida como porcentaje de lo que cada copia reserva, y baja rápido en la práctica](37-el-hpa-escala-por-cpu-reservada.md) | 31 (apéndice) |
| 38 | [El nativo se compila con Mandrel dentro de un contenedor, con el mismo compilador de Java que la JVM](38-el-nativo-se-compila-en-un-contenedor.md) | 32 (apéndice) |
| 39 | [JVM contra nativo se mide en tu máquina, con un método fijo y sin umbrales; la capacidad se mide igual](39-se-mide-en-tu-maquina-sin-umbrales.md) | 32 (apéndice) |
| 40 | [Los contenedores de Camel escriben como el dueño de tu carpeta, sin que tengas que hacer nada](40-camel-escribe-como-el-dueno-de-la-carpeta.md) | 01 a 30 |
