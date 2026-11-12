# La API recibe una llave de idempotencia

> **Dónde aparece:** lección 02.

## El problema

Si la red se corta después de que la API guardó el pedido, reenviarlo sin cuidado crea un pedido duplicado.

## Qué decidimos

Una operación es *idempotente* si hacerla dos veces deja lo mismo que hacerla una: si la tienda reenvía el pedido, la
API no crea un segundo. Para lograrlo, cada pedido que mandamos a la API lleva el encabezado `Idempotency-Key` con el
número de la orden. La API demo no es permisiva, y contesta lo que pide el borrador del IETF para este encabezado:

| Llega | Contesta |
|---|---|
| Sin llave | **400** |
| Una llave nueva | **201**, y guarda el pedido |
| La misma llave con el mismo contenido | **200**, sin duplicar: es un reenvío |
| La misma llave con **otro** contenido | **422**, con el tipo `llave-reusada` |
| Un pedido que no cuadra (sin partidas, un total mal sumado) | **422**, con el tipo `pedido-invalido` |

Los dos 422 se distinguen por el **cuerpo**, no por el código: todo error sale en el formato estándar de problemas
de HTTP (`application/problem+json`), y su campo `type` dice cuál es.

## Por qué

- **Reenviar tiene que ser seguro.** Si la red se corta después de que la API guardó el pedido, no sabemos si llegó;
  reenviarlo con la misma llave no crea un segundo pedido.
- **La llave sale del negocio** (el número de orden), no de un número al azar: la misma orden siempre da la misma llave.
- **Reusar una llave es un error de quien envía**, y no se arregla reintentando: no se adivina cuál de los dos
  contenidos vale. Por eso no es un 5xx, que invita a reintentar.
- **El código solo no alcanza.** Un 422 puede ser «la llave ya se usó» o «el pedido no cuadra»; quien recibe la
  respuesta lee el `type`. Así trabajan APIs conocidas que usan esta llave, como Stripe: el detalle va en el cuerpo.
- **El 409 queda para otra cosa.** El borrador lo usa cuando llega un reintento mientras la petición original sigue
  en proceso, y significa «vuelve a intentar más tarde». La API demo contesta al momento, así que nunca lo da.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Sin llave, confiar en que no habrá reenvíos | Las redes fallan; un reintento duplicaría el pedido |
| Una llave al azar por envío | Cada reenvío tendría llave nueva y la API no podría reconocerlo |
| Contestar 409 a la llave reusada | Suena natural («conflicto»), pero el borrador reserva el 409 para «sigue en proceso, reintenta», y enseñarlo para un error que no se reintenta lleva a una ruta a reintentar lo que nunca va a pasar |

## Qué nos haría reabrirla

- Que la propuesta del IETF para este encabezado cambie de forma al volverse estándar.

## Fuentes

- Borrador del IETF, encabezado Idempotency-Key (versión -08, §2.7, códigos de error): https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/
- RFC 9457, el formato de problemas de HTTP: https://www.rfc-editor.org/rfc/rfc9457
- Stripe, peticiones idempotentes: https://docs.stripe.com/api/idempotent_requests
