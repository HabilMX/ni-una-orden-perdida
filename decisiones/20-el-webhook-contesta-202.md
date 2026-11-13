# El webhook de la paquetería contesta 202 en cuanto guardó

> **Dónde aparece:** lección 16.

## El problema

La paquetería avisa por webhook que entregó un paquete. Es un **evento**: cuenta algo que ya pasó, no pide que se
haga algo. Si el router la hace esperar a que la API, el ERP y el socio terminen, la paquetería puede cansarse,
cortar la llamada y volver a mandar el aviso, aunque el router sí lo haya recibido.

## Qué decidimos

El router recibe el aviso en `POST /webhooks/paqueteria`, lo **guarda** como el momento «entregada» de la orden
(igual que la orden y el embarque, ficha 18), contesta **`202 Accepted`** y reparte después. Si la orden no existe,
contesta **`404`** y no guarda nada. El aviso trae la guía; la API y el ERP comprueban que sea la misma con la que
salió el pedido, y se niegan si no lo es.

## Por qué

- **Quien manda un webhook espera una respuesta corta.** La especificación Standard Webhooks cuenta como entregado
  solo lo que recibió un `2xx`, y como fallido lo que venció su tiempo de espera (sugiere de 15 a 30 segundos);
  lo fallido, el emisor lo vuelve a mandar. Contestar al guardar deja ese tiempo de sobra.
- **Un evento no espera respuesta de negocio.** A la paquetería no le toca saber si el ERP ya registró la entrega;
  solo que su aviso no se perdió.
- **Es la misma puerta que la orden y el embarque.** Tres momentos, una sola forma de recibirlos: guardar, contestar,
  repartir. Lo que cambia es de dónde viene y qué momento es.
- **El 404 dice la verdad**: el aviso es de una orden que no conocemos. Guardarlo sin orden sería una operación que
  nadie puede repartir.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Contestar 200 cuando los tres destinos terminen | La paquetería espera al destino más lento, y si corta la llamada reenvía un aviso que ya llegó |
| Contestar 200 sin guardar | Si el router se cae justo después, el aviso se pierde y la paquetería cree que llegó |
| Que la paquetería llame directo a la API y al ERP | Cada destino tendría que conocerla, y el socio no recibiría su 214 |
| Rechazar con 4xx un aviso repetido | Un aviso repetido es normal en los webhooks; se reconoce por su llave (lección 18), no se castiga |

## Qué nos haría reabrirla

- Que la paquetería firme sus avisos: habría que comprobar la firma antes de guardar (Standard Webhooks describe una
  forma).
- Que la paquetería pida una respuesta con datos (por ejemplo, un número de recepción): ahí el 202 no basta.

## Fuentes

- Event Message, de *Enterprise Integration Patterns*: https://www.enterpriseintegrationpatterns.com/patterns/messaging/EventMessage.html
- HTTP, `202 Accepted` y `404 Not Found` (RFC 9110): https://www.rfc-editor.org/rfc/rfc9110.html
- Standard Webhooks (especificación abierta; ver «Delivery success and failure» y «Request timeouts»): https://github.com/standard-webhooks/standard-webhooks/blob/main/spec/standard-webhooks.md
- Componente Platform HTTP de Camel: https://camel.apache.org/components/next/platform-http-component.html
