# El offset se confirma a mano, después de dejar el desenlace, y lo que falla se vuelve a leer

> **Dónde aparece:** lección 29 en adelante.

## El problema

Desde la 28, cada integración lee el tópico `ordenes` con su grupo, y Kafka lleva la cuenta de hasta dónde leyó cada
grupo: su *offset*. Por omisión, el consumidor la confirma solo (`enable.auto.commit`) con lo que ya leyó. Y cuando un
evento falla a la mitad (el canal lo entregó, pero Kafka no acepta su desenlace), Camel lo anota en el log y sigue con
el siguiente (`breakOnFirstError: false`, lo de omisión): la cuenta avanza por encima del que falló. La API ya tiene
el pedido, pero el router no se entera de cómo le fue, la operación se queda «enviada» y nadie vuelve a leer ese
evento. Para el router, se perdió.

## Qué decidimos

- Los tres puentes (`kafka.camel.yaml`) y el consumidor de `desenlaces` del router leen con
  `autoCommitEnable: false`, `allowManualCommit: true` y `breakOnFirstError: true`.
- Cada evento se confirma **a mano, al final**: después de entregarlo al canal y de dejar su desenlace en el tópico
  `desenlaces` (en el router, después de anotarlo en la base). Camel deja en el encabezado `CamelKafkaManualCommit`
  con qué confirmarlo; el puente lo guarda en una variable al empezar (la entrega limpia los encabezados) y al final
  llama a su `commit()`, que confirma de forma síncrona ese evento y los anteriores de su partición.
- Si el evento falla, no se confirma, y con `breakOnFirstError` Camel no sigue con el siguiente: regresa al que falló y
  lo vuelve a intentar.
- Un evento que no es para el canal también se confirma, para que la cuenta del grupo avance.
- Lo que se lea dos veces lo reconoce el destino: la llave de idempotencia de la API (lección 02), el «ya registrado»
  del ERP (lección 23) y el desenlace que solo cambia una operación que sigue `enviada` (lección 28).

## Por qué

- **Confirmar después de terminar es «al menos una vez»**: lo que no terminó se vuelve a leer. Para una orden,
  repetir se arregla con la llave; perder no se arregla.
- **Al final, y no al leer**: el desenlace es la prueba de que el canal terminó. Sin él, el router no sabe cómo quedó
  la operación.
- **El `verifica` mide en los destinos y en la base**: le baja al tópico `desenlaces` el tamaño máximo de mensaje
  para que Kafka no acepte ninguno, manda tres órdenes y comprueba que la API y el ERP las tienen y que el router no
  se enteró (control); lo regresa, y cuenta que el router se enteró de las seis operaciones y que la API creó cada
  pedido una sola vez. Con el `inicio/` de la lección, las seis se quedan «enviada».
- **Lo que se midió y conviene saber**: Camel entrega cada evento de un lote antes de pedir el siguiente, y la
  confirmación automática ocurre al pedirlo. Por eso, en esta práctica, detener a int-rest a la mitad de un lote o
  matarlo sin aviso no perdió eventos ni con la confirmación automática: al volver, leyó otra vez lo que no había
  terminado. El `verifica` lo mata a la mitad de una entrega y cuenta en el log de la API que cada pedido se creó una
  vez (el repetido contesta 200, «reenvío»). Lo que la confirmación automática sí pierde es el evento que falla.

## El costo

Un evento que no se puede terminar detiene su partición: Camel lo reintenta, y lo que viene detrás espera a que
salga. Por eso el puente deja un desenlace aunque el canal no conteste («no se sabe», código 0), y quien reintenta la
entrega es el router (ficha 32). Lo que hace fallar al puente es no poder escribir en Kafka, y eso se arregla cuando
Kafka vuelve. Si un canal pudiera atorarse por un evento malo, ese evento iría a un tópico aparte, a la vista, y el
resto seguiría.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Dejar la confirmación automática | Un evento que falla queda atrás confirmado y no se vuelve a leer (el `inicio/` lo muestra) |
| Confirmar a mano sin `breakOnFirstError` | Camel sigue con el siguiente; al confirmar ése, la cuenta pasa por encima del que falló |
| Confirmar al leer, antes de entregar | Si algo falla o muere a la mitad, el evento queda como leído y no llegó |
| Confirmar en segundo plano (asíncrono) | Si muere justo después, no se sabe si la confirmación llegó; la síncrona espera la respuesta del broker |
| Transacciones de Kafka (exactamente una vez) | Cubren lo que pasa dentro de Kafka; la API, el ERP y el SFTP están fuera, y ahí cuida la llave |

## Qué nos haría reabrirla

- Que un canal tarde tanto por evento que confirmar uno por uno pese: se confirmaría cada tantos eventos, con la misma
  regla (solo lo terminado).
- Que un destino no pueda reconocer un repetido: habría que guardar en el canal qué eventos ya entregó. Pasa con el
  850 del socio: el SFTP no pisa un archivo que ya está, así que un repetido no se duplica pero queda «no se sabe» y,
  agotados los reintentos, en conciliación.
- Que un evento se atore por su contenido: un tópico aparte para lo que no se puede terminar.

## Fuentes

- Componente Kafka de Camel (`allowManualCommit`, `autoCommitEnable`, `breakOnFirstError`, `KafkaManualCommit`): https://camel.apache.org/components/next/kafka-component.html
- KafkaConsumer, confirmación de offsets y garantías al leer: https://kafka.apache.org/44/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html
- `enable.auto.commit` en la configuración del consumidor: https://kafka.apache.org/documentation/#consumerconfigs_enable.auto.commit
- Garantías de entrega de Kafka (a lo más, al menos, exactamente una vez): https://kafka.apache.org/documentation/#semantics
- EIP script de Camel: https://camel.apache.org/components/next/eips/script-eip.html
- Guaranteed Delivery: https://www.enterpriseintegrationpatterns.com/patterns/messaging/GuaranteedMessaging.html
