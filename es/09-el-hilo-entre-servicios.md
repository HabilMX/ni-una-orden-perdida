# Lección 09 — El hilo entre servicios

> **Lo que vas a lograr:** que cada orden lleve un **id de correlación** que la acompañe de la tienda al router, del
> router a int-rest y de int-rest a la API, para que puedas encontrarla en cualquier punto del recorrido buscando
> un solo valor.

**Antes de empezar necesitas:** la lección 08 terminada y el ambiente de la 00 encendido:

```bash
docker compose -f infra/compose.yaml up -d --wait     # desde la carpeta raíz de la práctica
```

## Lo nuevo de hoy, en una frase

En la 08 la orden pasó por tres servicios, y cada uno escribió en su propio log. Si mañana una tienda reclama «mi
orden no llegó», ¿cómo juntas los renglones de los tres logs que hablan de **esa** orden? Hoy le pones un hilo: un
id que cada servicio recibe y le pasa al siguiente.

**La imagen:** el número de guía de un paquete. Pasa por la sucursal, el centro de distribución y el repartidor, y
en cada lugar lo anotan. Con ese número sabes dónde estuvo. Si una sucursal no lo anota, desde ahí el paquete se
vuelve invisible.

## El sobre, completo

En la 08 viste los encabezados `ce-*` sin usarlos. Hoy los lees:

| Encabezado | Qué dice | Para la orden 1003 |
|---|---|---|
| `ce-specversion` | Qué **versión** de CloudEvents usa el sobre | `1.0` |
| `ce-id` | Qué **evento** es este, uno solo | `orden-1003-creada` |
| `ce-type` | De qué **tipo** es el evento | `mx.tienda.orden.creada` |
| `ce-source` | **Dónde** pasó | `/tiendas/T-022` |
| `ce-subject` | **De qué** cosa habla: la orden | `1003` |
| `ce-correlationid` | El **hilo**: une todo lo que pasa por culpa de una misma petición | lo decide la tienda, o el router |

`ce-id` y `ce-correlationid` parecen lo mismo y no lo son. El `ce-id` nombra **un** evento. El hilo sigue a una
petición de la tienda por todos los servicios que toca. Cuando una orden genere varios eventos (creada, embarcada,
entregada), cada uno tendrá su `ce-id` propio, y todos podrán compartir el mismo hilo.

`ce-correlationid` no es uno de los campos base de CloudEvents: es una **extensión**, un campo que se agrega al
sobre. La norma permite agregar extensiones.

---

## 1. Prepara tu carpeta

```bash
cd lecciones/09-el-hilo-entre-servicios
cp -R inicio mio
```

## 2. Lee de dónde sale el hilo

Abre `mio/orden-router/orden-router.camel.yaml`. Arriba hay un paso `choice`:

| Si la tienda… | El router… |
|---|---|
| manda el encabezado `X-Correlation-Id` | usa ese mismo id como hilo |
| no lo manda | inventa uno nuevo, un UUID (un número al azar tan largo que, en la práctica, no se repite) |

Y al final, el router se lo **devuelve** a la tienda en la respuesta. Así la tienda tiene con qué preguntar por su
orden.

Fíjate en el comentario: el id se guarda en una variable **antes** de limpiar encabezados. Ése es el corazón de la
lección.

## 3. Predice

La ruta de `inicio/` tiene tres TODO, y por ahora ningún servicio pasa el hilo al siguiente. Contesta en un papel:

1. Si la tienda manda la orden 1003 con el id `mi-hilo-1`, ¿en qué punto del recorrido se pierde: en el router, en
   int-rest o en la API?
2. ¿La tienda recibe de vuelta su id, aunque se pierda en el camino?

Compruébalo:

```bash
./verifica inicio
```

Salen tres marcas rojas, y una dice `La API no recibió el hilo de la 1003 (anotó: correlacion=-)`. El guion es como
la API escribe «no me llegó ninguno». El router sí anotó el hilo y la tienda sí lo recibió de vuelta: **el hilo se
pierde en el primer servicio que no lo reenvía**, justo después del router.

## 4. Escribe los tres pasos

Son tres TODO y los tres son la misma idea: **recibir el hilo y pasarlo**.

**1. En el router: ponlo en el sobre.** En `mio/orden-router/orden-router.camel.yaml`, donde dice TODO, agrega un
`setHeader` con el nombre `ce-correlationid` y el valor de la variable `correlacion`. Usa como modelo el `setHeader`
de `ce-subject`, que está unos renglones arriba.

**2. En int-rest: guárdalo antes de que se borre.** En `mio/int-rest/int-rest.camel.yaml`, el primer TODO. Agrega un
`setVariable` llamado `correlacion` con el valor `${header.ce-correlationid}`. Tiene que ir **ahí**, arriba: unos
pasos más abajo está `removeHeaders`, que borra todos los encabezados que llegaron.

**3. En int-rest: pásaselo a la API.** El segundo TODO. Agrega un `setHeader` llamado `X-Correlation-Id` con el valor
de la variable `correlacion`. Va después del `removeHeaders`, junto a los demás encabezados de la llamada a la API.

## 5. Síguelo con tus ojos

Desde la carpeta de la lección, levanta los servicios y mira sus logs:

```bash
DONDE=mio docker compose -f compose.yaml up -d
DONDE=mio docker compose -f compose.yaml logs -f
```

En **otra** terminal, desde la raíz de la práctica, haz de tienda con tu propio hilo:

```bash
curl -i -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: mi-hilo-1' -d @datos/ordenes/orden-1003.json
```

Busca `mi-hilo-1` en tres lugares:

| Dónde | Qué buscar |
|---|---|
| La respuesta de `curl` | El encabezado `X-Correlation-Id: mi-hilo-1` |
| Los logs del router y de int-rest | `correlacion=mi-hilo-1` en sus renglones |
| El log de la API | `docker compose -f infra/compose.yaml logs api-pedidos \| grep mi-hilo-1` |

Si en los tres aparece el mismo valor, el hilo está completo. Ahora manda la orden **sin** el encabezado
`X-Correlation-Id`. ¿Qué id te devolvió la tienda? Búscalo en los logs: debe ser el mismo en todos.

Al terminar, apaga los servicios:

```bash
DONDE=mio docker compose -f compose.yaml down
```

## 6. El error más común: leerlo tarde

Haz la prueba: en int-rest, mueve tu `setVariable` de la correlación para que quede **debajo** del `removeHeaders`.
Apaga, levanta otra vez y manda la orden con `mi-hilo-1`.

La tienda recibe su id de vuelta, el router lo anota, pero int-rest escribe `correlacion=` (vacío) y la API,
`correlacion=-`. Cuando int-rest fue a leer el encabezado, `removeHeaders` ya lo había borrado. No hay error ni
alarma: **el hilo se corta en silencio**.

Por eso la regla es: **lo que necesites de la llamada que entró, guárdalo en una variable antes de limpiar**. Las
variables no se borran con los encabezados. Regresa tu paso a su lugar.

## 7. Comprueba

```bash
./verifica
```

`verifica` reinicia la API, levanta los dos servicios y manda dos órdenes: la 1003 con un hilo propio y la 1001 sin
ninguno. Está terminada cuando ves las ocho marcas verdes:

```
✅ Arrancaron el router e int-rest
✅ La tienda mandó la 1003 y la respuesta final fue 201
✅ El router anotó el hilo tienda-…
✅ int-rest recibió el hilo en el sobre
✅ La API registró la 1003 con el mismo hilo: se puede seguir de punta a punta
✅ La tienda recibió de vuelta su mismo id
✅ Los encabezados del sobre (ce-*) no salieron hacia la tienda
✅ Sin id de la tienda, el router inventó … y llegó igual a la API
```

Donde ves `…` aparece el id de esa corrida: `verifica` usa uno nuevo cada vez.

La séptima revisa algo que no escribiste tú: el sobre `ce-*` es para los servicios de adentro, y el router lo
borra antes de contestarle a la tienda. A la tienda solo le regresa `X-Correlation-Id`.

**¿No te corre?** `./verifica solucion` prueba la nuestra, y estos dos comandos te dicen en qué son distintas:

```bash
diff mio/orden-router/orden-router.camel.yaml solucion/orden-router/orden-router.camel.yaml
diff mio/int-rest/int-rest.camel.yaml solucion/int-rest/int-rest.camel.yaml
```

| Lo que ves | Qué haces |
|---|---|
| `int-rest no anotó el hilo` | Falta el paso 1 en el router, o el paso 2 quedó debajo del `removeHeaders` (paso 6) |
| `La API no recibió el hilo` con todo lo demás en verde | Falta el paso 3: int-rest lo tiene, pero no se lo pasa a la API |
| `El puerto 8080 está ocupado` | Quedaron los servicios arriba: `DONDE=mio docker compose -f compose.yaml down` |

---

## Ejercicio: encuentra una orden

Levanta tu versión, manda tres órdenes (la 1001, la 1003 y otra vez la 1001), cada una sin `X-Correlation-Id`, y
anota el id que te devolvió cada una. Después, **solo con el id de la segunda**, contesta: ¿qué orden era, qué le
contestó la API y cuánto tardó desde que llegó al router hasta que la API respondió? Pista: cada renglón del log
empieza con la hora.

---

## Por qué se hizo así

Lo que decidimos en esta lección, con lo que descartamos y qué nos haría cambiar de idea:

- [CloudEvents en modo binario como sobre](../decisiones/11-cloudevents-como-sobre.md)
- [El hilo de correlación viaja con la orden, pero no sale de casa](../decisiones/12-el-hilo-de-correlacion.md)

## Repaso

1. ¿Qué diferencia hay entre `ce-id` y `ce-correlationid`?
2. Si la tienda no manda id, ¿quién lo pone y de qué forma?
3. ¿Por qué int-rest guarda el hilo en una variable antes del `removeHeaders`?
4. ¿Qué escribió la API cuando el hilo no le llegó, y por qué es peor que un error?
5. ¿Para qué le sirve a la tienda recibir su id de vuelta?

**Siguiente:** en la lección 10 el router deja de mandarle la orden solo a int-rest: la manda a la vez a los tres
destinos, y el mismo hilo tiene que llegar a la API, al ERP y al socio.
