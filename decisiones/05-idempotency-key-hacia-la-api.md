# La API recibe una llave de idempotencia

> **Dónde muerde:** lección 02.

## Qué decidimos

Cada pedido que mandamos a la API lleva el encabezado `Idempotency-Key` con el número de la orden. La API demo no es
permisiva: sin llave contesta 400; con la misma llave y el mismo contenido contesta 200 sin duplicar; con la misma
llave y **otro** contenido contesta 409.

## Por qué

- **Reenviar tiene que ser seguro.** Si la red se corta después de que la API guardó el pedido, no sabemos si llegó;
  reenviarlo con la misma llave no crea un segundo pedido.
- **La llave sale del negocio** (el número de orden), no de un número al azar: la misma orden siempre da la misma llave.
- **El 409 importa:** misma llave con otro contenido es un conflicto, y no se adivina cuál vale.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Sin llave, confiar en que no habrá reenvíos | Las redes fallan; un reintento duplicaría el pedido |
| Una llave al azar por envío | Cada reenvío tendría llave nueva y la API no podría reconocerlo |

## Qué nos haría reabrirla

- Que la propuesta del IETF para este encabezado cambie de forma al volverse estándar.

## Fuentes

- Borrador del IETF, encabezado Idempotency-Key: https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/
