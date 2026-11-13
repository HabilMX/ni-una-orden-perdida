# Tres desenlaces, decididos por la evidencia: aceptado, rechazado o no se sabe

> **Dónde aparece:** lección 20 (la clasificación existe desde la 15; aquí se razona y se completa).

## El problema

La API tardó más que el tiempo límite y la llamada se cortó. ¿Escribió o no escribió? No lo sabemos. Si lo anotamos
como «falló», alguien lo reenvía a mano y, sin idempotencia, se duplica; o alguien lo da por perdido cuando sí
llegó. Y si la API contestó 422 por una regla de negocio, reintentarlo no cambia nada: solo la vuelve a molestar.

## Qué decidimos

| Lo que se vio | Desenlace | ¿Se reintenta? |
|---|---|---|
| 2xx | `aceptada` | No hace falta |
| 4xx (regla de negocio, llave reusada, pedido inválido) | `rechazada` | No: el destino dijo que no |
| 5xx, 504 del canal, o sin respuesta | `no_se_sabe` | Sí, en el lugar (ficha 23) |
| Reintentos agotados | la operación pasa a `conciliacion_pendiente` | No; una persona revisa el destino |

- El canal pone el tiempo límite (3 s hacia la API) y, si vence, contesta **504** diciendo que no sabe. No inventa un
  «sí» ni un «no».
- **El intento se escribe antes de enviar**, con su huella y sin desenlace. Si el router se cae con la llamada en
  camino, en la base queda que lo intentó y que no sabe cómo le fue.
- No existe el desenlace «fallo»: lo que no es un sí o un no explícito es «no se sabe».

## Por qué

- **La evidencia manda.** Un 4xx es una respuesta del destino; un tiempo vencido es la ausencia de respuesta. Tratarlos
  igual es tirar información.
- **«No se sabe» no es «no llegó».** En la práctica se ve: la API lenta registra el pedido diez segundos después de
  que cortamos.
- **El renglón antes de enviar** es lo que permite, tras una caída, encontrar lo que quedó en el aire y conciliarlo.
- **La conciliación la hace quien puede mirar el destino.** El router no puede preguntar «¿te llegó?» si el destino no
  ofrece cómo; lo deja marcado para alguien que sí puede.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Un solo estado «fallida» para todo lo que no es 2xx | Mezcla «el destino dijo que no» con «no sabemos», y lleva a reintentar lo que no cambia o a abandonar lo que sí llegó |
| Reintentar también los 4xx | Da la misma respuesta las mismas veces, y retrasa todo lo que viene detrás en la cola |
| Escribir el intento después de la llamada | Si el router se cae en medio, no queda rastro de que se envió |
| Un Dead Letter Channel que descarte lo agotado | Lo agotado no se tira: queda en `conciliacion_pendiente`, en la misma base, a la vista |

## Qué nos haría reabrirla

- Que un destino ofrezca cómo preguntar por un envío (`GET` por la llave de idempotencia): la conciliación podría
  hacerla el router, sin una persona.
- Que un destino use un 4xx para un problema pasajero (por ejemplo, `429 Too Many Requests`): ese código se trataría
  como «no se sabe» y se respetaría su espera.

## Fuentes

- Dead Letter Channel, de *Enterprise Integration Patterns*: https://www.enterpriseintegrationpatterns.com/patterns/messaging/DeadLetterChannel.html
- HTTP, códigos 4xx, 5xx y `504 Gateway Timeout` (RFC 9110): https://www.rfc-editor.org/rfc/rfc9110.html
- Componente HTTP de Camel (`responseTimeout`): https://camel.apache.org/components/next/http-component.html
- The Idempotency-Key HTTP Header Field (borrador del IETF): https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/
