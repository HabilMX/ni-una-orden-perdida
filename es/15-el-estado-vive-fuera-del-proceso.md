# Lección 15 — El estado vive fuera del proceso

> **Lo que vas a lograr:** que el router **guarde** cada orden en su propia base antes de contestarle a la tienda,
> con una fila por canal y un registro de cada intento, y que con eso una orden no se pierda aunque un destino
> esté caído.

**Antes de empezar necesitas:** la lección 14 terminada, el ambiente de la 00 encendido (la base de estado,
`estado`, es la que levantaste desde el primer día y hasta hoy no se usaba) y tu respuesta a la pregunta de
cierre de la 12.

## Lo nuevo de hoy, en una frase

Hasta la 14, el router recibía la orden, llamaba a los tres canales, esperaba y le contestaba a la tienda lo que
dijo cada uno. Si el router se caía a la mitad, nadie sabía qué destinos ya tenían la orden. Desde hoy el router
**primero anota y después reparte**: guarda la orden en PostgreSQL, le contesta a la tienda **202 «recibida»** y,
ya con la tienda tranquila, sale a repartir.

**La imagen:** la ventanilla de una paquetería. Te reciben el paquete, lo registran en el sistema y te dan tu
comprobante. No te quedas en el mostrador hasta que llegue a su destino. El comprobante vale porque **el paquete ya
quedó registrado**, no porque el empleado se acuerde de ti. Si cambia el turno, el que llega lee el registro.

Regresa a tu respuesta de la 12: «si el router tuviera réplicas, ¿dónde viviría lo de cada orden?». Hoy la
contestamos: **fuera del proceso, en una base**. Así cualquier copia del router puede leerlo, y si una se cae, lo
anotado sigue ahí.

---

## 1. Prepara tu carpeta

```bash
cd lecciones/15-el-estado-vive-fuera-del-proceso
cp -R inicio mio
```

El router ahora tiene tres archivos. Ábrelos en este orden:

| Archivo | Qué hace | ¿Lo escribes? |
|---|---|---|
| `orden-router.camel.yaml` | Recibe la orden o el embarque, manda guardarlo, contesta 202 y encola el reparto | No, léelo |
| `estado.camel.yaml` | Guarda en la base. Aquí está `guardar-orden`, tu trabajo | **Sí** |
| `reparto.camel.yaml` | Lee de la base lo pendiente, anota el intento, llama al canal y anota cómo le fue | No, léelo |

Y abre el esquema de la base, `infra/postgres/01-estado.sql`. Hoy importan tres tablas:

| Tabla | Una fila por… | Lo importante |
|---|---|---|
| `orden` | orden | En qué momento va (`creada`, `embarcada`, `entregada`) y sus `datos`, la orden tal como llegó |
| `operacion` | orden, momento y canal | Qué hay que hacer en cada destino y cómo quedó (`estado`: `pendiente`, `aceptada`, `rechazada`, `no_se_sabe`) |
| `intento` | vez que se intentó una operación | La huella de lo que salió, la versión del mapeo y el desenlace |

Una orden de franquicia, al crearse, deja **una** fila en `orden` y **tres** en `operacion`: `rest`, `bd` y `edi`.

## 2. Predice

Contesta en un papel:

1. Hoy el router contesta 202 aunque `guardar-orden` no guarda nada. ¿Qué crees que pasa con la orden después del
   202? (Pista: el reparto lee de la base lo pendiente.)
2. Si el ERP está apagado cuando llega la orden, ¿qué debería contestarle el router a la tienda? ¿Y qué debería
   quedar en la base?

Mira cómo está hoy:

```bash
./verifica inicio
```

La tienda recibe su 202, y después no pasa nada: la base no tiene la orden y ningún destino la recibe. **El router
prometió algo que no cumplió.** Es el peor error de esta práctica, porque la tienda cree que todo salió bien.

## 3. Guarda antes de contestar

Abre `mio/orden-router/estado.camel.yaml` y busca `guardar-orden`. Tiene tres TODO. Tu modelo está justo abajo:
`guardar-momento` hace lo mismo para el embarque. Léelo completo antes de escribir.

| TODO | Qué escribes | Para qué |
|---|---|---|
| 1 | `transacted` con `ref: todoONada` | Que la orden y sus operaciones queden **todas o ninguna** (la misma idea de la 05, ahora en el estado) |
| 2 | Un `to` con `sql:INSERT INTO orden (orden, momento, datos) VALUES (…)` | La fila de la orden: su número, el momento `'creada'` y la orden completa como `jsonb` |
| 3 | Un `split` sobre `${variable.canales.split(',')}` con un `INSERT INTO operacion …` adentro | Una operación por canal, en `'pendiente'`, con su hilo. La del socio, con su número de control |

Tres pistas:

- Las variables que necesitas ya existen cuando se llama a esta ruta: `orden`, `canales` y `correlacion`. El cuerpo es
  la orden como la mandó la tienda.
- Para guardar el cuerpo como `jsonb`: `CAST(:#${body} AS jsonb)`. `:#${…}` es cómo el componente `sql` pasa un
  valor como parámetro, sin pegarlo al texto del SQL.
- El `INSERT INTO operacion` de `guardar-momento` sirve casi igual: lo que cambia es el momento.

Fíjate en la línea del número de control:
`CASE WHEN :#${body} = 'edi' THEN CAST(nextval('numero_control_seq') AS text) END`. Solo la operación del socio
pide un número a la secuencia. **Desde hoy el número de control del X12 lo asigna el router**, no sale del número de
orden como en la 06 y la 13.

## 4. Rompe uno a propósito

Con tu `guardar-orden` terminado, apaga la base de estado y mándale una orden al router:

```bash
DONDE=mio docker compose -f compose.yaml up -d
docker compose -f ../../infra/compose.yaml stop estado
curl -i -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' -d @../../datos/ordenes/tramo-2/orden-4003.json
```

Antes de mandarla, predice: ¿el router contesta 202?

No lo hace: contesta un error. Sin base no puede guardar, y **sin guardar no promete**. La tienda ve el error y sabe
que tiene que volver a intentar. Es justo lo contrario de `inicio/`, que contestaba 202 sin guardar. Vuelve a
encender todo:

```bash
docker compose -f ../../infra/compose.yaml start estado
DONDE=mio docker compose -f compose.yaml down
```

## 5. Comprueba

```bash
./verifica
```

`verifica` manda la 4001 y su embarque, revisa la base y los destinos, y al final apaga el ERP y manda la 4002. Está
terminada cuando ves las doce marcas verdes:

```
✅ Arrancaron el router (con su base de estado), los tres canales y el sondeo
✅ La tienda mandó la 4001 y el router contestó 202 «recibida»
✅ Estado: una fila de la orden 4001, con sus datos
✅ Estado: tres operaciones de la 4001, una por canal, y las tres aceptadas
✅ Estado: cada operación tiene su intento, con huella sha256, versión del mapeo y desenlace
✅ Destinos: la API tiene la 4001 CREADO y el ERP REGISTRADO
✅ Socio: el 850 de la 4001 lleva en el ISA el número de control de la base (…)
✅ Estado: el embarque de la 4001 tiene sus tres operaciones aceptadas y la orden va en «embarcada»
✅ Socio: el 856 lleva su propio número de control (…), distinto del 850 (…)
✅ Destinos: la API tiene la 4001 EMBARCADO
✅ Con el ERP caído, la tienda recibió su 202 por la 4002
✅ Estado: la operación del ERP quedó sin aceptar y está guardada (…); las otras dos siguieron
```

Los números de control cambian en cada corrida: los da la secuencia.

Mira las dos últimas. Con el ERP caído, la tienda **sí** recibió su 202, porque el router guardó, y lo que no salió
quedó anotado: la operación `bd` de la 4002 está en `no_se_sabe` o `rechazada`, mientras la API y el socio ya la
tienen. En la 14 eso se perdía en un log. Hoy está en una fila que se puede consultar.

**¿No te corre?** `./verifica solucion` prueba la nuestra, y
`diff mio/orden-router/estado.camel.yaml solucion/orden-router/estado.camel.yaml` te dice en qué son distintas.

| Lo que ves | Qué haces |
|---|---|
| `no está la fila de la orden 4001` | Falta el INSERT de `orden`, o no lleva `dataSource: "#estado"` |
| `esperaba «bd=aceptada edi=aceptada rest=aceptada» y hay «nada»` | Falta el `split` de las operaciones, o los INSERT quedaron fuera de él |
| `column "datos" is of type jsonb but expression is of type…` | Te faltó el `CAST(:#${body} AS jsonb)` |
| `el 850 … no lleva el número de control de la base` | La operación `edi` se guardó sin `numero_control`: revisa el `CASE WHEN` |
| `El puerto 8080 está ocupado` | Quedaron arriba los servicios del paso 4: `DONDE=mio docker compose -f compose.yaml down` |

---

## Lo que esto enseña fuera de la práctica

**Contestar «recibido» es una promesa, y solo se cumple si ya quedó escrito.** Por eso el router guarda primero. Es
lo mismo que hace un banco con una transferencia: te da el folio cuando ya está en su libro, no cuando ya llegó.

**El estado fuera del proceso es lo que deja crecer al router.** Un proceso que recuerda en su memoria no se puede
copiar: cada copia recordaría cosas distintas, y al reiniciarse olvidaría. Con la base, cualquier copia lee lo
mismo.

**Separar «recibí» de «entregué» cambia la conversación.** La tienda ya no espera a que contesten los tres destinos.
A cambio, alguien tiene que mirar lo que quedó pendiente: eso es lo que construye la parte 3.

## Ejercicio: la orden en tres tablas

Con tu versión terminada, corre `./verifica` y entra a la base:

```bash
docker compose -f ../../infra/compose.yaml exec estado psql -U router -d estado
```

1. En papel, **dibuja** la orden 4001 en las tres tablas: una caja para su fila de `orden`, una por cada `operacion`
   (creación y embarque) y una por cada `intento`, con flechas de cuál apunta a cuál.
2. Escribe la consulta que te dice, para la 4002, en qué estado va cada canal. Empieza con
   `SELECT canal, estado FROM operacion WHERE orden = '4002'` y agrégale el momento.
3. ¿Qué número de control tiene el 850 de la 4001, y cuál tenía en la 06? ¿De dónde sale cada uno, y por qué uno
   que da la base no se repite aunque dos órdenes lleguen al mismo tiempo?

---

## Por qué se hizo así

Lo que decidimos en esta lección, con lo que descartamos y qué nos haría cambiar de idea:

- [Guardar antes de contestar](../decisiones/18-guardar-antes-de-contestar.md)
- [El número de control del X12 lo asigna el router](../decisiones/19-el-numero-de-control-lo-asigna-el-router.md)

## Repaso

1. ¿Qué guarda cada una de las tres tablas, `orden`, `operacion` e `intento`?
2. ¿Por qué el router guarda la orden **antes** de contestar 202, y no después?
3. ¿Para qué va la transacción en `guardar-orden`? ¿Qué pasaría sin ella si el segundo INSERT fallara?
4. Con el ERP caído, ¿qué recibió la tienda y qué quedó en la base? ¿Qué pasaba con esa misma orden en la 14?
5. **La pregunta de cierre:** desde hoy el número de control del X12 lo asigna el router. ¿De dónde salía en la 06,
   y qué cambió? Compara con tu respuesta de la 12 sobre dónde vive lo de cada orden.

**Siguiente:** en la lección 16 llega el tercer momento: la paquetería avisa que entregó. Nadie le pregunta: llama
sola, por un webhook. ¿Qué tiene que pasar en el estado y en el SFTP?
