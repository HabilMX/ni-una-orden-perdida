# Glosario

Los términos de la práctica, en una línea cada uno y en orden alfabético. Entre paréntesis, la lección donde aparecen
por primera vez.

| Término | Qué es |
|---|---|
| **214** | El documento X12 de estatus del transportista: dónde va un envío o si ya se entregó (16) |
| **850** | El documento X12 de orden de compra (06) |
| **856** | El documento X12 de aviso de embarque: qué salió, en qué paquete y con qué guía (13) |
| **997** | El acuse funcional de X12: «recibí tu documento y está bien formado», o qué tiene mal (21) |
| **API** | La puerta por la que un sistema deja que otros le pidan cosas, aquí por HTTP (02) |
| **Camel** | Apache Camel: la biblioteca de integración con la que se escriben las rutas de la práctica (01) |
| **Caso dorado** | Una entrada con su salida esperada, para probar un mapeo; si no da exacto lo esperado, el mapeo está mal (03) |
| **CloudEvents** | Una especificación abierta para describir un evento: tipo, origen, id y momento (08) |
| **Componente** | La pieza de Camel que sabe hablar con algo: HTTP, archivos, SFTP, una base de datos (01) |
| **Compose** | La herramienta de Docker que levanta varios contenedores juntos a partir de un archivo YAML (00) |
| **Conexión persistente** | Una conexión HTTP que se queda abierta para varias llamadas, en vez de abrir una nueva cada vez (12) |
| **Contenedor** | Una imagen corriendo, con sus propios archivos, red y procesos (00) |
| **Content-Based Router** | El patrón que decide a dónde va un mensaje según lo que trae (11) |
| **Correlación, id de** | Un id que viaja con la orden por todos los sistemas, para encontrarla en cada uno (09) |
| **Cuerpo** | El contenido de un mensaje: en la práctica, casi siempre la orden (01) |
| **DNS** | El servicio que traduce un nombre (`int-bd`) a la dirección de una máquina o contenedor (12) |
| **DSL** | Un lenguaje hecho para una sola tarea; el DSL de YAML de Camel describe rutas (01) |
| **EDI** | Intercambio electrónico de documentos entre empresas, en un formato pactado (06) |
| **Encabezado** | Un dato sobre el mensaje, que viaja junto al cuerpo: `Content-Type`, `Idempotency-Key` (02) |
| **ERP** | El sistema central de la empresa: inventario, compras, contabilidad (04) |
| **Guía** | El número con que la paquetería identifica un envío (13) |
| **Idempotencia** | Que hacer algo dos veces deje lo mismo que hacerlo una (02) |
| **Idempotency-Key** | El encabezado con la llave que le permite a la API reconocer un reenvío (02) |
| **Imagen** | El paquete con todo lo que un contenedor necesita para correr (00) |
| **int-…** | Los servicios que llevan la orden a un destino: int-rest a la API, int-bd al ERP, int-edi al socio (08) |
| **JSONata** | El lenguaje con que la práctica traduce un JSON a otro: los mapeos (03) |
| **JVM** | La máquina virtual de Java: ejecuta el programa y lo optimiza mientras corre (01) |
| **known_hosts** | La lista de servidores SSH en los que confías, cada uno con su huella (07) |
| **Kubernetes** | El sistema que administra contenedores en varias máquinas; tiene su [ficha](antes-de-empezar/08-kubernetes.md) (12) |
| **Mapeo** | La traducción de un formato a otro, en su propio archivo con versión (03) |
| **Mensaje** | Lo que viaja por una ruta: un cuerpo y sus encabezados (01) |
| **Montar** | Hacer que un contenedor vea como suya una carpeta de tu máquina (08) |
| **Multicast** | El patrón que manda el mismo mensaje a varios destinos a la vez (10) |
| **Mustache** | El lenguaje de plantillas con que se arma el EDI; solo acomoda datos, no calcula (06) |
| **Nativo** | Un ejecutable hecho directo para el sistema operativo, sin JVM (33) |
| **problem+json** | El formato estándar para explicar un error de HTTP en el cuerpo (RFC 9457) (02) |
| **Puerto** | El número con que una máquina distingue a cada servicio que escucha en la red: 13306 es el ERP (00) |
| **Quarkus** | Un marco de Java para servicios que arrancan rápido; Camel puede correr sobre él (32) |
| **Réplica** | Una copia más del mismo servicio (12) |
| **Router** | El servicio que recibe la orden de la tienda y la reparte a los int-… (08) |
| **Ruta** | En Camel, el camino de un mensaje: de dónde llega, qué le pasa y a dónde va (01) |
| **SFTP** | Envío de archivos cifrado sobre SSH (07) |
| **Simple** | El lenguaje de expresiones de Camel: `${body}`, `${header.X}` (01) |
| **SP** | Procedimiento almacenado: un programa que vive dentro de la base de datos (04) |
| **Transacción** | Un grupo de escrituras que se hacen todas o ninguna (05) |
| **UUID** | Un número al azar tan largo que, en la práctica, no se repite (09) |
| **verifica** | El script de cada lección que mide lo que llegó a cada sistema y te dice verde o rojo (00) |
| **Webhook** | Cuando otro sistema te avisa llamando a una dirección tuya por HTTP (16) |
| **X12** | El estándar de EDI que usa la práctica (06) |
| **YAML** | Un formato de datos con sangrías; en él se escriben las rutas y los compose (00) |

Más a fondo: [antes de empezar](antes-de-empezar/README.md).
