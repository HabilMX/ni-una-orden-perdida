# Lección 09 — Todos los canales

> **Lo que vas a lograr:** que el router mande la misma orden, **a la vez**, a los tres destinos (la API, el ERP y el
> socio), y que le conteste a la tienda lo que dijo cada uno. Y ver con tus propios ojos qué pasa cuando uno de los
> tres está caído.

**Antes de empezar necesitas:** las lecciones 04b, 06 y 08 terminadas y el ambiente de la 00 encendido.

⚠️ **Esta lección agrega una columna al ERP** (`correlacion`, para guardar el hilo de la 08). Si tu ambiente ya estaba
arriba de antes, recrea el ERP una vez, desde la raíz de la práctica:

```bash
docker compose -f infra/compose.yaml up -d --wait --force-recreate -V erp
```

`--force-recreate` lo vuelve a crear y `-V` le borra los datos, así arranca con las tablas nuevas. Si te olvidas,
`verifica` te avisa y te da este mismo comando.

## Lo nuevo de hoy, en una frase

En la 07 el router le pasaba la orden a **un** servicio. Hoy hay cuatro servicios y el router reparte **tres
copias** de la orden, una por destino, todas al mismo tiempo:

```
                                        ┌→ int-rest → API de pedidos
  tienda → POST /ordenes → [router] ────┼→ int-bd   → ERP
                                        └→ int-edi  → SFTP del socio (850)
```

int-bd es tu lección 04b detrás del router, e int-edi es tu lección 06. No los escribes otra vez: ya vienen hechos.

**La imagen:** un aviso que la oficina manda a tres sucursales. No espera a que la primera conteste para mandarlo a la
segunda: lo manda a las tres a la vez y luego junta las tres respuestas.

Este patrón tiene nombre en los EIP: **Multicast**. Una misma orden va a varios destinos fijos.

---

## 1. Prepara tu carpeta

```bash
cd lecciones/09-todos-los-canales
cp -R inicio mio
```

Ahora `mio/` tiene cuatro carpetas, una por servicio: `orden-router`, `int-rest`, `int-bd` e `int-edi`.

## 2. Lee el router

Abre `mio/orden-router/orden-router.camel.yaml`. Arriba ya conoces todo: el hilo de la 08 y el sobre `ce-*`. Abajo
hay algo nuevo: **tres rutas de canal**, `canal-api`, `canal-erp` y `canal-socio`. Cada una llama a su servicio y
**siempre** deja una sola línea con lo que pasó:

| Si el servicio… | La línea es… |
|---|---|
| contesta, aunque sea con error | `{"canal":"erp","codigo":200}`, con el código HTTP que dio |
| ni siquiera contesta (está caído) | `{"canal":"erp","error":"UnknownHostException"}`, con el nombre del error |

Para eso usan `doTry` y `doCatch`: **intenta** la llamada y, si truena, **atrapa** el error y lo convierte en línea.

¿Por qué tanto cuidado? Porque al probar esta lección vimos que, sin las rutas de canal, cuando un servicio estaba
caído el router le devolvía a la tienda **la orden completa**, con el nombre y el correo del cliente. Una respuesta de
error nunca debe llevar datos personales.

Hoy la ruta principal solo llama a uno de los tres:

```yaml
- to:
    uri: direct:canal-api
```

`direct:` es una llamada a otra ruta **del mismo archivo**, sin red de por medio. El TODO de arriba dice qué falta.

## 3. Predice

Contesta en un papel:

1. Con el router como está, si mandas la orden 1003, ¿qué destinos la reciben?
2. Cuando mandes a los tres: si el ERP está caído, ¿la API y el socio reciben la orden o no?
3. En ese caso, ¿qué código HTTP crees que recibe la tienda?

Comprueba la primera:

```bash
./verifica
```

Salen cuatro marcas rojas. La API tiene la orden; el ERP y el socio no tienen nada.

## 4. Escribe el multicast

Cambia el `to: direct:canal-api` por un `multicast` que llame a los tres canales:

```yaml
- multicast:
    parallelProcessing: true
    aggregationStrategy: "#juntarRespuestas"
    steps:
      - to:
          uri: direct:canal-api
      # … los otros dos canales
```

| Parámetro | Qué hace |
|---|---|
| `parallelProcessing: true` | Los tres a la vez. Sin esto, uno tras otro: el socio esperaría a que el ERP termine |
| `aggregationStrategy: "#juntarRespuestas"` | Cómo se juntan las tres respuestas. `juntarRespuestas` es un bean que ya viene declarado arriba, y las pone en una lista |

Después del multicast, el cuerpo es esa lista de tres líneas. Contéstale a la tienda con un `setBody`:

```
{"orden":"${variable.orden}","canales":[${join(",")}]}
```

`${join(",")}` une las tres líneas de la lista separándolas con comas. Usa la notación canónica, como en los demás
`setBody` del archivo (`expression:` → `simple:` → `expression:`).

## 5. Levántalo y mándale una orden

Antes de levantar a mano, **anota la llave del socio en la carpeta de int-edi**: es el servicio que se conecta a su
SFTP, como en la 06.

```bash
(cd mio/int-edi && ../../../../herramientas/confiar-en-socio)
DONDE=mio docker compose -f compose.yaml up -d
DONDE=mio docker compose -f compose.yaml logs -f
```

Espera los cuatro `Started`. En otra terminal, desde la raíz de la práctica:

```bash
curl -s -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' -d @datos/ordenes/orden-1001.json
```

La tienda recibe lo que dijo cada canal:

```json
{"orden":"1001","canales":[{"canal":"api","codigo":201},{"canal":"erp","codigo":200},{"canal":"socio","codigo":200}]}
```

El orden de las tres líneas puede cambiar de una corrida a otra: los canales trabajan a la vez y cada uno termina
cuando termina. Fíjate también en los logs: los tres renglones de «contestó» del router salen casi al mismo tiempo.

Si el canal del socio dice `500`, revisa el log de int-edi. Si ves `reject HostKey`, te faltó anotar la llave.

## 6. Apaga un canal

Ahora la pregunta 2. Con todo arriba, apaga **solo** int-bd y manda otra orden:

```bash
DONDE=mio docker compose -f compose.yaml stop int-bd
curl -s -w ' %{http_code}\n' -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' \
  -d @datos/ordenes/orden-1003.json
```

```
{"orden":"1003","canales":[{"canal":"api","codigo":…},{"canal":"erp","error":"UnknownHostException"},{"canal":"socio","codigo":200}]} 200
```

- La API y el socio **sí** recibieron la orden. Un canal caído no detiene a los otros dos.
- La línea del ERP dice `UnknownHostException`: el router buscó a `int-bd` por su nombre y nadie contestó.
- La tienda recibió un **200**, aunque un destino no tiene la orden.

Si repites este paso con la misma orden, el socio contesta `500`: ya tiene ese 850 y no lo pisa, como en la 06.

Revisa tu predicción 3. Y ahora la pregunta que deja la lección: **¿qué hacemos con el ERP, que no tiene la orden?**
¿Se la volvemos a mandar? ¿Cuándo? ¿Y si la API ya la tiene: la volvemos a mandar también? Escribe tu respuesta: la
vas a comparar con lo que construyas en las lecciones 17 y 18.

Apaga todo:

```bash
DONDE=mio docker compose -f compose.yaml down
```

## 7. El hilo en los tres destinos

El hilo de la 08 viaja en el sobre a los tres canales. Pero cada destino lo guarda distinto:

| Destino | Dónde queda el hilo |
|---|---|
| API | En su log: `correlacion=<id>` |
| ERP | En la columna nueva `pedido_cabecera.correlacion` |
| Socio | **En ninguna parte del archivo.** int-edi lo anota en **su propio** log, y el 850 sale igual que en la 06 |

¿Por qué no se lo mandamos al socio? Porque al socio solo se le envían los campos que pacta con él el formato X12. El
hilo es nuestro, de nuestra integración; meterlo en un campo que el socio no pidió no se hace. Con el socio, el hilo
ya existe: es el **número de control** del sobre del 850 (el `ISA13` y el `GS06` de la 05). Cuando el socio conteste
con su acuse de recibo, el 997, va a citar ese número. Eso lo usas en la lección 17c.

**No todos los destinos pueden guardar tu id.** Por eso, más adelante, una base de estado va a unir el hilo con el
número de control.

## 8. Comprueba

```bash
./verifica
```

`verifica` reinicia la API, vacía el ERP y el buzón del socio, anota la llave del socio en `mio/int-edi/`, levanta los
cuatro servicios y manda la orden 1003 con un hilo propio. Está terminada cuando ves las seis marcas verdes:

```
✅ Arrancaron el router y los tres canales
✅ La tienda mandó la 1003 y el router contestó lo que dijo cada canal: api=201 erp=200 socio=200
✅ API: tiene el pedido 1003, con el hilo tienda-…
✅ ERP: tiene el pedido 1003 REGISTRADO, con el hilo tienda-…
✅ Socio: tiene el 850 de la 1003 en su SFTP
✅ int-edi anotó el hilo (al socio no se le manda: con él, el hilo es el número de control del X12)
```

Tres de las seis las confirma el destino mismo: el registro de la API, la tabla del ERP y el buzón del socio.

**¿No te corre?** `./verifica solucion` prueba la nuestra, y
`diff mio/orden-router/orden-router.camel.yaml solucion/orden-router/orden-router.camel.yaml` te dice en qué son
distintas.

| Lo que ves | Qué haces |
|---|---|
| `Tu ERP no tiene la columna correlacion` | Recrea el ERP con el comando del principio |
| Rojo solo en ERP, o solo en el socio | Ese canal no está en tu `multicast` |
| `El router no contestó que los tres canales la recibieron` y solo aparece una línea | Falta el `setBody` con `${join(",")}`, o el multicast sigue siendo un solo `to` |
| `El puerto 8080 está ocupado` | Quedaron los servicios arriba: `DONDE=mio docker compose -f compose.yaml down` |

---

## Repaso

1. ¿Qué hace un multicast, y qué cambia con `parallelProcessing: true`?
2. ¿Para qué sirve el bean `juntarRespuestas`, y qué hace `${join(",")}`?
3. ¿Por qué cada canal deja una línea aunque su servicio esté caído? ¿Qué se habría escapado si no?
4. Con el ERP caído, ¿qué destinos recibieron la orden y qué código recibió la tienda? ¿Por qué ese código es un
   problema?
5. ¿Por qué al socio no se le manda el hilo, y qué número cumple ese papel con él?

**Siguiente:** en la lección 10 no todas las órdenes van a todos los destinos: el router decide a cuáles, según lo que
traiga la orden.
