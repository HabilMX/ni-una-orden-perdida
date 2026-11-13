# Se reintenta en el lugar, solo en el router, con espera creciente

> **Dónde aparece:** lección 19.

## El problema

La API está apagada cuando llega la orden 4001. El canal rest no sabe si la API la recibió: no contestó. Si el router
lo deja así, la orden no llega a la API aunque la API vuelva en diez segundos. Si reintenta sin pausa, la
golpea mientras arranca. Y si cada pieza (router, int-rest) reintenta por su cuenta, los reintentos se multiplican.

## Qué decidimos

- **Solo el router reintenta.** Los canales contestan lo que les pasó y ya.
- **Solo se repite lo que quedó «no se sabe»** (sin respuesta, o un 5xx). Un 4xx es un «no» del destino y no se
  repite.
- **Se repite el intento completo**: la ruta `intentar` escribe su renglón en `intento` antes de enviar, llama al
  canal y anota el desenlace. Si queda «no se sabe», lanza un error, y la `redeliveryPolicy` de `enviar` la vuelve a
  llamar: cada reintento deja su renglón, con su hora.
- **Espera creciente con un poco de azar**: 1 s, 2 s, 4 s, 8 s, 16 s (`useExponentialBackOff`, `backOffMultiplier: 2`),
  con 15 % de variación (`useCollisionAvoidance`). Cinco reintentos; agotados, la operación se queda en `no_se_sabe`.
- **En el lugar**: la cola `seda:repartir` tiene un solo consumidor y la espera lo detiene. Lo que viene detrás espera.

## Por qué

- **Un reintento en el lugar conserva el orden.** El embarque de la 4001 no le llega a la API antes que la orden.
- **La espera creciente le da tiempo al destino** para volver, sin golpearlo mientras arranca; el azar evita que dos
  routers reintenten justo al mismo tiempo.
- **Un renglón por intento** deja ver en la base cuántas veces se intentó, cuándo, y qué pasó cada vez.
- **Reintentar un 4xx no cambia la respuesta**: el destino ya dijo que no.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Reintentar en cada canal y también en el router | Cinco reintentos en dos piezas son hasta 36 llamadas por un solo envío |
| Espera fija | O espera de más cuando el destino ya volvió, o lo golpea mientras arranca |
| Reintento asíncrono (`asyncDelayedRedelivery`) | Libera la cola mientras espera: lo que venía detrás puede llegar antes y se pierde el orden |
| Mandar el fallo a otra cola y reintentar después | También rompe el orden; tiene sentido cuando el orden no importa (lección 29 en adelante) |

## Qué nos haría reabrirla

- Que un destino lento detenga demasiado a los demás: el costo de esperar en el lugar se vuelve mayor que el de
  perder el orden. Ahí entra separar la cola por canal, o un cortacircuito (lección 22).
- Que el destino diga cuánto esperar (`Retry-After`): habría que respetarlo en vez de calcularlo.

## Fuentes

- Redelivery Policy de Camel: https://camel.apache.org/manual/error-handler.html
- Exception Clause (`onException`) de Camel: https://camel.apache.org/manual/exception-clause.html
- Route Configuration de Camel: https://camel.apache.org/manual/route-configuration.html
- Guaranteed Delivery, de *Enterprise Integration Patterns*: https://www.enterpriseintegrationpatterns.com/patterns/messaging/GuaranteedMessaging.html
