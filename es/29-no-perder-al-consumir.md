# Lección 29 — No perder al consumir

> **Lo que vas a lograr:** que un evento que una integración leyó pero no terminó (entregó la orden, pero no pudo
> dejar su desenlace) **se vuelva a leer**, en vez de darse por leído. Y que leerlo dos veces no cree la orden dos
> veces en la API.

**Antes de empezar necesitas:** las lecciones 18 y 28 terminadas, el ambiente de la 00 encendido y Kafka arriba
(`docker compose -f infra/compose.yaml --profile kafka up -d --wait`).

## Lo nuevo de hoy, en una frase

En la 28 cada grupo lleva su cuenta de hasta dónde leyó: el **offset**. Hasta hoy esa cuenta la confirma Kafka solo
(`autoCommitEnable`, que viene encendido), con lo que el consumidor ya leyó, haya terminado o no. Y cuando un evento
falla a la mitad, Camel lo anota en el log y sigue con el siguiente: la cuenta avanza **por encima** del que falló.
La API ya tiene el pedido, pero el router nunca se entera de cómo le fue, y nadie vuelve a leer ese evento. Hoy el
offset se confirma **a mano**, y solo cuando el trabajo terminó.

**La imagen:** el separador de un libro. Si lo avanzas cada vez que pasas la página, aunque te hayas quedado dormido a
la mitad del capítulo, mañana empiezas en el capítulo siguiente y lo que no leíste ya no lo lees. Si lo mueves solo
cuando **terminaste** el capítulo, lo peor que pasa es que releas unas páginas.

```
leer el evento → entregarlo al canal → publicar el desenlace → confirmar el offset
                                                                ↑ si algo falla antes de aquí, se vuelve a leer
```

---

## 1. Prepara tu carpeta

```bash
cd lecciones/29-no-perder-al-consumir
cp -R inicio mio
```

Tu trabajo va en los tres puentes: `mio/int-rest/kafka.camel.yaml`, `mio/int-bd/kafka.camel.yaml` y
`mio/int-edi/kafka.camel.yaml`. Los tres se cambian igual.

Antes, abre `mio/orden-router/desenlaces.camel.yaml`: el router **ya** lee el tópico `desenlaces` así. Te sirve de
modelo.

## 2. Predice

Contesta en un papel:

1. La API recibió la orden 2001 y le contestó `201` a int-rest, pero Kafka no aceptó el desenlace que int-rest quiso
   publicar. Con la confirmación automática, ¿el router se entera de que la 2001 llegó? ¿Alguien vuelve a leer ese
   evento?
2. Si confirmas el offset solo al final, y matan a int-rest después de entregar pero antes de confirmar, ¿qué pasa al
   volver? ¿La 2001 se crea dos veces en la API? (Pista: la 18.)
3. Si un evento no se puede terminar nunca, ¿qué les pasa a los que vienen detrás de él en su partición?

Mira cómo está hoy:

```bash
./verifica inicio
```

La verifica le pone a Kafka una trampa: hace que el tópico `desenlaces` no acepte nada. Manda tres órdenes, ve que la
API y el ERP las reciben y que el router no se entera de ninguna. Luego quita la trampa. Con la confirmación
automática, el router se queda sin saber de **ninguna** de las 6 operaciones: los eventos ya estaban confirmados.

## 3. Confirma cuando terminaste

En cada puente hay tres TODO.

### TODO 1: las tres opciones del consumidor

En el `from: kafka:ordenes`, junto a `groupId`:

```yaml
autoCommitEnable: false      # Kafka ya no confirma solo
allowManualCommit: true      # el puente confirma cada evento cuando terminó con él
breakOnFirstError: true      # si un evento falla, se vuelve a leer ése, no se sigue con el siguiente
```

Las tres van juntas:

| Si falta… | Qué pasa |
|---|---|
| `autoCommitEnable: false` | Kafka confirma solo lo leído, y tu confirmación a mano no cambia nada |
| `allowManualCommit: true` | Camel no te da con qué confirmar (el TODO 2 no encuentra nada) |
| `breakOnFirstError: true` | Camel sigue con el siguiente evento y, al confirmar ése, confirma también el que falló |

Esa última fila es la que se olvida. Confirmar un evento le dice a Kafka «hasta aquí leí» en su partición: confirmar
el siguiente cubre al que falló.

### TODO 2: guardar con qué confirmar

Con `allowManualCommit`, Camel deja en el encabezado `CamelKafkaManualCommit` el objeto que confirma **este** evento.
Guárdalo en una variable, como primer paso, porque la entrega al canal limpia los encabezados:

```yaml
- setVariable:
    name: confirmar
    expression:
      simple:
        expression: "${header.CamelKafkaManualCommit}"
```

### TODO 3: confirmar al final

Al final de los pasos, después del `filter` (o sea, cuando el desenlace ya quedó en Kafka, o el evento no era para
este canal), llama a `commit()` sobre esa variable. Camel tiene un paso, `script`, que evalúa una expresión solo por lo
que hace:

```yaml
- script:
    expression:
      simple:
        expression: "${variable.confirmar.commit()}"
```

Antes de esto, no: lo que se confirma ya no se vuelve a leer. Repite los tres TODO en los otros dos puentes.

## 4. Rompe uno a propósito

Con tus tres puentes terminados, en el de int-rest mueve el `script` del TODO 3 **arriba** del `filter`, para que
confirme antes de entregar. Corre `./verifica`.

Antes, predice: las tres opciones siguen puestas. ¿Se vuelve a leer lo que falló?

No. Sale en rojo la marca de las 6 operaciones: el router no se entera de las de la API. El evento ya estaba
confirmado cuando falló su desenlace, así que `breakOnFirstError` regresa al offset confirmado, que ya está **después**
de él. **Las opciones dicen cómo se confirma; el lugar del `script` dice cuándo.** Y el cuándo es lo que importa.
Regrésalo al final.

## 5. Comprueba

```bash
./verifica
```

`verifica` hace dos experimentos. En el primero pone la trampa en `desenlaces`, manda la 2001, la 2003 y la 2004, y
la quita. En el segundo detiene int-rest, manda la 2006, la 2008 y la 2009, pone a la API lenta (2.5 s por pedido),
prende int-rest y lo **mata** a la mitad de una entrega, para volver a prenderlo. Está terminada cuando ves las siete
marcas verdes:

```
✅ Arrancaron el router, los tres canales con su puente y el sondeo
✅ La 4001 llegó a sus tres canales
✅ Control: la API tiene (…) y el ERP (…) de las 3, y el router no se enteró de ninguna (sus desenlaces no se pudieron escribir)
✅ Kafka volvió a aceptar y el router se enteró de las 6 operaciones: lo que falló se volvió a leer
✅ En la API se creó cada una UNA vez (3 creaciones para 3 órdenes)
✅ Tras matar int-rest, la API tiene las 3 y las 3 operaciones quedaron aceptadas
✅ Cada una se creó UNA vez en la API (…)
```

La tercera es un **control**: antes de quitar la trampa, la verifica comprueba que de verdad falló algo. Si el router
ya supiera de las órdenes, la cuarta marca no probaría nada.

Fíjate en la tercera: la API puede tener 1, 2 o 3 de las órdenes. Con tu versión, un evento que no se puede terminar
**detiene su partición**: Camel lo reintenta, y lo que viene detrás en esa partición espera. Ése es el costo de no
perder.

La última marca tiene dos formas. Si el corte de int-rest dejó un evento sin confirmar, int-rest lo volvió a leer y la
API lo reconoció por su llave de idempotencia (la 18): contestó `200`, no `201`, y no creó nada nuevo. Si el corte cayó
justo después de confirmar, no hubo nada que releer. Las dos son correctas.

**¿No te corre?** `./verifica solucion` prueba la nuestra, y `diff -r mio solucion` te dice en qué son distintas.

| Lo que ves | Qué haces |
|---|---|
| `Kafka no levantó` | Enciéndelo a mano con el comando de «Antes de empezar» |
| `No arrancaron todas las rutas` | Revisa el YAML de tus puentes: el log que sale debajo dice qué archivo y qué línea |
| `El router se enteró de … de 6 operaciones: … se perdieron` | Falta alguna de las tres opciones del TODO 1, o el `script` no está al final |
| `Control: esperaba al menos 1 en la API y en el ERP y 0 enteradas en el router` | Si el router sí se enteró, la trampa no se puso: vuelve a correr la verifica. Si la API tiene 0, revisa que el puente siga entregando |
| `En la API hubo … creaciones para 3 órdenes` | La API creó alguna dos veces: ¿cambiaste la llave que int-rest le manda? (la 18) |
| `Tras matar int-rest, la API tiene … de 3` | Al volver, int-rest no releyó lo que no confirmó: revisa las opciones del TODO 1 en su puente |
| `El puerto 8080 está ocupado` | Quedaron arriba los servicios de otra corrida: `DONDE=mio docker compose -f compose.yaml down` |

---

## Lo que esto enseña fuera de la práctica

**«Leído» no es «terminado».** La confirmación automática dice lo primero. Lo que importa es lo segundo, y solo lo
sabe quien hizo el trabajo.

**Lo que se pierde no siempre es lo que se espera.** En nuestras corridas, matar al consumidor a la mitad no perdió
eventos ni con la confirmación automática: al volver, Kafka le dio lo que no se había confirmado todavía. Lo que sí se
perdió fue el evento cuyo desenlace no se pudo escribir. Por eso la verifica provoca las dos cosas.

**No perder obliga a tolerar repetidos.** Si un evento se puede leer dos veces, quien lo recibe tiene que reconocerlo.
La llave de idempotencia de la 18 es lo que hace que esta lección funcione.

**No perder tiene un costo.** Un evento que no se puede terminar detiene su partición. Por eso el puente deja un
desenlace aunque el canal no conteste («no se sabe»): quien reintenta la entrega es el router, no el puente.

## Ejercicio: el separador del libro

1. En papel, para cada caso di si el evento se pierde, se repite o sale bien, con confirmación automática y con la
   tuya: (a) el canal entrega y el desenlace se publica; (b) el canal entrega y el desenlace no se puede publicar;
   (c) matan al puente después de entregar y antes de publicar.
2. Abre `mio/orden-router/desenlaces.camel.yaml`. ¿Dónde confirma el router? ¿Qué pasaría si confirmara antes de
   anotar el desenlace en la base?
3. Explica por qué el puente no reintenta la entrega él mismo cuando el canal no contesta. (Pista: la partición.)

---

## Por qué se hizo así

Lo que decidimos en esta lección, con lo que descartamos y qué nos haría cambiar de idea:

- [El offset se confirma a mano, después de dejar el desenlace, y lo que falla se vuelve a leer](../decisiones/33-el-offset-se-confirma-despues-del-desenlace.md)

## Repaso

1. ¿Qué es el offset, y qué hace la confirmación automática?
2. ¿Para qué sirve cada una de las tres opciones del TODO 1?
3. ¿Por qué el `commit()` va al final y no al principio?
4. ¿Qué evita que una orden leída dos veces se cree dos veces en la API?
5. **La pregunta de cierre:** Kafka no aceptó el desenlace de la 2001. Explica qué pasa con ese evento con
   confirmación automática y con la tuya, y qué le cuesta a la partición tu versión.

**Siguiente:** en la lección 30 el hueco está del otro lado: el router guarda la orden y luego publica. ¿Y si Kafka
está caído justo en ese momento?
