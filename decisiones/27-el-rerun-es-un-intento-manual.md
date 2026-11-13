# El reproceso a mano es un intento más, con nombre y motivo

> **Dónde aparece:** lección 23.

## El problema

Después de una caída queda trabajo atorado: el canal del socio en `pendiente` porque su cortacircuito estaba
abierto, o la API en `conciliacion_pendiente` porque no contestó a tiempo y no se supo si registró. Alguien arregla
la causa y tiene que volver a mandar **solo eso**, sin repetir lo que ya salió bien y sin crear un pedido doble en un
destino que sí lo había recibido. Y mañana, quien lea el rastro tiene que saber qué hizo el reparto solo y qué pidió
una persona.

## Qué decidimos

- Una sola puerta: `POST /rerun` en el router, con `{orden, momento, canal, pedido_por, motivo}`. Sin `pedido_por`
  o sin `motivo` contesta 400.
- **Solo se reprocesa lo que falló**: `pendiente`, `no_se_sabe`, `conciliacion_pendiente` o `rechazada`. Lo
  `aceptado` o `confirmado` contesta 409, y lo `estacionado` también: eso lo suelta el barrido (lección 17).
- El reproceso es **un intento más de la misma operación**, con `tipo = manual`, `pedido_por` y `motivo`. Se arma con
  las mismas rutas del reparto (`preparar` e `intentar`): mismo sobre, mismo número de control, misma huella.
- **Un solo intento, en ese momento**, sin reintentos en el lugar: quien lo pidió ve el desenlace, el código y la
  versión del mapeo en la respuesta. Si no se supo, la operación vuelve a `conciliacion_pendiente`.
- Sale con **la configuración vigente** (el mapeo y la plantilla desplegados hoy), y la versión que contestó el canal
  queda en `intento.version_mapeo`.
- **Los duplicados los evita cada destino con su llave**: la API contesta 200 «reenvío» a la misma
  `Idempotency-Key` con el mismo contenido; el ERP contesta 200 «ya registrado» a un pedido que ya tiene con el mismo
  total (y 409 si el total es otro); el 850 sale con el mismo número de control de la operación, que es lo que el
  X12 le da al socio para reconocer un repetido (el socio de la práctica no lo revisa).

## Por qué

- **Un intento más, no un camino aparte**: el rastro de una operación cuenta su historia completa en una tabla, y el
  reproceso pasa por el mismo código que el reparto. Un camino aparte terminaría mandando otra cosa.
- **Nombre y motivo obligatorios**, porque un intento manual sin ellos se ve igual que uno automático.
- **La configuración vigente**, porque se reprocesa después de arreglar algo: mandar con el mapeo del primer intento
  repetiría el error. Por eso la versión se anota, para saber con cuál salió cada intento.
- **El router no basta para evitar el doble**: cuando la operación está en «no se sabe», el router no sabe si el
  destino la tiene. Solo el destino lo sabe, y lo dice con su llave de idempotencia.
- **Negarse a reprocesar lo aceptado** evita reabrir una operación que estaba bien: mandarla otra vez solo puede
  terminar en el mismo «sí» o en un error nuevo.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Marcar la operación como `pendiente` y devolverla al reparto | El intento sale como «auto», sin quién ni por qué; reabre lo que ya estaba aceptado; quien lo pidió no ve el desenlace |
| Reprocesar la orden completa, los tres canales | Manda otra vez lo que ya salió bien; solo la idempotencia de cada destino evitaría el doble |
| Reintentos en el lugar también en el rerun | La persona que lo pidió esperaría medio minuto sin saber nada; el rerun se puede volver a pedir |
| Reprocesar con la versión del mapeo del primer intento | Se reprocesa porque algo se arregló; el arreglo no saldría |
| Que el router consulte al destino antes de reenviar | Cada destino tendría que exponer una consulta, y entre la consulta y el envío puede cambiar; la llave de idempotencia lo resuelve en un solo paso |

## Qué nos haría reabrirla

- Que haya que reprocesar muchas operaciones a la vez después de una caída: un rerun por lote (por canal y rango de
  horas) que llame a esta misma puerta una por una.
- Que el socio no reconozca un 850 repetido por su número de control: el canal del socio necesitaría su propia
  revisión antes de depositar.
- Que el rerun lo pidan sistemas y no personas: `pedido_por` tendría que venir de una identidad autenticada, no de un
  campo del cuerpo.

## Fuentes

- Borrador del IETF del encabezado `Idempotency-Key`: https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/
- Idempotent Receiver, en Enterprise Integration Patterns: https://www.enterpriseintegrationpatterns.com/patterns/messaging/IdempotentReceiver.html
- Message Store, en Enterprise Integration Patterns: https://www.enterpriseintegrationpatterns.com/patterns/messaging/MessageStore.html
- `doTry` / `doCatch` en Camel: https://camel.apache.org/manual/try-catch-finally.html
- Componente `platform-http` de Camel: https://camel.apache.org/components/next/platform-http-component.html
