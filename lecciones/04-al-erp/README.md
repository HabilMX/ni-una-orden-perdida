# Lección 04 — La orden llega al ERP

> **Lo que vas a lograr:** registrar la orden en el ERP de la empresa, que no recibe JSON sino filas en tablas:
> una cabecera, un renglón de detalle por cada partida, y al final un procedimiento que revisa que todo cuadre.

**Antes de empezar necesitas:** la lección 03 terminada y el ambiente de la 00 encendido:

```bash
docker compose -f infra/compose.yaml up -d --wait     # desde la carpeta raíz de la práctica
```

## Lo nuevo de hoy, en una frase

Hasta ahora el destino era una API que recibe un JSON completo de un solo golpe. El ERP es distinto: guarda la
información en **tablas**, como hojas de cálculo, y cada pedido ocupa **varias filas en dos tablas**. Hoy tu ruta
parte la orden en pedazos y la escribe fila por fila.

**Tres palabras que vas a usar:**

| Palabra | Qué es | Una imagen |
|---|---|---|
| **ERP** | El sistema central donde la empresa registra lo que vende, compra y factura | El libro de contabilidad de la empresa |
| **SQL** | El lenguaje para hablar con una base de datos: `INSERT` agrega una fila, `SELECT` las consulta | Las instrucciones que le dictas al que lleva el libro |
| **Procedimiento almacenado** | Un programa que vive **dentro** de la base y se llama con `CALL` | El contador que revisa la página antes de firmarla |

---

## 1. Prepara tu carpeta

```bash
cd lecciones/04-al-erp
cp -R inicio mio
```

## 2. Conoce las tablas del ERP

El ERP tiene dos tablas. Así se vería la orden 1001 ya registrada:

**`pedido_cabecera`** — una fila por pedido

| pedido | tienda | fecha | total | estatus |
|---|---|---|---|---|
| 1001 | T-014 | 2026-11-12 10:15:00 | 428.00 | REGISTRADO |

**`pedido_detalle`** — una fila por cada partida del pedido

| pedido | posicion | sku | cantidad | precio |
|---|---|---|---|---|
| 1001 | 1 | SKU-0001 | 2 | 89.50 |
| 1001 | 2 | SKU-0042 | 1 | 249.00 |

La columna `pedido` del detalle es lo que **une** cada renglón con su cabecera. Por eso la cabecera se escribe
primero: el ERP no acepta un renglón de detalle de un pedido que todavía no existe.

Y el `estatus` empieza en `RECIBIDO`. Solo cambia a `REGISTRADO` cuando el procedimiento `registrar_pedido` revisa
que la suma del detalle sea igual al total de la cabecera. Si quieres ver cómo está hecho, está en
`infra/mariadb/01-erp.sql`; no necesitas cambiarlo.

## 3. Lee el mapeo y la ruta

**El mapeo ya viene hecho:** `mio/mappings/erp/pedido/v1/mapeo.jsonata`. Convierte la orden en un JSON con dos
partes, `cabecera` y `detalle`, que tienen exactamente los nombres de las columnas. Pruébalo como en la 03:

```bash
../../herramientas/probar-mapeo mio/mappings/erp/pedido/v1
```

Fíjate en la fecha. La tienda manda `2026-11-12T10:15:00-06:00` y el mapeo la deja en `2026-11-12 10:15:00`. Le
quita la zona horaria (`-06:00`) porque la columna del ERP es de tipo `DATETIME`, que guarda la hora local de la
tienda y no sabe de zonas.

**La ruta:** abre `mio/orden-al-erp.camel.yaml`. Arriba de la ruta hay un **bean** llamado `erp`: es la conexión a
la base, con su dirección, usuario y contraseña. Los pasos `sql:` lo usan con `dataSource: "#erp"`.

La ruta ya hace esto:

```
POST /ordenes  →  traducir  →  guardar cabecera y detalle en variables  →  INSERT de la cabecera  →  (te toca)
```

Mira el `INSERT` de la cabecera:

```yaml
uri: sql:INSERT INTO pedido_cabecera (pedido, tienda, fecha, total) VALUES (:#pedido, :#tienda, :#fecha, :#total)
```

Cada `:#nombre` es un hueco que Camel llena con el campo de ese nombre **que trae el cuerpo del mensaje**. Por eso,
justo antes, la ruta pone la cabecera como cuerpo con `setBody`.

Una cosa más: el controlador de MariaDB, la pieza que le enseña a Camel a hablar con esa base, no viene incluido.
Lo pide el archivo `dependencias.txt`, y `herramientas/camel` lo descarga al arrancar.

## 4. Predice

La ruta de `inicio/` solo escribe la cabecera. Antes de completarla, contesta en un papel:

1. Si mandas la orden 1001 con la ruta tal como está, ¿qué filas quedan en cada tabla, y con qué estatus?
2. Si agregaras el `CALL registrar_pedido` **sin** escribir el detalle, ¿qué contestaría el procedimiento?

Puedes comprobar la primera ahora mismo. Desde `mio/`, arranca la ruta:

```bash
cd mio
../../../herramientas/camel run orden-al-erp.camel.yaml
```

En otra terminal, desde la misma carpeta `mio/`, manda la orden y pregúntale al ERP:

```bash
curl -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' -d @../../../datos/ordenes/orden-1001.json

docker compose -f ../../../infra/compose.yaml exec erp \
  mariadb -uintegracion -ppractica erp -e "SELECT * FROM pedido_cabecera; SELECT * FROM pedido_detalle;"
```

Compara las dos respuestas. **Tu ruta** le contestó a la tienda `{"pedido":"1001","erp":"REGISTRADO"}`. **El ERP**
dice otra cosa: la cabecera está en `RECIBIDO` y el detalle está vacío. La ruta no miente a propósito: su último
paso escribe esa respuesta sin preguntarle nada al ERP. Por eso en esta práctica lo que cuenta es lo que guardó el
destino, nunca lo que dice la ruta.

Un pedido con cabecera y sin detalle es un pedido a medias. Para el ERP existe, pero no se puede surtir ni facturar.

## 5. Escribe el detalle y el cierre

Detén la ruta con `Ctrl+C`. En `mio/orden-al-erp.camel.yaml` hay dos comentarios que dicen **TE TOCA**.

**El detalle.** La lista de renglones está en `${variable.detalle}`, y necesitas un `INSERT` por cada uno. Para eso
existe el paso **`split`**: toma una lista, la parte en sus elementos y corre sus pasos una vez por cada elemento,
con ese elemento como cuerpo.

```yaml
- split:
    simple: "${variable.detalle}"
    steps:
      - to:
          uri: sql:INSERT INTO pedido_detalle (…) VALUES (…)
          parameters:
            dataSource: "#erp"
```

Completa tú las columnas y los huecos. Usa el `INSERT` de la cabecera como modelo; las columnas del detalle están
en la tabla del paso 2.

**El cierre.** Después del `split`, llama al procedimiento:

```yaml
uri: sql:CALL registrar_pedido(:#pedido)
```

Ojo con el cuerpo: después del `split`, ¿qué trae? El hueco `:#pedido` necesita un cuerpo que tenga el campo
`pedido`. ¿Qué paso de la ruta ya hizo eso antes del `INSERT` de la cabecera?

Este patrón tiene nombre en los EIP: **Splitter**. Una orden con varias partidas se parte en mensajes más chicos,
uno por partida, porque así los recibe el destino.

## 6. Compara con tu predicción

Corre la ruta otra vez y manda la orden 1001. Antes, vacía el ERP para empezar limpio:

```bash
docker compose -f ../../../infra/compose.yaml exec erp \
  mariadb -uintegracion -ppractica erp -e "DELETE FROM pedido_detalle; DELETE FROM pedido_cabecera;"
```

Consulta las dos tablas como en el paso 4. Ahora la cabecera debería decir `REGISTRADO` y el detalle tener dos filas.

¿Y la pregunta 2? Si llamas al procedimiento sin detalle, contesta `El total no cuadra con el detalle`: la suma de
cero renglones es 0, y el total es 428. **El ERP no confía en lo que le mandas: lo revisa.** Igual que la API en la
03, un destino que valida es lo que hace que un error se vea.

## 7. Comprueba

Detén la ruta con `Ctrl+C` y, desde la carpeta de la lección:

```bash
cd ..
./verifica
```

⚠️ `verifica` **vacía las tablas del ERP** antes de probar: es un ERP de práctica y así empieza desde cero cada vez.

Está terminada cuando ves las seis marcas verdes:

```
✅ El mapeo al ERP pasa sus casos dorados
✅ La ruta arrancó
✅ El ERP tiene las 2 cabeceras
✅ El pedido 1001 tiene sus 2 renglones de detalle
✅ El pedido 1003 tiene sus 3 renglones de detalle
✅ El procedimiento marcó los 2 pedidos REGISTRADO
```

`verifica` manda dos órdenes, la 1001 y la de los centavos (1003), y le pregunta al ERP qué guardó. Como siempre, lo
confirma el destino, no tu ruta.

**¿No te corre?** `./verifica solucion` prueba la nuestra, y
`diff mio/orden-al-erp.camel.yaml solucion/orden-al-erp.camel.yaml` te dice en qué son distintas.

| Lo que ves | Qué haces |
|---|---|
| `Al pedido 1001 le faltan renglones de detalle` | El `split` no corre o le falta un hueco: revisa que recorra `${variable.detalle}` |
| `Hay pedidos sin REGISTRADO` | Falta el `CALL`, o el cuerpo no trae `pedido`: revisa el paso 5 |
| `No pude hablar con el ERP: ¿corriste la 00?` | Enciende el ambiente: `docker compose -f infra/compose.yaml up -d --wait` |
| `El puerto 8080 está ocupado` | Quedó una ruta corriendo: detenla con `Ctrl+C` |

---

## Repaso

1. ¿Por qué un pedido ocupa filas en dos tablas, y qué columna las une?
2. ¿Por qué la cabecera se escribe antes que el detalle?
3. ¿Qué hace un `split`, y qué trae el cuerpo dentro de cada vuelta?
4. ¿De dónde saca Camel el valor de `:#sku`?
5. ¿Qué revisa `registrar_pedido` antes de marcar un pedido `REGISTRADO`?

**Siguiente:** ¿qué pasa si un renglón de en medio está mal y el ERP lo rechaza? La cabecera ya quedó escrita y el
detalle a medias. En la lección 04b vas a hacer que el ERP guarde **todo o nada**.
