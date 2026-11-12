# El negocio de retail: ERP, SP, SFTP, EDI X12, webhook y guía

**Para qué te sirve aquí:** la práctica integra una tienda inventada con los sistemas que tiene cualquier cadena. Si
sabes qué hace cada uno y por qué habla como habla, las rutas tienen sentido.

## Lo que ves en la práctica

Una orden de la tienda tiene que llegar a tres lugares, cada uno con su forma: la **API de pedidos** (JSON por HTTP),
el **ERP** (filas en MariaDB y un procedimiento) y el **socio comercial** (un archivo EDI X12 en su SFTP). Más adelante
llegan el embarque de la bodega y el aviso de entrega de la paquetería.

## Una imagen de la vida diaria

Una orden es una **carta** que tiene que llegar a tres oficinas que hablan idiomas distintos. A una se le entrega en
mano (la API), a otra se le llena su formulario (el ERP) y a la tercera se le deja en su buzón en un formato pactado
hace años (el socio).

## Los términos

- **ERP** (Enterprise Resource Planning): el sistema central de la empresa. Ahí viven el inventario, las compras, la
  contabilidad. Suele ser lo más viejo y lo más delicado de tocar.
- **SP** (*stored procedure*, procedimiento almacenado): un programa que vive **dentro** de la base de datos. En la
  práctica, `registrar_pedido` valida y registra el pedido del lado del ERP; nosotros solo lo llamamos.
- **SFTP**: envío de archivos cifrado sobre SSH. Muchas empresas intercambian documentos dejando archivos en una
  carpeta del otro.
- **EDI X12**: un estándar de intercambio electrónico de documentos entre empresas, con décadas de uso en el comercio.
  Cada documento tiene un número: **850** es la orden de compra; **856**, el aviso de embarque; **214**, el estatus del
  transportista; **997**, el acuse funcional («recibí tu documento y está bien formado», o no). Es texto con segmentos
  separados por símbolos; la práctica trae un lector que te los explica en español.
- **Socio comercial**: la otra empresa (aquí, quien opera las franquicias) que recibe nuestros documentos.
- **Webhook**: cuando otro sistema te **avisa** llamando a una dirección tuya por HTTP, en vez de que tú le preguntes a
  cada rato. La paquetería avisa así que entregó.
- **Guía**: el número con el que la paquetería identifica un envío. Llega con el embarque y la entrega lo necesita.

## Lo que necesitas saber para la práctica

- La tienda, las órdenes y los clientes son inventados. Ningún dato es de una empresa real.
- Cada sistema destino **no es permisivo**: valida, rechaza y a veces contesta tarde, como uno real.
- Un archivo dejado en el SFTP no es una orden confirmada: la confirmación llega después, en el 997 (lección 21).

## Compruébalo

Pregunta: la bodega embarcó la orden 1003 y la paquetería ya la entregó. ¿Qué documento X12 le corresponde a cada uno
de esos dos momentos, y por qué la entrega necesita algo del embarque?

Respuesta: el embarque es un **856** y la entrega un **214**. La entrega necesita la **guía**, que llega con el embarque:
sin ella no se sabe qué envío se entregó.

## Si quieres ir más lejos

- X12, la organización que publica el estándar: https://x12.org/
- X12, lista de documentos (transaction sets): https://x12.org/products/transaction-sets
- MariaDB, procedimientos almacenados: https://mariadb.com/docs/server/server-usage/stored-routines/stored-procedures
