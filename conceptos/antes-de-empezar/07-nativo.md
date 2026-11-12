# Qué es «nativo» y por qué arranca tan rápido

**Para qué te sirve aquí:** cuando una integración tiene que arrancar al momento porque la carga sube, compilarla a modo
nativo es una de las opciones. En la lección 33 lo mides tú mismo contra la JVM.

## Lo que ves (en la lección 33)

Vas a correr el mismo servicio de dos formas, una en la JVM y otra en nativo, y vas a comparar dos cosas en su bitácora:
cuánto tardó en decir `started` y cuánta memoria ocupa ya corriendo. No te damos aquí las cifras: las vas a medir.

## Una imagen de la vida diaria

La JVM es un **traductor simultáneo**: traduce mientras hablas, y se vuelve más rápido conforme conoce el tema. El
nativo es un **libro ya traducido**: se lee de inmediato, pero alguien tuvo que traducirlo completo antes, y esa
traducción ya no mejora con el uso.

## El concepto

Un programa **nativo** es un ejecutable hecho directo para el sistema operativo y el procesador donde va a correr, sin
JVM de por medio. En el mundo Java se hace con **GraalVM** (o con Mandrel, su distribución para Quarkus), que analiza
el programa completo **al compilar** y deja fuera todo lo que no se usa.

Lo que se gana, según la documentación de Quarkus:

- **Arranca mucho más rápido**, porque ya no hay que cargar clases ni preparar la JVM.
- **Ocupa menos memoria**, porque lleva solo lo que el programa usa.

Lo que cuesta, y se dice igual de claro:

- **Compilar tarda varios minutos y pide una máquina con bastante memoria.** No se hace en cada cambio pequeño.
- **En carga sostenida suele rendir menos por copia que la JVM**, porque la JVM optimiza mientras corre y el nativo no.
- **No todo cabe:** lo que un programa descubre al vuelo (reflexión, clases que se cargan por nombre) hay que declararlo
  al compilar. Por eso no todas las extensiones de Camel Quarkus funcionan en nativo; cada una lo dice en su página.

Entonces el nativo responde rápido **escalando**: muchas copias pequeñas que suben en un momento. La JVM conviene
cuando unas pocas copias atienden mucho tráfico por largo tiempo. Cuál conviene depende de cuál es tu problema.

## Lo que necesitas saber para la práctica

- Las lecciones 00 a 31 corren en la JVM, con `camel run`. El nativo aparece solo en la 33.
- «Nativo» aquí no tiene que ver con «aplicación nativa» de un celular: es un ejecutable sin JVM.

## Compruébalo

Pregunta: tu tienda recibe picos de ventas que llegan sin aviso (una mención en redes, una oferta de un competidor), y
fuera de esos picos casi no hay tráfico. ¿Qué modo consideras primero, y qué tendrías que medir antes de decidir?

Respuesta: el **nativo**, porque tiene que arrancar copias rápido para el pico. Antes de decidir mides cuánto tarda en
arrancar y cuánto rinde cada copia, en tu servicio y con tu carga: los números de otro no son los tuyos. Si el pico llegara a la misma hora cada noche,
podrías encender las copias antes, y el arranque pesaría menos.

## Si quieres ir más lejos

- Quarkus, construir un ejecutable nativo: https://quarkus.io/guides/building-native-image
- GraalVM, Native Image: https://www.graalvm.org/latest/reference-manual/native-image/
- Camel Quarkus, extensiones y su soporte en nativo: https://camel.apache.org/camel-quarkus/latest/reference/index.html
