# Lección 02 — La orden llega a la API

> **Lo que vas a lograr:** que una orden de la tienda entre a tu ruta por internet y llegue a la API de pedidos, y
> entender qué contesta la API en cada caso. Es la primera vez que tu integración habla con otro sistema.

**Antes de empezar necesitas:** la lección 01 terminada y el ambiente de la 00 encendido:

```bash
docker compose -f infra/compose.yaml up -d --wait     # desde la carpeta raíz de la práctica
```

## Lo nuevo de hoy, en una frase

En la 01 la estación de entrada era una carpeta. Hoy es una **dirección HTTP**: la tienda manda la orden como cuando
tu navegador manda un formulario, y tu ruta la reenvía a la API de pedidos de la misma forma.

**HTTP, lo mínimo:** un programa le manda a otro una **petición** con un método (`POST` es «aquí te mando algo nuevo»),
una dirección (`/ordenes`), unos **encabezados** (datos sobre el mensaje, como etiquetas en un sobre) y un **cuerpo**
(la orden). El otro contesta con un **código** de tres cifras y su propio cuerpo. Si quieres más, está la ficha
[`conceptos/antes-de-empezar/`](../../conceptos/antes-de-empezar/).

---

## 1. Prepara tu carpeta

```bash
cd lecciones/02-la-orden-llega-a-la-api
cp -R inicio mio
cd mio
```

## 2. Lee la ruta

Abre `orden-a-api.camel.yaml`. Tiene cinco pasos:

```
POST /ordenes  →  sacar el id de la orden  →  traducir  →  POST a la API  →  anotar lo que contestó
```

| Paso en el YAML | Qué hace |
|---|---|
| `from: platform-http:/ordenes` | **La estación de entrada.** Abre la dirección `/ordenes` en el puerto 8080 de tu máquina, y solo acepta `POST` |
| `setHeader: Idempotency-Key` con `jsonpath: "$.orden"` | Saca el número de la orden (`"orden": "1001"`) y lo pone en un encabezado llamado `Idempotency-Key`. Es la **llave** con la que la API reconoce una orden que ya recibió |
| `to: jsonata:…/mapeo.jsonata` | **Traduce** la orden del formato de la tienda al formato de la API. El traductor ya viene hecho; en la 03 lo escribes tú |
| `removeHeaders: CamelHttp*` | Borra las etiquetas de la llamada que entró, para que no se mezclen con la que va a salir |
| `to: http://api-pedidos:8080/pedidos` | **La estación de salida.** Llama a la API por su nombre de servicio (`api-pedidos`), no por un número de IP |

Abre también `datos/ordenes/orden-1001.json` y `mio/mappings/api/pedido/v1/mapeo.jsonata`. Pon uno junto al otro:
¿qué nombre cambió (`renglones` → ?) y cuál se quedó igual?

## 3. Predice

La API de pedidos no acepta cualquier cosa: contesta con un código distinto según lo que le llegue. Antes de correr,
escribe en un papel qué código crees que da en cada caso:

| Caso | Tu predicción |
|---|---|
| Mandas la orden 1001 por primera vez | |
| Mandas **la misma** orden 1001 otra vez | |
| Mandas una orden sin renglones | |

## 4. Córrela y mándale una orden

En una terminal, arranca la ruta:

```bash
../../../herramientas/camel run orden-a-api.camel.yaml
```

La primera vez tarda unos 15 segundos, porque Camel baja lo que necesita para hablar HTTP. Espera la línea
`Started orden-a-api`.

En **otra** terminal, desde la misma carpeta `mio/`, haz de tienda y manda la orden:

```bash
curl -i -XPOST localhost:8080/ordenes \
  -H 'Content-Type: application/json' \
  -d @../../../datos/ordenes/orden-1001.json
```

- `curl` es un programa que hace peticiones HTTP desde la terminal.
- `-i` te muestra el código y los encabezados de la respuesta, no solo el cuerpo.
- `-XPOST` es el método.
- `-H` agrega un encabezado.
- `-d @archivo` manda ese archivo como cuerpo.

Mira las dos terminales. En la de la ruta aparecen `Recibí la orden 1001` y `La API contestó …`. Ahora pregúntale a
la API qué guardó; ella vive en el puerto 8081:

```bash
curl localhost:8081/pedidos/1001
```

## 5. Compara con tu predicción

Manda la misma orden una segunda vez y fíjate en el código. Luego prueba una orden sin renglones: copia la orden,
**cámbiale el número** (`"orden": "1002"`, para que sea una orden nueva), deja los renglones vacíos y mándala.

```bash
cp ../../../datos/ordenes/orden-1001.json sin-renglones.json   # en tu editor: "orden": "1002" y "renglones": []
curl -i -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' -d @sin-renglones.json
```

| Caso | Lo que contesta la API | Qué significa |
|---|---|---|
| Primera vez | **201** | *Created*: «lo creé» |
| La misma orden otra vez | **200**, con `ya_existia` | «Ya lo tenía; no lo dupliqué». La API reconoció la llave |
| Sin renglones | **422** | «Entendí lo que mandaste, pero no tiene sentido»: un pedido sin partidas no se puede registrar |
| Sin la llave `Idempotency-Key` | **400** | «Te falta algo obligatorio» |
| La misma llave con **otro** contenido | **409** | «Conflicto: con esta llave ya me mandaste algo distinto» |

Las dos últimas no las probaste. **¿Cómo harías para provocar un 400?** Pista: ¿qué línea de la ruta pone la llave?
¿Y un 409? Pista: ¿qué pasaría si mandas la orden 1001 con otra cantidad? Haz las pruebas y luego devuelve la ruta
a como estaba.

¿Ves por qué la orden sin renglones necesitaba otro número? Con el 1001 habrías tenido la misma llave con otro
contenido.

**Lo importante del 200:** si la red falla y la tienda reenvía la orden, la API no crea dos pedidos, porque la llave
le dice que es la misma. Por eso el id de la orden viaja desde la primera estación. En la lección 16 vas a construir
esa misma protección en tu propia integración.

## 6. Comprueba

Detén la ruta con `Ctrl+C` (si no, el puerto 8080 queda ocupado) y, desde la carpeta de la lección:

```bash
cd ..
./verifica
```

`verifica` reinicia la API para que empiece vacía, arranca tu ruta, manda la orden dos veces y revisa. Está terminada
cuando ves las cinco marcas verdes:

```
✅ La ruta arrancó
✅ Primer envío: la API creó el pedido (201)
✅ Reenvío: la API lo reconoció y no lo duplicó (200)
✅ La API tiene el pedido 1001 con sus 2 partidas
✅ La API recibió la llave 1001: el id de la orden viajó hasta el destino
```

La última la confirma **la API**, no tu ruta: `verifica` lee lo que anotó el destino. Que tu ruta diga que mandó algo
no prueba que llegó; lo prueba el que lo recibió. Esa idea va a regresar muchas veces en la práctica.

**¿No te corre?** `./verifica solucion` prueba la nuestra. Si la nuestra sale verde y la tuya no,
`diff mio/orden-a-api.camel.yaml solucion/orden-a-api.camel.yaml` te dice en qué son distintas.

| Lo que ves | Qué haces |
|---|---|
| `El puerto 8080 está ocupado` | Quedó una ruta corriendo en otra terminal: detenla con `Ctrl+C` |
| `La API no levantó: ¿corriste la 00?` | Enciende el ambiente: `docker compose -f infra/compose.yaml up -d --wait` |
| `curl: (7) Failed to connect` | La ruta todavía no arranca: espera la línea `Started` |

---

## Repaso

1. ¿Cuál es la estación de entrada de esta ruta, y cuál la de salida?
2. ¿Para qué sirve el encabezado `Idempotency-Key`, y de dónde sale su valor?
3. ¿Qué diferencia hay entre un 201 y un 200 en esta API?
4. ¿Por qué la ruta llama a `api-pedidos` y no a una dirección IP?
5. ¿Por qué la última comprobación la hace la API y no tu ruta?

**Siguiente:** en la lección 03 el traductor deja de venir hecho. Lo escribes tú, y unos casos de prueba te dicen si
quedó bien.
