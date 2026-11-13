# Lección 16 — Llega la entrega

> **Lo que vas a lograr:** recibir el aviso que manda la paquetería cuando entrega un paquete, de modo que la orden
> quede ENTREGADO en la API y en el ERP, y que el socio reciba su 214. Y distinguir un aviso así de una petición.

**Antes de empezar necesitas:** la lección 15 terminada y el ambiente de la 00 encendido.

## Lo nuevo de hoy, en una frase

Ya tienes dos momentos de la orden: se **crea** (la tienda llama) y se **embarca** (la bodega deja un archivo). Hoy
llega el tercero: se **entrega**. Y llega de una tercera forma: la paquetería **te llama sola** cuando entregó, a
una dirección tuya. Eso es un **webhook**, y lo que trae es un **evento**: no te pide nada, te cuenta que algo ya
pasó.

**La imagen:** el mensaje de la paquetería en tu celular, «Tu paquete fue entregado». No te pide que hagas nada ni
espera que le contestes qué vas a hacer. Solo quiere saber que el mensaje te llegó. Lo que hagas después (avisarle a
quien lo esperaba, revisar que sea tu paquete) es asunto tuyo.

---

## 1. Prepara tu carpeta

```bash
cd lecciones/16-llega-la-entrega
cp -R inicio mio
```

Abre el aviso de ejemplo, `datos/webhooks/entrega-4001.json`. Trae el evento (`entregado`), la paquetería, **la
guía**, la orden, cuándo se entregó y quién recibió.

Todo lo de salida ya está hecho. Cada canal tiene su `entrega.camel.yaml`:

| Canal | Qué hace con la entrega |
|---|---|
| int-rest | Le cuenta a la API que la orden se entregó, como un evento con su llave |
| int-bd | Registra la entrega en el ERP y pasa el pedido a ENTREGADO |
| int-edi | Arma un **214**, el estatus de envío de X12, con la guía en el segmento `B10`, y lo deja en el SFTP |

El reparto de la 15 ya sabe mandar el momento `entregada`. **Falta la puerta.**

## 2. Predice

Contesta en un papel:

1. La paquetería avisa que entregó la 4001. ¿Qué tiene que quedar en la base de estado, y qué en cada destino?
2. ¿Qué le contesta el router a la paquetería: lo que dijeron la API, el ERP y el socio, o solo «recibido»? ¿Por qué?
3. Llega un aviso de la orden 9999, que la tienda nunca creó. ¿Qué debería contestar el router?

Mira cómo está hoy:

```bash
./verifica inicio
```

El webhook contesta **501**, que en HTTP quiere decir «eso no lo sé hacer», y no guarda nada. La 4001 se queda en
EMBARCADO para siempre, aunque el cliente ya tiene su paquete.

## 3. Abre la puerta

Abre `mio/orden-router/paqueteria.camel.yaml`. La ruta `webhook-paqueteria` escucha en
`POST /webhooks/paqueteria`, y hoy solo contesta 501. Tu modelo es el `embarque-router` de
`orden-router.camel.yaml`: hace casi lo mismo para el embarque. Léelo antes de empezar.

| TODO | Qué escribes | Para qué |
|---|---|---|
| 1 | `convertBodyTo` a `String` | Que el aviso se guarde tal cual llegó |
| 2 | Dos variables: `orden` (de `$.orden`, con `jsonpath`) y `momento` (`entregada`). Y llama a `direct:hilo` | Saber de qué orden es y qué momento es, con su hilo de la 09 |
| 3 | Llama a `direct:cargar-orden`. Con un `choice`: si `${variable.tienda} == null`, a `direct:no-existe`; si no, a `direct:canales`, `direct:guardar-momento` y `direct:contestar-y-repartir` | Una orden que no conocemos se rechaza; una que sí, se guarda, se contesta y se reparte |

Y borra los dos pasos que hoy contestan 501.

Fíjate en que **no escribes nada nuevo de fondo**: `cargar-orden`, `canales`, `guardar-momento` y
`contestar-y-repartir` ya existen desde la 15. La entrega entra por una puerta distinta, pero adentro sigue el mismo
camino que la orden y el embarque: **guardar, contestar, repartir**.

## 4. Rompe uno a propósito

Con tu webhook terminado, quita el `choice` y deja solo lo de la rama `otherwise`: como si toda orden existiera.
Levanta los servicios y manda un aviso de una orden que no existe:

```bash
DONDE=mio docker compose -f compose.yaml up -d
curl -i -XPOST localhost:8080/webhooks/paqueteria -H 'Content-Type: application/json' \
  -d '{"evento":"entregado","paqueteria":"PAQX","guia":"PAQX770009999","orden":"9999"}'
```

Antes, predice: ¿qué contesta el router?

Ya no contesta 404, contesta un error del servidor. `guardar-momento` intenta crear las operaciones de una orden que
no está en la tabla `orden`, la base lo impide y la transacción se deshace. Ahora piensa en la paquetería: ante un
error del servidor, **un emisor de webhooks vuelve a mandar el aviso**, y lo seguirá mandando. El 404 dice la
verdad, «esa orden no la conozco», y el emisor deja de insistir. Regresa el `choice` y apaga:

```bash
DONDE=mio docker compose -f compose.yaml down
```

## 5. Comprueba

```bash
./verifica
```

`verifica` crea la 4001, la embarca, hace de paquetería con `entrega-4001.json` y al final manda un aviso de la
9999. Está terminada cuando ves las nueve marcas verdes:

```
✅ Arrancaron el router (con el webhook de la paquetería), los tres canales y el sondeo
✅ Antes de la entrega: la 4001 está EMBARCADO en la API y en el ERP
✅ La paquetería avisó la entrega de la 4001 y el router contestó 202 «recibida»
✅ Estado: tres operaciones de la 4001 con momento=entregada, aceptadas, y la orden va en «entregada»
✅ API: la 4001 está ENTREGADO, con la guía con la que salió
✅ ERP: la 4001 está ENTREGADO y su fila de entrega tiene la guía
✅ Socio: el 214 de la 4001 está en su SFTP y el lector X12 no le encontró fallas
✅ Socio: el 214 lleva la guía en el B10 y el número de control de la base (…)
✅ Un aviso de la orden 9999, que no existe: el router contestó 404 y no guardó nada
```

Abre el 214 que llegó al socio, `mio/salida/214-4001.edi`, y busca la guía en el segmento `B10`.

**¿No te corre?** `./verifica solucion` prueba la nuestra, y
`diff mio/orden-router/paqueteria.camel.yaml solucion/orden-router/paqueteria.camel.yaml` te dice en qué son
distintas.

| Lo que ves | Qué haces |
|---|---|
| `Esperaba 202 del webhook y el router contestó` … 501 | Quedaron los dos pasos de `inicio/` que contestan 501: bórralos |
| `Un aviso de una orden que no existe contestó «500»` | Falta el `choice`, o la condición no es `${variable.tienda} == null` |
| `las operaciones de la entrega quedaron «sin operaciones»` | La variable `momento` no se llama así o no vale `entregada`, o falta `direct:guardar-momento` |
| La 4001 se guardó, pero ningún destino la tiene | Falta `direct:contestar-y-repartir` al final |
| `El puerto 8080 está ocupado` | Quedaron arriba los servicios del paso 4: `DONDE=mio docker compose -f compose.yaml down` |

---

## Lo que esto enseña fuera de la práctica

**Un evento y una petición se contestan distinto.** A una petición («crea esta orden») se le contesta si se pudo.
A un evento («ya entregué») solo se le contesta que llegó: lo que pasó, ya pasó, y no depende de ti. Por eso el
router contesta 202 y reparte después.

**Quien manda un webhook espera poco y reintenta.** Los emisores cuentan como entregado solo lo que recibió un 2xx a
tiempo, y vuelven a mandar lo demás. Contestar en cuanto guardaste cuida las dos cosas: no hace esperar y no provoca
reintentos de más.

**El código de respuesta es una instrucción para el que manda.** 202 es «ya lo tengo», 404 es «no insistas, eso no
existe» y un error del servidor es «inténtalo luego». Elegir mal el código cambia lo que hace el otro sistema.

## Ejercicio: tres puertas, un camino

Con tu versión terminada:

1. Haz una tabla con los tres momentos: creada, embarcada y entregada. Para cada uno: quién lo manda, por dónde entra
   (HTTP de la tienda, archivo de la bodega, webhook) y qué ruta del router lo recibe. ¿Qué tienen en común las tres
   rutas después de la entrada?
2. En la base de estado, consulta las operaciones de la 4001 de los tres momentos:
   `SELECT momento, canal, estado FROM operacion WHERE orden = '4001' ORDER BY id`. ¿Cuántas filas son, y por qué?
3. ¿Qué pasaría si la paquetería avisara la entrega de la 4002 **antes** de que llegue su embarque? Escribe tu
   respuesta: es la lección 17.

---

## Por qué se hizo así

Lo que decidimos en esta lección, con lo que descartamos y qué nos haría cambiar de idea:

- [El webhook de la paquetería contesta 202 en cuanto guardó](../decisiones/20-el-webhook-contesta-202.md)

## Repaso

1. ¿Qué es un webhook, y en qué se distingue de la forma en que llega la orden y de la forma en que llega el embarque?
2. ¿Qué es un 214, y qué lleva en su segmento `B10`?
3. ¿Por qué el router le contesta 202 a la paquetería y no lo que dijeron la API, el ERP y el socio?
4. ¿Por qué un aviso de una orden que no existe se contesta con 404 y no con un error del servidor?
5. **La pregunta de cierre:** «entregué el paquete» es un evento; «crea esta orden» es una petición. Con un ejemplo
   de la práctica para cada uno, explica en qué se distinguen y cómo cambia lo que contesta el router.

**Siguiente:** en la lección 17 el mundo se desordena: la entrega de la 4002 llega antes que su guía. El router no
puede repartirla todavía, pero tampoco la puede tirar. ¿Qué haces con ella?
