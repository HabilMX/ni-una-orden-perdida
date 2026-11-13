# Lección 19 — El destino no contesta

> **Lo que vas a lograr:** que cuando la API no contesta, el router vuelva a intentar solo, cada vez con más pausa,
> hasta que la orden llegue. Y que mientras tanto el embarque de esa orden no se adelante: la API tiene que recibir
> primero la orden y después su embarque.

**Antes de empezar necesitas:** la lección 18 terminada y el ambiente de la 00 encendido.

## Lo nuevo de hoy, en una frase

En la 15 el router aprendió a guardar antes de contestar, y en la 18 a reconocer un mensaje repetido. Hoy pasa lo
contrario de la 18: el mensaje **no llega**, porque el destino no contesta. El router no puede saber si la API
recibió la orden; lo que sí puede hacer es volver a intentar. Y como ya sabe reconocer repetidos (la API también: lo
viste en la 02 con la llave), reintentar no duplica nada.

**La imagen:** marcarle a alguien que no contesta. No le marcas cada segundo durante una hora: le marcas, esperas un
minuto, vuelves a marcar, esperas dos, luego cuatro. Le das tiempo a que salga de la junta. Y no le cuentas lo
segundo antes que lo primero: si la llamada era para avisarle que su paquete ya salió, no le mandas antes un mensaje
de «¿ya te llegó?».

---

## 1. Prepara tu carpeta

```bash
cd lecciones/19-el-destino-no-contesta
cp -R inicio mio
```

Hoy todo el trabajo va en un archivo nuevo del router, `orden-router/reintentos.camel.yaml`. Ábrelo y lee el
comentario de arriba completo. Trae un dibujo:

```
intento 1 ✗ ─ 1 s ─ intento 2 ✗ ─ 2 s ─ intento 3 ✗ ─ 4 s ─ intento 4 ✓     (más o menos 15 % al azar)
```

Y tres reglas que ya están en el código:

| Regla | Qué significa | Dónde vive |
|---|---|---|
| **Solo el router reintenta** | Los canales (int-rest, int-bd, int-edi) contestan lo que les pasó y ya | Los canales no tienen reintentos |
| **Solo se repite lo que quedó «no se sabe»** | Sin respuesta, o un error 5xx. Un 4xx de esta API (su 422) es un «no» del destino y no se repite | `direct:intentar` lanza una `IOException` solo en ese caso |
| **Cada intento deja su renglón** | Antes de llamar al canal, `intentar` escribe en la tabla `intento`; después anota cómo le fue | `reparto.camel.yaml`, desde la 15 |

## 2. Predice

Contesta en un papel:

1. La API está apagada cuando llega la orden 4001, y vuelve a los pocos segundos. Mientras tanto, la bodega deja el
   embarque de la 4001. ¿En qué orden deberían llegarle a la API la orden y el embarque? ¿Y si llegaran al revés?
2. Si el router reintenta cada 100 milisegundos, ¿qué le pasa a una API que está arrancando?
3. El ERP y el socio sí estaban arriba. ¿Cuántas veces deberían recibir la 4001?

Mira cómo está hoy:

```bash
./verifica inicio
```

La tienda recibe su 202, porque el router guardó. Pero el canal rest lo intenta **una sola vez**: la API estaba
apagada, el intento queda en `no_se_sabe` y nadie lo vuelve a intentar. La API regresa unos segundos después y la
4001 nunca le llega.

## 3. Reintenta con espera creciente

Abre `mio/orden-router/reintentos.camel.yaml`. Arriba está un `routeConfiguration` con un `onException` que atrapa
la `IOException` que lanza `intentar`. Hoy solo anota en el log que se rindió. Tu trabajo es decirle **cuántas veces
y con qué pausa** reintentar, con una `redeliveryPolicy` dentro del `onException`:

| TODO | Qué escribes | Para qué |
|---|---|---|
| 1 | `maximumRedeliveries: 5` y `redeliveryDelay: 1000` | Hasta cinco reintentos; el primero, al segundo |
| 2 | `useExponentialBackOff: true` y `backOffMultiplier: 2` | Que cada pausa sea el doble de la anterior: 1, 2, 4, 8, 16 segundos |
| 3 | `useCollisionAvoidance: true` y `collisionAvoidanceFactor: 0.15` | Que cada pausa varíe un 15 % al azar |
| 4 | `retryAttemptedLogLevel: WARN` | Que cada reintento se vea en el log |

La `redeliveryPolicy` va a la misma altura que `handled` y `steps`, dentro del `onException`. Así se ve el
esqueleto:

```yaml
- onException:
    exception:
      - java.io.IOException
    redeliveryPolicy:
      maximumRedeliveries: 5
      …
    handled:
      constant:
        expression: "true"
    steps:
      - log: …
```

Fíjate en dos cosas:

- **El azar del TODO 3 parece un capricho y no lo es.** Imagina tres copias del router (la 12) que pierden la API al
  mismo tiempo. Sin azar, las tres reintentan al segundo exacto, luego a los dos, luego a los cuatro: la API
  regresa y recibe tres golpes juntos cada vez. Con un 15 % de variación, los golpes se separan.
- **El reintento es «en el lugar».** La cola `seda:repartir` tiene un solo consumidor, y la pausa lo detiene. Lo que
  viene detrás en la cola, como el embarque de la 4001, espera su turno. Eso es lo que conserva el orden.

## 4. Rompe uno a propósito

Con tus reintentos terminados, cambia `maximumRedeliveries` a `1` y corre `./verifica`.

Antes, predice: la verifica deja la API apagada unos seis segundos. Con un solo reintento al segundo, ¿llega la orden
a la API?

No llega. El primer intento falla, el reintento al segundo también, y el router se rinde mientras la API sigue
apagada. Sale en rojo la marca de los intentos de rest y la de la API. La operación se queda en `no_se_sabe`, y eso es
correcto: el router **no inventa** que la entregó. Pero muestra el costo de quedarse corto: cinco reintentos con
espera creciente cubren unos treinta segundos de caída; uno solo, un segundo. Regresa a `5`.

## 5. Comprueba

```bash
./verifica
```

`verifica` apaga la API, manda la 4001, deja su embarque en el buzón, espera unos segundos y vuelve a prender la
API. Está terminada cuando ves las seis marcas verdes:

```
✅ Arrancaron el router (con sus reintentos), los tres canales y el sondeo
✅ Con la API apagada, la tienda recibió su 202: lo suyo quedó guardado
✅ Estado: el canal rest reintentó en el lugar: (…) intentos; esperas (…); desenlaces (…)
✅ Estado: el ERP y el socio no se repitieron: aceptados al primer intento (bd=aceptada/1 edi=aceptada/1)
✅ API: recibió la orden y después su embarque, y la 4001 quedó EMBARCADO
✅ ERP: la 4001 está EMBARCADO
```

Lee la tercera marca con calma. Te dice cuántos intentos hubo, cuánto esperó entre uno y otro y cómo terminó cada
uno. Las esperas cambian en cada corrida por el azar, pero siempre crecen, y el último intento es el único
`aceptada`.

**¿No te corre?** `./verifica solucion` prueba la nuestra, y
`diff mio/orden-router/reintentos.camel.yaml solucion/orden-router/reintentos.camel.yaml` te dice en qué son
distintas.

| Lo que ves | Qué haces |
|---|---|
| `esperaba 3 o más intentos de rest` y hay 1 | Falta la `redeliveryPolicy`, o quedó fuera del `onException` |
| `esperaba 3 o más intentos de rest` y las esperas no crecen | Falta `useExponentialBackOff: true` |
| `esperaba la orden antes que el embarque` y la orden «no llegó» | Pocos reintentos o pausas muy cortas: el router se rindió antes de que volviera la API |
| `El ERP y el socio quedaron` con más de un intento | Pusiste reintentos también en otro lado: solo van en `reintentos.camel.yaml` |
| `El puerto 8080 está ocupado` | Quedaron arriba los servicios de otra corrida: `DONDE=mio docker compose -f compose.yaml down` |

Si la verifica se corta a la mitad, la API se vuelve a prender sola al salir. Si aun así la ves apagada:
`docker compose -f ../../infra/compose.yaml start api-pedidos`.

---

## Lo que esto enseña fuera de la práctica

**Reintentar solo es seguro si el destino reconoce los repetidos.** El router no sabe si el primer intento llegó.
Si llegó y reintenta, la API recibe la orden dos veces, y lo que impide el duplicado es su llave de idempotencia (la
02). Reintentos sin idempotencia son duplicados con otro nombre.

**La espera creciente con azar es la práctica habitual**, y tiene nombre: *exponential backoff with jitter*. La
recomiendan los proveedores de nube grandes para cualquier cliente de sus servicios. Espera fija, o le das tiempo de
más al que ya volvió o golpeas al que sigue arrancando.

**Uno solo reintenta.** Si el router reintenta cinco veces y el canal también cinco, un solo envío puede volverse
treinta y seis llamadas. Se elige un lugar, y el resto contesta lo que le pasó.

**El orden tiene un precio.** Reintentar en el lugar detiene la cola: mientras la API no contesta, nada de lo que
viene detrás avanza. Aquí vale la pena, porque un embarque antes que su orden es un error. Cuando un destino caído
detiene demasiado a los demás, hace falta otra herramienta: es la lección 22.

## Ejercicio: la bitácora de los intentos

Con tu versión terminada, corre `./verifica` y entra a la base:

```bash
docker compose -f ../../infra/compose.yaml exec estado psql -U router -d estado
```

1. Consulta los intentos del canal rest para la creación de la 4001, con su hora:
   `SELECT i.n, i.inicio, i.desenlace FROM intento i JOIN operacion p ON p.id = i.operacion WHERE p.orden = '4001'
   AND p.momento = 'creada' AND p.canal = 'rest' ORDER BY i.n;`
   Resta a mano la hora de cada intento menos la del anterior. ¿Se parecen a 1, 2, 4 y 8 segundos? ¿Por qué no son
   exactas?
2. Suma las cinco pausas de la política (1 + 2 + 4 + 8 + 16). Si la API tardara un minuto en volver, ¿qué pasaría con
   la orden? ¿En qué estado quedaría, y qué haría falta para que llegara? (Lo resuelve la lección 23.)
3. Explica con tus palabras por qué el embarque de la 4001 llegó **después** que la orden, aunque la bodega lo dejó
   mientras la API estaba apagada.

---

## Por qué se hizo así

Lo que decidimos en esta lección, con lo que descartamos y qué nos haría cambiar de idea:

- [Se reintenta en el lugar, solo en el router, con espera creciente](../decisiones/23-reintentar-en-el-lugar.md)

## Repaso

1. ¿Qué respuestas del destino se reintentan y cuáles no? ¿Por qué un 4xx no?
2. ¿Para qué sirve que cada pausa sea el doble de la anterior?
3. ¿Para qué sirve el 15 % de azar? Explícalo con tres copias del router.
4. ¿Por qué reintentar no duplicó la orden en la API?
5. **La pregunta de cierre:** reintentar «en el lugar» conserva el orden. ¿Por qué, y qué cuesta? Usa el embarque de
   la 4001 como ejemplo.

**Siguiente:** en la lección 20 la API sí contesta, pero no siempre dice «sí». A veces dice «no», y a veces tarda
tanto que se corta la llamada. ¿Escribió o no escribió?
