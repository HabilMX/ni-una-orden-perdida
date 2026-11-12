# El hilo de correlación viaja con la orden, pero no sale de casa

> **Dónde muerde:** lección 08, 09.

## Qué decidimos

Cada orden lleva un **id de correlación**. Si la tienda manda `X-Correlation-Id`, se usa ese; si no, el router
inventa un UUID. Viaja en el sobre como extensión `ce-correlationid`, cada servicio lo lee **antes** de limpiar
encabezados y se lo pasa a su destino: la API lo anota y el ERP lo guarda en `pedido_cabecera.correlacion`. La tienda
lo recibe de vuelta. **Al socio no se le manda dentro del 850.**

## Por qué

- **Con un solo id encuentras la orden en cada punto del recorrido.** Sin él, seguir una orden es adivinar por hora.
- **El hilo se pierde en el primer servicio que no lo reenvía**, y casi siempre es por leerlo después de un
  `removeHeaders`. Por eso cada servicio lo guarda primero.
- **El id es nuestro.** Al socio se le manda lo que pacta su guía X12; con él, lo que une su respuesta con nuestra
  orden es el número de control del sobre X12, que su acuse 997 cita (lección 17c).
- Los encabezados del sobre (`ce-*`) no salen hacia la tienda: son del viaje interno.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Usar el número de orden como hilo | Una orden tiene varios momentos y reenvíos; el hilo distingue cada viaje |
| Meter el id en un segmento REF del 850 | El socio no lo pidió y no forma parte de su guía de implementación |
| Encabezado traceparent de W3C | Es para trazas técnicas entre servicios; se puede sumar después, pero no sustituye al hilo de negocio |

## Qué nos haría reabrirla

- Que la práctica sume trazas distribuidas (OpenTelemetry): el hilo de negocio se queda y la traza técnica va al
  lado.

## Fuentes

- Correlation Identifier (EIP): https://www.enterpriseintegrationpatterns.com/patterns/messaging/CorrelationIdentifier.html
- Correlation Identifier en Camel: https://camel.apache.org/components/next/eips/correlation-identifier.html
- W3C Trace Context: https://www.w3.org/TR/trace-context/
