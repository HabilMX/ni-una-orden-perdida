# El hilo de correlación viaja con la orden, pero no sale de casa

> **Dónde aparece:** lecciones 09 y 10.

## El problema

Sin un id que viaje con la orden, seguirla por los sistemas es adivinar por la hora.

## Qué decidimos

Cada orden lleva un **id de correlación**. Si la tienda manda `X-Correlation-Id`, se usa ese; si no, el router
inventa un UUID (un número al azar tan largo que, en la práctica, no se repite). Viaja en el sobre como extensión `ce-correlationid`, cada servicio lo lee **antes** de limpiar
encabezados y se lo pasa a su destino: la API lo anota y el ERP lo guarda en `pedido_cabecera.correlacion`. La tienda
lo recibe de vuelta. **Al socio no se le manda dentro del 850.**

## Por qué

- **Con un solo id encuentras la orden en cada punto del recorrido.** Sin él, seguir una orden es adivinar por hora.
- **El hilo se pierde en el primer servicio que no lo reenvía**, y casi siempre es por leerlo después de un
  `removeHeaders`. Por eso cada servicio lo guarda primero.
- **El id es nuestro.** Al socio se le manda lo que pacta su guía X12; con él, lo que une su respuesta con nuestra
  orden son los números de control del 850: su acuse 997 cita el `GS06` en el `AK1` y el `ST02` en el `AK2`
  (lección 21).
- Los encabezados del sobre (`ce-*`) no salen hacia la tienda: son del viaje interno.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Usar el número de orden como hilo | Una orden tiene varios momentos y reenvíos; el hilo distingue cada viaje |
| Meter el id en un segmento REF del 850 (el segmento para referencias libres) | El socio no lo pidió y no forma parte de su guía de implementación |
| Encabezado traceparent de W3C (el estándar para seguir una llamada técnica entre servicios) | Es para trazas técnicas entre servicios; se puede sumar después, pero no sustituye al hilo de negocio |

## Qué nos haría reabrirla

- Que la práctica sume trazas distribuidas (OpenTelemetry, el estándar abierto para medir y seguir servicios): el hilo de negocio se queda y la traza técnica va al
  lado.

## Fuentes

- Correlation Identifier (EIP): https://www.enterpriseintegrationpatterns.com/patterns/messaging/CorrelationIdentifier.html
- Correlation Identifier en Camel: https://camel.apache.org/components/next/eips/correlation-identifier.html
- W3C Trace Context: https://www.w3.org/TR/trace-context/
- X12, ejemplo de 997 (`AK1` con el control del grupo, `AK2` con el de la transacción): https://x12.org/examples/004010x363/example-04-basic-functional-acknowledgment-partially-accepted-detail
