# Lo que hay que publicar se guarda en la misma transacción (outbox), y un sondeo lo publica

> **Dónde aparece:** lección 30.

## El problema

Desde la 28, el router guarda la orden en la base de estado, contesta 202 a la tienda y DESPUÉS publica el evento en
Kafka. Son dos sistemas, y no hay una transacción que abarque a los dos. Si Kafka está caído justo en ese momento, la
orden quedó guardada y la tienda ya tiene su 202, pero el evento no salió. Nadie lo vuelve a intentar: el reparto se
disparó una vez, al guardar, y la operación se queda pendiente. Al revés tampoco sirve: publicar antes de
guardar deja un evento de una orden que, si la base falla, no existe.

## Qué decidimos

- **Una tabla `outbox`** en la base de estado (`infra/postgres/01-estado.sql`): una fila por operación que hay que
  publicar, con quién lo pidió (`tipo`, `pedido_por`, `motivo`) y cuándo salió (`publicado`, vacío mientras no).
- **La fila se escribe en la MISMA transacción que la operación**: al guardar una orden (`guardar-orden`) y al guardar
  un momento nuevo (`guardar-momento`, solo lo que nace «pendiente»). O quedan las dos cosas, o ninguna.
- **Un sondeo del router** (`sondeo-outbox`, cada segundo) publica lo que el outbox tenga sin publicar, en el orden en
  que entró, y junta en un solo evento las filas del mismo momento (como en la 28). Le pone `publicado` solo cuando
  Kafka recibió el evento. Si no lo recibió, la fila se queda, y las que vienen detrás esperan a la siguiente vuelta:
  así lo de una orden no sale en desorden.
- **Una fila sin `publicado` se vuelve a publicar, aunque su operación diga «enviada»**: mientras la fila no tenga
  `publicado`, nadie sabe si Kafka recibió el evento (el router pudo caerse con el envío en el aire). Solo no se
  vuelve a mandar lo que ya llegó a su destino (`aceptada`, `confirmada`) o lo que espera al barrido (`estacionada`);
  esa fila queda publicada sin mandar nada.
- **Una operación tiene a lo más una fila esperando.** El reintento de un «no se sabe» y el reproceso a mano no dejan
  otra si ya hay una; el reproceso contesta 409 «ya tiene una fila en el outbox». Así lo mismo no sale dos veces en
  una vuelta.
- **El sondeo da una vuelta a la vez** (`synchronous: true` en su `timer`). Kafka contesta en otro hilo; sin esa
  opción, la vuelta siguiente arranca mientras la anterior todavía espera a Kafka, y las dos toman las mismas filas.
- **Todo lo que sale a Kafka sale por el outbox**: el barrido que suelta lo estacionado (una sola sentencia que la
  cambia a «pendiente» y escribe su fila), el reintento de un «no se sabe» y el reproceso a mano, que ahora contesta
  202 «en el outbox».
- Al publicar, todo sigue como en la 28: el intento se escribe antes, y si Kafka no lo recibe queda `no_enviado` y la
  operación vuelve a «pendiente».

## Por qué

- **La base y Kafka no comparten transacción; la base consigo misma, sí.** Guardar «hay que publicar esto» junto con
  la operación convierte dos escrituras en dos sistemas en una sola escritura, y deja la publicación como un trabajo
  pendiente que se puede repetir hasta que salga.
- **Se prefiere repetir a perder**: si el router se cae con el envío en el aire, o después de que Kafka recibió el
  evento y antes de anotar `publicado`, la fila sale otra vez. Los destinos reconocen el repetido con su llave
  (lección 18), y los canales confirman su offset al terminar (ficha 33).
- **El `verifica` mide en los destinos**: tira Kafka, manda dos órdenes, comprueba que el router contestó 202, que el
  outbox tiene seis filas sin publicar y que la API no tiene nada (control); levanta Kafka y cuenta que la API, el ERP
  y el socio tienen las dos, que la API creó cada una una sola vez y que el outbox quedó sin pendientes.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Publicar justo después de guardar (la 28 y la 29) | Con Kafka caído en ese momento, lo guardado se queda sin salir (el `inicio/` lo muestra) |
| Publicar antes de guardar | Si la base falla, sale un evento de una orden que no existe |
| Una transacción que abarque la base y Kafka (en dos fases) | Pide un coordinador entre los dos y más viajes por cada orden, para lo que una tabla resuelve |
| Dejar que el reloj arranque una vuelta por segundo aunque la anterior no termine (lo de omisión) | Dos vueltas toman las mismas filas: lo mismo sale dos veces, y lo de una orden puede salir en desorden |
| Dar la fila por publicada si su operación ya dice «enviada» | «Enviada» se escribe ANTES de publicar: si Kafka no lo recibió o el router se cayó a la mitad, la fila queda marcada y nadie publica la orden |
| Reintentar la publicación en la memoria del router | Si el router se reinicia mientras Kafka está caído, se pierde lo que esperaba |
| Leer el registro de cambios de PostgreSQL (CDC) | Agrega una pieza más (un conector) para lo que una tabla y un sondeo resuelven aquí |

## Qué nos haría reabrirla

- Que el router corra en más de una copia: dos sondeos publicarían la misma fila. Cada uno tomaría sus filas con
  `FOR UPDATE SKIP LOCKED`, o el sondeo sería uno solo (como el de la bodega, lección 13).
- Que el volumen haga que un segundo de espera pese: se sondearía más seguido, o se pasaría a leer los cambios de la
  base (CDC).
- Que el outbox crezca sin fin: se borrarían las filas publicadas después de un plazo.

## Fuentes

- Patrón Transactional Outbox: https://microservices.io/patterns/data/transactional-outbox.html
- Garantías de entrega de Kafka: https://kafka.apache.org/documentation/#semantics
- Componente Kafka de Camel (`maxBlockMs`, `deliveryTimeoutMs`): https://camel.apache.org/components/next/kafka-component.html
- Componente Timer de Camel: https://camel.apache.org/components/next/timer-component.html
- Guaranteed Delivery: https://www.enterpriseintegrationpatterns.com/patterns/messaging/GuaranteedMessaging.html
