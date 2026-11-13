# Un mensaje se reconoce por su llave, y su contenido por su huella

> **Dónde aparece:** lección 18 (y desde ahí, en todo momento que recibe el router).

## El problema

La paquetería avisa dos veces la misma entrega: no le llegó nuestro 202 a tiempo, o su sistema reintenta solo. Si el
router trata el segundo aviso como nuevo, la API recibe otro evento y el socio otro 214. Y hay un caso que se parece
pero no es igual: un aviso con la misma orden y el mismo momento, pero con **otra guía**. Eso no es un repetido;
alguien cambió algo.

## Qué decidimos

- La **llave** de un momento es su `ce-id` (`orden-<orden>-<momento>`), y cada canal la lleva una vez: es la
  `UNIQUE (orden, momento, canal)` de la tabla `operacion`. La llave sale del negocio, no de un número al azar.
- La **huella** es el sha256 de lo que llegó, ya normalizado como `jsonb` (sin espacios de más, sin importar el orden
  de los campos), y se guarda en cada operación.
- Antes de guardar, el router reconoce: sin operaciones con esa llave es **nuevo**; con la misma huella es
  **repetido** (202, no se guarda ni se reparte nada); con otra huella es **conflicto** (409, una fila en la tabla
  `conflicto` con las dos huellas y lo que llegó, y un aviso `WARN` en el log). Un conflicto no pisa lo guardado.
- Si dos avisos iguales llegan a la vez y los dos se ven nuevos, la `UNIQUE` detiene al segundo: su transacción se
  deshace y, cuando lo reintenten, ya se reconoce como repetido.

## Por qué

- **Un Idempotent Consumer necesita una llave que se repita igual en cada reenvío.** Un UUID nuevo por envío haría que
  cada reenvío pareciera otro mensaje.
- **La llave sola no distingue un reenvío de un cambio.** La huella sí: mismo contenido, misma huella.
- **Normalizar antes de la huella** evita que el mismo aviso, escrito en una sola línea o con los campos en otro
  orden, se tome por un cambio.
- **La base de estado ya es el registro de lo que se recibió.** La llave es la de sus operaciones: no hace falta
  otra tabla de ids que se pueda desincronizar.
- **Un conflicto lo decide una persona.** Quedarse con el primero o con el último en silencio sería decidir por ella.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| `idempotentConsumer` de Camel con un repositorio JDBC aparte | Guarda solo el id: no ve que el contenido cambió, y es otra tabla que puede no coincidir con `operacion` |
| Huella del texto tal cual llegó | El mismo aviso con otro formato tendría otra huella y se marcaría como conflicto |
| Quedarse con el último aviso | Pisa lo que ya salió a los destinos; la API y el ERP ya tienen la guía original |
| Contestar error al repetido | Quien reenvía un aviso que sí llegó volvería a mandarlo; el 202 le dice que ya lo tenemos |

## Qué nos haría reabrirla

- Que un remitente mande un id propio para cada aviso: la llave podría ser la suya, si es estable entre reenvíos.
- Que los conflictos sean frecuentes y tengan una regla clara (por ejemplo, «la paquetería corrige la guía»): se
  podría resolver solo, con esa regla escrita y probada.

## Fuentes

- Idempotent Receiver, de *Enterprise Integration Patterns*: https://www.enterpriseintegrationpatterns.com/patterns/messaging/IdempotentReceiver.html
- Idempotent Consumer EIP de Camel: https://camel.apache.org/components/next/eips/idempotentConsumer-eip.html
- HTTP, `409 Conflict` (RFC 9110): https://www.rfc-editor.org/rfc/rfc9110.html
- PostgreSQL, funciones de cadenas binarias (`sha256`): https://www.postgresql.org/docs/17/functions-binarystring.html
