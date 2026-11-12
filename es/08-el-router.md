# Lección 08 — El router

> **Lo que vas a lograr:** partir tu integración en dos servicios que corren por separado: un **router** que recibe
> la orden y un servicio **int-rest** que sabe hablar con la API. Escribes la llamada que los une, por el nombre
> del servicio y nunca por una dirección.

**Antes de empezar necesitas:** las lecciones 02 a 07 terminadas y el ambiente de la 00 encendido:

```bash
docker compose -f infra/compose.yaml up -d --wait     # desde la carpeta raíz de la práctica
```

## Lo nuevo de hoy, en una frase

Hasta ahora cada lección era **una** ruta que hacía todo: recibir, traducir y entregar. Con tres destinos (la API, el
ERP y el socio), esa ruta crecería sin parar. Hoy la partes: un servicio **decide a dónde va** la orden y otro **sabe
entregarla**. Cada uno corre en su propio contenedor.

```
tienda ──POST /ordenes──▶ [orden-router] ──POST http://int-rest:8080/integrar──▶ [int-rest] ──▶ API de pedidos
```

**La imagen:** la recepción de un hospital. La recepcionista no opera a nadie: pregunta qué te pasa y te manda con
el especialista. Si mañana abre una especialidad nueva, la recepción sigue igual; solo aprende una puerta más.

---

## 1. Prepara tu carpeta

```bash
cd lecciones/08-el-router
cp -R inicio mio
```

Mira lo que copiaste: `mio/` tiene **dos carpetas**, una por servicio.

| Carpeta | Servicio | Qué trae |
|---|---|---|
| `mio/orden-router/` | El router | Su ruta: recibe la orden de la tienda y se la pasa a int-rest |
| `mio/int-rest/` | int-rest | Su ruta y el mapeo v2 de la 03: traduce la orden y la entrega a la API |

## 2. Lee el compose de la lección

Esta lección no usa `herramientas/camel`. Los dos servicios se levantan juntos con su propio `compose.yaml`, como el
ambiente de la 00. Ábrelo:

```yaml
x-camel: &camel
  image: apache/camel-jbang:4.22.1
  command: ["run", "."]          # corre todos los *.camel.yaml de su carpeta

services:
  orden-router:
    <<: *camel
    volumes:
      - ./${DONDE:-mio}/orden-router:/work
    ports: ["8080:8080"]

  int-rest:
    <<: *camel
    volumes:
      - ./${DONDE:-mio}/int-rest:/work
    # sin ports: solo lo alcanza el router, por dentro de la red
```

Dos cosas que vale la pena ver:

- **Los dos servicios son la misma imagen de Camel.** Lo único distinto es la carpeta que cada uno monta. `&camel`
  define la parte común una vez y `<<: *camel` la reutiliza. Para crear un servicio nuevo no hay que programar otro
  programa: basta otra carpeta con sus rutas.
- **Solo el router tiene `ports`.** Es el único que se ve desde tu máquina. A int-rest solo lo alcanza el router, por
  dentro de la red de Docker.

`DONDE` es una variable que dice qué carpetas montar: `mio` (la tuya, por omisión) o `solucion` (la nuestra).

## 3. Lee las dos rutas

**`mio/int-rest/int-rest.camel.yaml`** ya viene completa. Recibe en `/integrar`, traduce con el mapeo v2 y llama a
la API, igual que la ruta de la 03.

**`mio/orden-router/orden-router.camel.yaml`** recibe en `/ordenes` y prepara la orden para pasarla. Antes de la
llamada le pone unos encabezados que empiezan con `ce-`:

| Encabezado | Valor para la orden 1003 |
|---|---|
| `ce-type` | `mx.tienda.orden.creada` |
| `ce-source` | `/tiendas/T-022` |
| `ce-subject` | `1003` |
| `ce-id` | `orden-1003-creada` |

Son el **sobre** de la orden, en un formato estándar que se llama **CloudEvents**: dicen qué pasó, dónde y a quién.
Hoy solo los vas a **mirar** viajar; en la 09 sirven para seguir una orden de servicio en servicio.

Al final de la ruta hay un **TODO**: falta la llamada a int-rest.

## 4. Predice

Antes de escribir la llamada, contesta en un papel:

1. Si mandas la orden 1003 al router tal como está, sin la llamada, ¿qué le contesta a la tienda? ¿Llega algo a la
   API?
2. Cuando ya esté la llamada, ¿en qué orden esperas ver pasar la orden por los logs de los dos servicios?
3. Dentro del contenedor del router, ¿a qué máquina crees que apunta `localhost`?

## 5. Escribe la llamada

En el lugar del TODO, agrega un paso `to` que haga un `POST` a la ruta `/integrar` de int-rest, en su puerto 8080.
Llámalo **por su nombre de servicio**, `int-rest`, como int-rest llama a la API por `api-pedidos`. Usa
`throwExceptionOnFailure: false` para que, si int-rest contesta con error, el router lo anote en lugar de tronar:
la siguiente línea del log dice qué contestó.

Si te atoras, la llamada de int-rest a la API tiene exactamente la misma forma.

## 6. Levántalo y mándale una orden

Desde la carpeta de la lección:

```bash
DONDE=mio docker compose -f compose.yaml up -d
DONDE=mio docker compose -f compose.yaml logs -f
```

El segundo comando muestra los logs de los dos servicios juntos, cada renglón con el nombre del que lo escribió.
Espera a ver `Started orden-router` y `Started int-rest`. En **otra** terminal, desde la raíz de la práctica:

```bash
curl -i -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' -d @datos/ordenes/orden-1003.json
```

En los logs deberías ver la orden pasar por los dos servicios:

```
orden-router-1  | Orden 1003: se la paso a int-rest
int-rest-1      | Recibí del router la orden 1003 (sobre orden-1003-creada, tipo mx.tienda.orden.creada)
int-rest-1      | La API contestó 201 para la orden 1003
orden-router-1  | Orden 1003: int-rest contestó 201
```

¿Coincide con tu predicción 2? La respuesta regresa por el mismo camino: la API le contesta a int-rest, int-rest al
router y el router a la tienda.

Si la API contesta `200` en vez de `201`, ya tenía la orden 1003 de la lección 03. Reiníciala para que empiece vacía:
`docker compose -f infra/compose.yaml restart api-pedidos`.

## 7. Rómpelo a propósito: localhost

Ahora la pregunta 3. En la ruta del router, cambia `int-rest` por `localhost`:

```yaml
uri: http://localhost:8080/integrar
```

Apaga y vuelve a levantar los servicios para que tomen el cambio, y manda la orden otra vez:

```bash
DONDE=mio docker compose -f compose.yaml down
DONDE=mio docker compose -f compose.yaml up -d
```

La tienda recibe un **404** y el log del router dice `int-rest contestó 404`. Pero int-rest nunca recibió nada.
**Dentro de un contenedor, `localhost` es ese mismo contenedor.** El router se llamó a sí mismo, buscó una ruta
`/integrar` que él no tiene y se contestó «no existe». En tu máquina, `localhost` es tu máquina; en cada contenedor,
es el contenedor.

Por eso los servicios se llaman por su **nombre**: Docker sabe en qué contenedor vive `int-rest`, aunque mañana
cambie de dirección. Regresa el nombre y apaga:

```bash
DONDE=mio docker compose -f compose.yaml down
```

## 8. Comprueba

```bash
./verifica
```

`verifica` reinicia la API para que empiece vacía, levanta los dos servicios, manda la orden 1003 y al final los
apaga. Está terminada cuando ves las seis marcas verdes:

```
✅ El router arrancó
✅ int-rest arrancó (la misma imagen, otra carpeta)
✅ La tienda mandó la 1003 al router y la respuesta final fue 201
✅ La API registró la orden 1003: el recorrido tienda → router → int-rest → API se completó
✅ int-rest recibió el sobre CloudEvents de la orden (id orden-1003-creada)
✅ int-rest no se ve desde tu máquina: solo el router lo alcanza
```

La cuarta la confirma la API desde su propio registro: no basta con que el router diga que pasó la orden.

**¿No te corre?** `./verifica solucion` prueba la nuestra, y
`diff mio/orden-router/orden-router.camel.yaml solucion/orden-router/orden-router.camel.yaml` te dice en qué son
distintas.

| Lo que ves | Qué haces |
|---|---|
| `La tienda esperaba 201 y le llegó 200` | El router devolvió la orden sin llamar a nadie: falta el paso `to` |
| `La tienda esperaba 201 y le llegó 404` | La llamada apunta a `localhost`: vuelve al paso 7 |
| `El puerto 8080 está ocupado` | Quedó otra ruta corriendo, o los servicios de esta lección: `DONDE=mio docker compose -f compose.yaml down` |
| `El router no arrancó` | `verifica` muestra las últimas líneas del log: casi siempre es la sangría del paso nuevo |

---

## Ejercicio: un especialista más

Sin escribir código, contesta en un papel: si mañana quisieras un servicio `int-erp` que entregue la orden al ERP
con la ruta de la 05, ¿qué carpeta crearías, qué pondrías en ella y qué renglones agregarías al `compose.yaml`?
¿Le pondrías `ports`? ¿Qué cambiaría en el router?

---

## Por qué se hizo así

Lo que decidimos en esta lección, con lo que descartamos y qué nos haría cambiar de idea:

- [Una sola imagen; cada servicio es una carpeta](../decisiones/09-una-imagen-un-servicio-por-carpeta.md)
- [Los servicios se hablan por nombre, sobre HTTP](../decisiones/10-servicios-por-nombre-sobre-http.md)
- [CloudEvents en modo binario como sobre](../decisiones/11-cloudevents-como-sobre.md)

## Repaso

1. ¿Qué hace el router y qué hace int-rest? ¿Por qué conviene separarlos?
2. Si los dos servicios usan la misma imagen, ¿qué los hace distintos?
3. ¿Por qué el router llama a `int-rest` y no a `localhost`?
4. ¿Por qué int-rest no tiene `ports` en el compose?
5. ¿Qué dicen los encabezados `ce-type`, `ce-subject` y `ce-id` de la orden 1003?

**Siguiente:** en la lección 09 sigues una orden de servicio en servicio con el sobre CloudEvents, y descubres qué
pasa cuando un servicio no lo reenvía.
