# Lección 28 — Un tópico, un grupo por integración

> **Lo que vas a lograr:** que el router deje de llamar a cada canal y espere su respuesta. En vez de eso, publica cada
> momento de la orden en un **tópico** de Kafka, y cada integración lo lee a su paso. Si el socio está detenido una
> hora, la API y el ERP siguen, y al volver el socio se pone al día solo: nadie le reenvía nada.

**Antes de empezar necesitas:** las lecciones 12, 17 y 27 terminadas y el ambiente de la 00 encendido. Y Kafka, que
desde hoy se enciende aparte (una vez):

```bash
docker compose -f infra/compose.yaml --profile kafka up -d --wait
```

## Lo nuevo de hoy, en una frase

Hasta la 27, el router le habla a cada canal por HTTP. Si int-edi está detenido, el router reintenta, abre el
cortacircuito y al final deja la operación en conciliación: alguien tiene que hacer un rerun a mano cuando int-edi
vuelve. El router carga con que cada canal esté arriba. Hoy eso cambia: el router **publica** un evento por momento
en el tópico `ordenes`, y cada integración lo **lee** cuando puede. Dos ideas hacen que funcione: la **llave** del
evento y el **grupo** de cada integración.

**La imagen:** el periódico de la colonia. El repartidor deja un ejemplar en cada casa, no uno para toda la calle.
Si en una casa se fueron de vacaciones, los periódicos se juntan en la puerta y, al volver, los leen en orden; las
otras casas no esperaron a nadie. Y en una casa con tres hermanos, llega **un** periódico: lo lee el que lo agarre
primero, no tres.

| En la imagen | En la práctica |
|---|---|
| El periódico de cada día | Un evento: un momento de una orden |
| Cada casa | Un grupo: `int-rest`, `int-bd`, `int-edi` |
| Los hermanos de una casa | Las 3 copias de int-bd, que comparten su grupo |
| Los periódicos juntados en la puerta | Lo que el grupo todavía no lee: Kafka se lo guarda |

---

## 1. Prepara tu carpeta

```bash
cd lecciones/28-un-topico-un-grupo-por-integracion
cp -R inicio mio
```

Desde hoy hay piezas nuevas. Léelas antes de escribir:

| Archivo | Qué hace | ¿Lo escribes? |
|---|---|---|
| `orden-router/reparto.camel.yaml` | Ya no llama a los canales: publica un evento en el tópico `ordenes` | **Sí, el TODO 1** |
| `int-rest/kafka.camel.yaml`, `int-bd/…`, `int-edi/…` | El **puente** de cada integración: lee el tópico y le entrega el evento a su ruta de siempre (`/integrar`, `/embarque`, `/entrega`) | **Sí, el TODO 2** |
| `orden-router/desenlaces.camel.yaml` | Lo que contestó cada canal regresa por otro tópico, `desenlaces`, y solo lo lee el router | No, léelo |

Lo que cada canal hace con su destino **no cambió**: solo cómo le llega el trabajo. El router sigue siendo el único
que escribe en la base de estado, y sigue decidiendo a qué canales les toca cada orden (la 11): el evento lleva la
operación y el intento de cada canal que le toca, y un puente que no encuentra los suyos lo deja pasar.

Dos cosas que se notan desde hoy:

- **El cortacircuito del router (la 22) ya no está.** Su trabajo era no insistirle a un canal caído; ahora el router
  no le habla al canal, así que no hay a quién insistirle. Kafka guarda el evento hasta que el canal lo lea.
- **El rerun (la 23) contesta `202` «enviado»** en cuanto Kafka recibió el evento, no cuando el canal terminó.

Abre `infra/compose.yaml` y busca `KAFKA_NUM_PARTITIONS: 3`, y en el `compose.yaml` de la lección, `replicas: 3`.

## 2. Predice

Contesta en un papel:

1. Las tres integraciones leen con el **mismo** grupo, `integraciones`. ¿Cuántas de ellas reciben la 4001?
2. Si int-edi está detenido y llegan la 4002 y la 4003, ¿qué pasa con la API y el ERP? ¿Y qué pasa con los 850 del
   socio cuando int-edi vuelve?
3. El tópico tiene 3 particiones e int-bd corre en 3 copias. Si un evento **no lleva llave**, ¿los momentos de la
   misma orden pueden caer en particiones distintas? ¿Por qué eso importaría?

Mira cómo está hoy:

```bash
./verifica inicio
```

La primera marca de entrega sale en rojo: la 4001 llega a **uno** de sus tres canales, y los otros dos se quedan en
`enviada`. Con un solo grupo, Kafka trata a int-rest, int-bd e int-edi como tres copias de lo mismo, y le da cada
evento a una sola. Y la última marca también: ningún evento lleva la orden como llave.

## 3. Pon la llave y separa los grupos

### TODO 1: la llave del evento

En `mio/orden-router/reparto.camel.yaml`, busca el TODO 1, justo antes del `to: kafka:ordenes`. El componente kafka
de Camel toma la llave del encabezado `CamelKafkaKey`. La orden ya está en la variable `orden`:

```yaml
- setHeader:
    name: CamelKafkaKey
    expression:
      simple:
        expression: "${variable.orden}"
```

Kafka manda a la **misma partición** todo lo que lleva la misma llave, y dentro de una partición el orden se
respeta. Así, el creada, el embarcada y el entregada de la 4001 se leen en el orden en que se publicaron. Una
partición la lee una sola copia de cada grupo a la vez: si el grupo se reacomoda (una copia se cae o llega otra), la
puede seguir otra copia, pero en el mismo orden. Sin llave, cada evento cae donde sea, y dos copias distintas podrían atender dos momentos
de la misma orden, cada una a su paso.

### TODO 2: un grupo por integración

En los tres `kafka.camel.yaml`, cambia `groupId: integraciones` por el nombre de la integración:

| Archivo | `groupId` |
|---|---|
| `mio/int-rest/kafka.camel.yaml` | `int-rest` |
| `mio/int-bd/kafka.camel.yaml` | `int-bd` |
| `mio/int-edi/kafka.camel.yaml` | `int-edi` |

Con eso tienes las dos formas de repartir que da Kafka, al mismo tiempo:

| | Entre grupos distintos | Entre copias del mismo grupo |
|---|---|---|
| Quién recibe un evento | **Todos** los grupos, cada uno su copia | **Una** sola copia |
| El patrón se llama | *Publish-Subscribe* | *Competing Consumers* |
| En la práctica | La API, el ERP y el socio reciben cada orden | Las 3 copias de int-bd se reparten las 3 particiones |

Cada grupo lleva **su propia cuenta** de hasta dónde leyó: el *offset*. Por eso un grupo detenido no detiene a los
demás, y al volver sigue donde se quedó.

## 4. Rompe uno a propósito

Con tu versión terminada, quita el `setHeader` de `CamelKafkaKey` y corre `./verifica`.

Antes, predice: las tres integraciones tienen su grupo. ¿Llegan las órdenes?

Llegan, y casi todas las marcas salen verdes. Solo sale en rojo la última: los eventos ya no llevan la orden como
llave. Con una orden a la vez no se nota nada, y por eso este error es traicionero: aparece el día en que dos
momentos de la misma orden llegan juntos y dos copias distintas de int-bd los atienden. La máquina de estados de la
17 estaciona el que llega antes de tiempo, pero la llave evita que pase. **Que hoy funcione no prueba que el orden
esté garantizado.** Regresa la llave.

## 5. Comprueba

```bash
./verifica
```

`verifica` recrea Kafka vacío, manda la 4001 con todo arriba, **detiene int-edi**, manda la 4002 y la 4003, revisa
que la API y el ERP siguieron y que el socio no tiene sus 850, vuelve a prender int-edi y espera a que se ponga al
día. Al final lee el tópico entero con un lector aparte para revisar la llave de cada evento. Está terminada cuando
ves las siete marcas verdes:

```
✅ Arrancaron el router (que publica en Kafka), los tres canales con su puente y el sondeo
✅ La 4001 llegó por Kafka a sus tres canales: la API, el ERP y el socio
✅ Con int-edi detenido, la API y el ERP recibieron la 4002 y la 4003 (API 2 de 2, ERP 2 de 2)
✅ El socio todavía no tiene sus 850, y Kafka se los guarda: el grupo int-edi va 2 eventos atrás
✅ int-edi volvió y se puso al día solo: 2 archivos 850 en el socio, y el router no reenvió nada (2 intentos para 2 operaciones)
✅ Los tres grupos (int-rest, int-bd, int-edi) quedaron al día: 0 eventos por leer
✅ Los 3 eventos del tópico ordenes llevan la orden como llave
```

Lee la quinta: «2 intentos para 2 operaciones». Hasta la 27, un canal caído terminaba en conciliación y un rerun a
mano. Hoy nadie hizo nada: int-edi leyó lo que Kafka le guardó.

Mira tú cuánto lleva leído cada grupo, con los servicios arriba:

```bash
docker compose -f ../../infra/compose.yaml exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --all-groups
```

La columna `LAG` es lo que le falta leer a cada grupo en cada partición.

**¿No te corre?** `./verifica solucion` prueba la nuestra, y `diff -r mio solucion` te dice en qué son distintas.

| Lo que ves | Qué haces |
|---|---|
| `Kafka no levantó` | Enciéndelo a mano con el comando de «Antes de empezar» y vuelve a intentar |
| `A la base de estado le faltan tablas de lecciones anteriores` | Tu base es de antes: recréala con el comando que trae el mensaje (`--force-recreate -V estado`) |
| `La 4001 no llegó a sus tres canales` con alguno en `enviada` | Ese canal no tiene su propio grupo: revisa el `groupId` de su `kafka.camel.yaml` |
| `Con int-edi detenido, la API y el ERP debían seguir` | Revisa el `groupId` de int-rest e int-bd: si comparten grupo con otro, se quitan los eventos entre sí |
| `el grupo int-edi va -1 atrás` | El grupo `int-edi` no existe: int-edi lee con otro nombre de grupo |
| `De … eventos del tópico ordenes, … llevan la orden como llave` | Falta el TODO 1, o el `setHeader` quedó después del `to: kafka:ordenes` |
| `El puerto 8080 está ocupado` | Quedaron arriba los servicios de otra corrida: `DONDE=mio docker compose -f compose.yaml down` |

---

## Lo que esto enseña fuera de la práctica

**Publicar no es llamar.** Quien llama necesita que el otro esté arriba; quien publica solo necesita que el tópico
esté arriba. El router dejó de cargar con la disponibilidad de cada canal, y eso es lo que se llama **desacoplar**.

**El grupo decide quién recibe.** El mismo tópico sirve para repartir trabajo entre copias (un grupo) y para avisarle a
varios sistemas (varios grupos). Equivocarse de grupo es el error más común al empezar con Kafka, y no da error: los
eventos simplemente le llegan a otro.

**La llave decide el orden.** Kafka no promete orden en todo el tópico, solo dentro de una partición. Si algo tiene
que leerse en orden, tiene que llevar la misma llave.

**Lo que no se ha leído se guarda.** Un consumidor detenido no pierde nada mientras Kafka conserve el evento: al
volver, sigue desde su offset. Eso cambia qué significa «el socio estuvo caído una hora».

## Ejercicio: el periódico de la colonia

1. Con tus servicios arriba, detén int-bd (`DONDE=mio docker compose -f compose.yaml stop int-bd`), manda una orden
   y corre el comando de `kafka-consumer-groups.sh`. ¿Qué grupo tiene `LAG`? Préndelo y vuelve a correrlo.
2. En papel: si mañana el socio pide recibir también los avisos de entrega, y alguien de finanzas quiere un sistema
   nuevo que lea **todas** las órdenes, ¿cuántos grupos hay? ¿Qué cambias en el router para el sistema nuevo?
3. Las 3 copias de int-bd se reparten 3 particiones. ¿Qué pasaría si corrieras 5 copias? (Pista: una partición la
   lee una sola copia de cada grupo.)

---

## Por qué se hizo así

Lo que decidimos en esta lección, con lo que descartamos y qué nos haría cambiar de idea:

- [Un evento por momento en un tópico, con la orden como llave, y un grupo de consumidores por integración](../decisiones/32-un-topico-un-grupo-por-integracion.md)

## Repaso

1. ¿Qué es un tópico, y qué cambia para el router al publicar en vez de llamar?
2. ¿Qué hace la llave de un evento? ¿Por qué la llave es la orden?
3. ¿Qué diferencia hay entre dos copias del mismo grupo y dos grupos distintos?
4. ¿Qué es el offset, y por qué un grupo detenido no detiene a los demás?
5. **La pregunta de cierre:** el socio estuvo detenido una hora. Explica, paso por paso, por qué hoy no hizo falta un
   rerun y hasta la 27 sí.

**Siguiente:** en la lección 29 el problema se mueve al otro lado: ¿qué pasa si int-bd lee un evento y no alcanza a
dejar su desenlace? ¿Kafka da el evento por leído?
