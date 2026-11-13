# Un evento por momento en un tópico, con la orden como llave, y un grupo de consumidores por integración

> **Dónde aparece:** lección 28 en adelante.

## El problema

Hasta la 27, el router llama por HTTP a cada canal y espera su respuesta. Si int-edi está detenido, el router no
tiene a quién entregarle: reintenta, abre el cortacircuito de ese canal y, al agotar, deja la operación en
`conciliacion_pendiente`. Cuando int-edi vuelve, nadie le manda lo que se perdió mientras no estaba: una persona
tiene que reprocesarlo a mano. El router carga con la disponibilidad de cada canal.

## Qué decidimos

- **Un tópico, `ordenes`, y un evento por momento.** El router publica un solo evento cuando una orden se crea, se
  embarca o se entrega. El cuerpo es lo guardado (lo mismo que ya mandaba por HTTP a los tres canales).
- **La llave del evento es la orden** (`CamelKafkaKey`). Kafka pone en la misma partición todo lo que lleva la misma
  llave, y dentro de una partición respeta el orden: los momentos de una orden se leen en el orden en que se
  publicaron, y los lee siempre la misma copia de cada integración.
- **Un grupo de consumidores por integración**: `int-rest`, `int-bd`, `int-edi`. Cada grupo recibe todos los eventos
  y lleva su propia cuenta de hasta dónde leyó (su *offset*). Las tres copias de int-bd comparten su grupo y se
  reparten las tres particiones del tópico: entre ellas es *Competing Consumers*; entre integraciones es
  *Publish-Subscribe*.
- **A quién le toca cada evento lo sigue decidiendo el router** (lección 11): el evento lleva, para cada canal que le
  toca, su operación y su intento (`operacion-rest`, `intento-rest`…). Una integración que no encuentra los suyos lo
  deja pasar. Una tienda propia no lleva los del socio.
- **El desenlace regresa por otro tópico, `desenlaces`**, con la orden como llave, y solo lo lee el router (grupo
  `orden-router`). El router sigue siendo la única pieza que escribe en la base de estado. La operación pasa a
  `enviada` al publicar y cambia solo si sigue `enviada` cuando llega su desenlace: un desenlace repetido no pisa lo
  que ya se sabe.
- **Cada integración tiene un puente** (`kafka.camel.yaml`) que lee el tópico y entrega el evento a su propia ruta de
  siempre (`/integrar`, `/embarque`, `/entrega`) por HTTP local. Lo que el canal hace con su destino no cambia.
- **El sobre CloudEvents viaja en los encabezados de Kafka con guion bajo** (`ce_id`, `ce_type`…), como lo pide el
  enlace de CloudEvents para Kafka; el puente lo vuelve a escribir con guion (`ce-id`) para HTTP. Todo valor va como
  texto: el serializador de encabezados de Camel guardaría un número en binario.
- **El broker es la imagen oficial `apache/kafka`** (Apache 2.0), en modo KRaft, un solo nodo, bajo el perfil `kafka`
  de `infra/compose.yaml`. Las lecciones 00 a 27 no la levantan.

## Qué cambió en lo que ya existía

- **El cortacircuito por canal (ficha 26) sale del router.** Protegía la cola de reparto de un canal caído; ahora el
  router no llama a los canales, y un canal detenido solo atrasa a su propio grupo.
- **El reintento ya no es en el lugar (ficha 23).** Un «no se sabe» llega como desenlace, y el router vuelve a
  publicar el evento solo para ese canal, con la misma espera creciente (1 s, 2 s, 4 s… con azar) y el mismo tope de
  seis intentos. La espera no ocupa a nadie.
- **El reproceso a mano (ficha 27) contesta 202 «enviado»** en cuanto Kafka recibió el evento; su desenlace se ve
  después en el recorrido.
- **El reparto por conexión de la 12 (ficha 15) deja de hacer falta** hacia los canales: las copias de int-bd se
  reparten particiones, no llamadas.

## Por qué

- **Un canal detenido no frena a los demás, y se pone al día solo.** Kafka guarda el evento hasta que el grupo lo lea;
  el router no tiene que saber quién está arriba. El `verifica` detiene int-edi, comprueba en el broker que su grupo va
  atrasado y que la API y el ERP siguen, y al volver int-edi cuenta sus 850 y un solo intento por operación.
- **Un evento por momento y no uno por canal**: el router no reparte copias; publica un hecho, y cada integración que
  se interesa lo lee. Agregar un cuarto canal es agregar un grupo, no tocar el reparto.
- **La orden como llave**: el orden por orden importa (no se embarca lo que no se creó), y la llave lo cuida sin que
  nadie coordine a las copias. La máquina de estados de la 17 sigue estacionando lo que llega antes de tiempo.
- **El desenlace por tópico y no por HTTP de regreso**: el canal no necesita que el router esté arriba para contar
  cómo le fue, y la base de estado sigue teniendo un solo dueño.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Un tópico por canal (`ordenes-rest`, `ordenes-bd`…) | El router volvería a repartir copias; un canal nuevo pide un tópico nuevo y cambiar el router |
| Un solo grupo para las tres integraciones | Kafka le da cada evento a UNA de ellas: lo que lee int-bd ya no lo ve la API (el `inicio/` lo muestra) |
| Eventos sin llave | Dos momentos de la misma orden pueden caer en particiones distintas y leerse a destiempo |
| Que cada canal escriba su desenlace en la base de estado | La base tendría cuatro dueños; desde la 15 la escribe solo el router |
| Tópicos de reintento con espera | Más piezas para lo mismo: el router ya sabe esperar y cuántas veces lleva |
| Redpanda | Su edición comunitaria se publica con la Business Source License, que no es una licencia abierta, y la práctica pide licencias abiertas |
| Mantener HTTP y agregar Kafka al lado | Dos caminos para lo mismo; el alumno no sabría cuál cuenta |

## Qué nos haría reabrirla

- Que un canal necesite un orden distinto al de la orden (por ejemplo, por tienda): cambiaría la llave.
- Que el volumen pida más de tres copias de int-bd: el tópico necesitaría más particiones (una copia sin partición se
  queda sin trabajo).
- Que un destino necesite protección contra ráfagas: el cortacircuito volvería, dentro de su integración, antes de
  llamar al destino.
- Que alguien fuera del router necesite los desenlaces: leerían `desenlaces` con su propio grupo, sin tocar la base.

## Fuentes

- Componente Kafka de Camel (grupos, llave, encabezados): https://camel.apache.org/components/next/kafka-component.html
- Documentación de Apache Kafka (tópicos, particiones, grupos de consumidores): https://kafka.apache.org/documentation/
- Imagen oficial de Apache Kafka: https://hub.docker.com/r/apache/kafka
- CloudEvents, enlace para Kafka (encabezados `ce_`): https://github.com/cloudevents/spec/blob/main/cloudevents/bindings/kafka-protocol-binding.md
- Competing Consumers: https://www.enterpriseintegrationpatterns.com/patterns/messaging/CompetingConsumers.html
- Publish-Subscribe Channel: https://www.enterpriseintegrationpatterns.com/patterns/messaging/PublishSubscribeChannel.html
- Licencia de Redpanda (BSL): https://github.com/redpanda-data/redpanda/blob/dev/licenses/bsl.md
- Licencia Apache 2.0: https://www.apache.org/licenses/LICENSE-2.0
