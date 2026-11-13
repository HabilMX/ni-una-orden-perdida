# Tres desenlaces, decididos por la evidencia: aceptado, rechazado o no se sabe

> **Dónde aparece:** lección 20 (la clasificación existe desde la 15; aquí se razona y se completa, con los 408, 425 y
> 429 y su `Retry-After`) y, con la misma pieza, hasta la 30.

## El problema

La API tardó más que el tiempo límite y la llamada se cortó. ¿Escribió o no escribió? No lo sabemos. Si lo anotamos
como «falló», alguien lo reenvía a mano y, sin idempotencia, se duplica; o alguien lo da por perdido cuando sí
llegó. Y si la API contestó 422 por una regla de negocio, reintentarlo no cambia nada: solo la vuelve a molestar.
Pero no todo 4xx es un «no»: un 429 dice «me mandaste demasiadas, prueba después». Tomarlo como rechazo deja sin
mandar una orden que la API habría aceptado unos segundos más tarde.

## Qué decidimos

| Lo que se vio | Desenlace | ¿Se reintenta? |
|---|---|---|
| 2xx | `aceptada` | No hace falta |
| 408, 425 o 429 | `no_se_sabe` | Sí: el destino dijo «ahora no, prueba después». Si mandó `Retry-After` en segundos, se espera eso (con un tope de 10 s); si no, la espera creciente (ficha 23) |
| Cualquier otro 4xx (regla de negocio, llave reusada, pedido inválido) | `rechazada` | No: el destino dijo que no |
| 5xx, 504 del canal, o sin respuesta | `no_se_sabe` | Sí, en el lugar (ficha 23) |
| Reintentos agotados | la operación pasa a `conciliacion_pendiente` | No; una persona revisa el destino |

- El canal pone el tiempo límite (3 s hacia la API) y, si vence, contesta **504** diciendo que no sabe. No inventa un
  «sí» ni un «no».
- **El intento se escribe antes de enviar**, con su huella y sin desenlace. Si el router se cae con la llamada en
  camino, en la base queda que lo intentó y que no sabe cómo le fue.
- No existe el desenlace «fallo»: lo que no es un sí o un no explícito es «no se sabe».
- **Los tres 4xx que no son un «no»**, en palabras sencillas:
  - **429 Too Many Requests**: «me mandaste demasiadas en poco tiempo». Puede traer `Retry-After` con cuánto esperar.
  - **408 Request Timeout**: «me cansé de esperar a que terminaras de mandarme la petición». El RFC dice que el
    cliente puede repetirla.
  - **425 Too Early**: «es muy pronto para atender esto, podría ser una repetición». El RFC espera que el cliente la
    repita.
- **`Retry-After` se respeta**: cuando uno de esos tres trae `Retry-After` en segundos, el siguiente intento espera
  eso en vez de la espera creciente. El tope de **10 s** es de la práctica, para que un `Retry-After` de una hora no
  la detenga; un sistema real fija su tope según cuánto puede esperar. Si viene como fecha HTTP o no viene, se usa la
  espera creciente. Hasta la 27 lo hace el encabezado `CamelRedeliveryDelay` que deja `intentar`; desde la 28,
  int-rest pasa la espera en el desenlace y el router la usa al volver a publicar.
- **Solo el router reintenta**: el cliente HTTP de Camel repite en silencio un 429 o un 503 por su cuenta; en el
  router y en int-rest esa repetición está apagada (`automaticRetriesDisabled`), para que cada intento quede en la
  base y no se multipliquen.
- En la práctica, la API en modo `satura` contesta 429 con `Retry-After: 3` unas veces y luego atiende (API 1.5.0).

## Por qué

- **La evidencia manda.** Un 4xx es una respuesta del destino; un tiempo vencido es la ausencia de respuesta. Tratarlos
  igual es tirar información.
- **El código dice si repetir sirve.** El RFC de cada uno de esos tres códigos describe una condición pasajera: la
  misma petición, más tarde, puede salir bien. El resto de los 4xx describe un problema de la petición misma.
- **El destino sabe cuándo puede atender.** Si dice cuánto esperar, adivinar con la espera creciente lo vuelve a
  llamar antes de tiempo, que es justo lo que pidió que no se hiciera.
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
| Tomar todo 4xx como `rechazada` | Un 429, un 408 o un 425 dejan sin mandar una orden que el destino habría aceptado más tarde |
| Ignorar `Retry-After` y usar en todos los casos la espera creciente | Se le vuelve a llamar antes de lo que pidió, cuando ya avisó que está saturado |
| Respetar `Retry-After` sin tope | Un destino que pide una hora detiene esa orden (y, hasta la 27, al consumidor que la lleva) todo ese tiempo |
| Dejar que el cliente HTTP repita solo los 429 y 503 | Son llamadas al destino que no quedan en `intento`, y se suman a los reintentos del router |
| Escribir el intento después de la llamada | Si el router se cae en medio, no queda rastro de que se envió |
| Un Dead Letter Channel que descarte lo agotado | Lo agotado no se tira: queda en `conciliacion_pendiente`, en la misma base, a la vista |

## Qué nos haría reabrirla

- Que un destino ofrezca cómo preguntar por un envío (`GET` por la llave de idempotencia): la conciliación podría
  hacerla el router, sin una persona.
- Que un destino use otro 4xx para un problema pasajero, o un 429 para un «no» definitivo (por ejemplo, una cuota
  diaria agotada): la lista de códigos que se reintentan tendría que ser por destino y no una sola.
- Que un destino mande `Retry-After` como fecha HTTP: habría que convertirla a segundos en vez de usar la espera
  creciente.
- Que un destino pida esperas mayores al tope de forma habitual: el tope tendría que subir, y la orden pasar a
  esperar fuera de la cola (como desde la 28) en todas las lecciones.

## Fuentes

- Dead Letter Channel, de *Enterprise Integration Patterns*: https://www.enterpriseintegrationpatterns.com/patterns/messaging/DeadLetterChannel.html
- HTTP, códigos 4xx, 5xx y `504 Gateway Timeout` (RFC 9110): https://www.rfc-editor.org/rfc/rfc9110.html
- `408 Request Timeout` (RFC 9110, sección 15.5.9): https://www.rfc-editor.org/rfc/rfc9110.html#section-15.5.9
- `Retry-After` (RFC 9110, sección 10.2.3): https://www.rfc-editor.org/rfc/rfc9110.html#section-10.2.3
- `429 Too Many Requests` (RFC 6585, sección 4): https://www.rfc-editor.org/rfc/rfc6585.html#section-4
- `425 Too Early` (RFC 8470, sección 5.2): https://www.rfc-editor.org/rfc/rfc8470.html#section-5.2
- Componente HTTP de Camel (`responseTimeout`, `automaticRetriesDisabled`): https://camel.apache.org/components/next/http-component.html
- Dónde Camel 4.22.1 lee `CamelRedeliveryDelay` para la espera del reintento (`determineRedeliveryDelay`): https://github.com/apache/camel/blob/camel-4.22.1/core/camel-core-processor/src/main/java/org/apache/camel/processor/errorhandler/RedeliveryErrorHandler.java
- The Idempotency-Key HTTP Header Field (borrador del IETF): https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/
