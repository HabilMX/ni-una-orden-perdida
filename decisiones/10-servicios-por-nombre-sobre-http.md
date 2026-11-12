# Los servicios se hablan por nombre, sobre HTTP

> **Dónde muerde:** lección 07.

## Qué decidimos

El router llama a cada integración por su **nombre de servicio** (`http://int-rest:8080/integrar`), nunca por una IP
ni por `localhost`. En el camino principal de la práctica, los servicios se hablan por HTTP.

## Por qué

- **Las IP cambian** cada vez que un contenedor se recrea; el nombre no.
- **`localhost` dentro de un contenedor es ese mismo contenedor.** El router que llama a `localhost` se llama a sí
  mismo (en la 07 lo ves: contesta 404).
- HTTP se ve y se prueba con `curl`; los mensajes por un broker (Kafka) llegan en la parte avanzada.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Direcciones IP fijas | Se rompen al recrear un contenedor |
| Todo dentro de un solo proceso | Más simple, pero ya no se puede escalar cada servicio por su lado |

## Qué nos haría reabrirla

- Que el router necesite seguir aceptando órdenes aunque una integración esté caída mucho tiempo: ahí entra un
  broker de mensajes.

## Fuentes

- Message Router (EIP): https://www.enterpriseintegrationpatterns.com/patterns/messaging/MessageRouter.html
- Message Router en Camel: https://camel.apache.org/components/next/eips/message-router.html
