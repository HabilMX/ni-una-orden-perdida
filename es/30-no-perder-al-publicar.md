# Lección 30 — No perder al publicar

> **Lo que vas a lograr:** cerrar el último hueco de la práctica. Si Kafka está caído justo cuando llega una orden, la
> orden se guarda, la tienda recibe su `202` y, cuando Kafka vuelve, la orden sale sola a sus tres canales, sin que
> nadie haga un rerun.

**Antes de empezar necesitas:** las lecciones 17 y 29 terminadas, el ambiente de la 00 encendido y Kafka arriba
(`docker compose -f infra/compose.yaml --profile kafka up -d --wait`).

## Lo nuevo de hoy, en una frase

Desde la 28 el router hace dos cosas seguidas: **guarda** la orden en la base de estado y **después** publica el
evento en Kafka. Son dos sistemas distintos, y ninguna transacción abarca a los dos. Si Kafka está caído en ese
instante, la orden quedó guardada y la tienda ya tiene su `202`, pero el evento no salió, y nadie lo vuelve a
intentar. Al revés tampoco sirve: publicar antes de guardar deja un evento de una orden que, si la base falla, no
existe. Hoy el router guarda **lo que hay que publicar** en la misma transacción que la orden, en una tabla llamada
**outbox**, y un sondeo lo publica cuando Kafka puede recibirlo. El patrón se llama *Transactional Outbox*.

**La imagen:** la charola de «por enviar» de una oficina. Quien atiende al cliente no sale corriendo a la paquetería
con cada sobre: llena el formato y, **en el mismo momento**, deja el sobre en la charola. El mensajero pasa cada
rato y se lleva lo que haya. Si un día la paquetería está cerrada, los sobres se quedan en la charola, no en el
bolsillo de nadie, y salen cuando abra.

```
antes:  guardar (transacción) → 202 → publicar en Kafka           ← si Kafka está caído aquí, se pierde
hoy:    guardar orden + operaciones + outbox (UNA transacción) → 202
        cada 1 s: outbox pendiente → publicar → ¿Kafka lo recibió? sí → «publicado» · no → espera la siguiente vuelta
```

---

## 1. Prepara tu carpeta

```bash
cd lecciones/30-no-perder-al-publicar
cp -R inicio mio
```

Abre `infra/postgres/01-estado.sql` y busca `CREATE TABLE outbox`: cada fila apunta a una `operacion` y tiene una
columna `publicado`, vacía hasta que Kafka recibió el evento.

El sondeo ya está hecho: es la ruta `sondeo-outbox` de `mio/orden-router/reparto.camel.yaml`. Léela. Cada segundo
toma lo pendiente **en el orden en que entró** y, si algo no sale, lo que sigue espera a la siguiente vuelta
(`stopOnException`): así lo de una misma orden no sale en desorden. El barrido de la 17 ya deja en el outbox lo que
suelta.

Tu trabajo va en dos archivos:

| TODO | Archivo | Qué haces |
|---|---|---|
| 1 | `mio/orden-router/estado.camel.yaml` | Al guardar una orden nueva, dejar en el outbox una fila por cada operación (momento `creada`) |
| 2 | El mismo | Al guardar un momento nuevo (embarque, entrega), lo mismo para las operaciones que nacieron `pendiente` |
| 3 | `mio/orden-router/orden-router.camel.yaml` | Quitar el envío directo a `seda:repartir`: desde hoy, solo publica el outbox |

## 2. Predice

Contesta en un papel:

1. Con la versión de la 29, Kafka está caído y llegan la 4002 y la 4003. ¿La tienda recibe su `202`? ¿Cuántas filas
   hay en el outbox? ¿Qué pasa con las órdenes cuando Kafka vuelve?
2. ¿Por qué la fila del outbox tiene que escribirse en la **misma** transacción que la operación, y no justo
   después?
3. Si dejas el envío directo y además el outbox, ¿qué le pasa a cada momento? ¿La API lo crearía dos veces?

Mira cómo está hoy:

```bash
./verifica inicio
```

Con Kafka caído, el router guarda las dos órdenes y contesta `202`, pero el outbox se queda **vacío**. Cuando Kafka
vuelve, las 6 operaciones siguen pendientes: nadie las vuelve a mandar.

## 3. La charola de «por enviar»

### TODO 1: la orden nueva

En `mio/orden-router/estado.camel.yaml` busca el TODO 1. Está dentro de la transacción que guarda la orden y sus
operaciones. Ahí mismo, un `INSERT … SELECT` deja una fila por cada operación que se acaba de guardar:

```yaml
- to:
    uri: "sql:INSERT INTO outbox (operacion) SELECT id FROM operacion WHERE orden = :#${variable.orden} AND momento = 'creada' ORDER BY id"
    parameters:
      dataSource: "#estado"
```

El `ORDER BY id` hace que las filas entren en el orden en que nacieron las operaciones, y el sondeo publica en ese
orden.

### TODO 2: un momento nuevo

Más abajo, en la misma transacción que guarda el embarque o la entrega, el TODO 2. Es el mismo `INSERT`, con dos
diferencias:

| | TODO 1 | TODO 2 |
|---|---|---|
| Qué momento | `'creada'` | `:#${variable.momento}` |
| Qué operaciones | Todas | Solo las que nacieron `pendiente`: `AND estado = 'pendiente'` |

¿Por qué solo las pendientes? Porque las `estacionada` (la 17) todavía no pueden salir. Esas las deja en el outbox el
barrido, cuando las suelta.

### TODO 3: un solo camino

En `mio/orden-router/orden-router.camel.yaml`, en la ruta `contestar-y-repartir`, quita el paso `to: seda:repartir`.
Si lo dejas, cada momento sale por **dos** caminos: el directo y el outbox. La API lo reconocería por su llave (la 18),
pero cada canal recibiría dos eventos y el router anotaría dos intentos. Un solo camino es más fácil de explicar y de
rastrear.

## 4. Rompe uno a propósito

Con tu versión terminada, borra el `INSERT` del TODO 1 y corre `./verifica`.

Antes, predice: Kafka está arriba al principio. ¿La 4001 llega?

No. La verifica se detiene en la segunda marca: la 4001 no llega a ninguno de sus canales. Ya quitaste el envío
directo (TODO 3), así que el outbox es **el único camino**, y una orden que no deja su fila no la publica nadie. Antes
del outbox, «Kafka está arriba» bastaba; ahora lo que manda es la fila guardada. Regrésalo.

## 5. Comprueba

```bash
./verifica
```

`verifica` manda la 4001 con todo arriba, **detiene Kafka**, manda la 4002 y la 4003, revisa el outbox y la API
durante unos segundos, vuelve a levantar Kafka y espera a que todo salga. Está terminada cuando ves las ocho marcas
verdes:

```
✅ Arrancaron el router, los tres canales con su puente y el sondeo
✅ La 4001 llegó a sus tres canales
✅ Con Kafka caído, el router guardó la 4002 y la 4003 y contestó 202 a las dos
✅ En el outbox esperan 6 filas sin publicar, una por operación de la 4002 y la 4003
✅ Control: mientras Kafka está caído, la API no tiene ninguna de las dos
✅ Kafka volvió y lo guardado salió solo: la API, el ERP y el socio tienen la 4002 y la 4003
✅ En la API se creó cada una UNA vez (2 creaciones para 2 órdenes)
✅ El outbox quedó sin pendientes: todo lo que se guardó, salió
```

La quinta es un **control**: si con Kafka caído la API ya tuviera las órdenes, la sexta marca no probaría que el
outbox las sacó.

Mira tú el outbox, con los servicios arriba:

```bash
docker compose -f ../../infra/compose.yaml exec estado psql -U router -d estado \
  -c "SELECT x.id, p.orden, p.canal, x.publicado FROM outbox x JOIN operacion p ON p.id = x.operacion ORDER BY x.id"
```

**¿No te corre?** `./verifica solucion` prueba la nuestra, y `diff -r mio solucion` te dice en qué son distintas.

| Lo que ves | Qué haces |
|---|---|
| `A la base de estado le falta la tabla outbox` | Tu base es de antes de la 30: recréala con el comando que trae el mensaje (`--force-recreate -V estado`) |
| `La 4001 no llegó a sus tres canales` | Falta el TODO 1: con el TODO 3 hecho, sin fila en el outbox no se publica nada |
| `En el outbox esperaba 6 filas sin publicar … y hay 0` | El `INSERT` del TODO 1 no corre: revisa que esté dentro de la transacción, después de guardar las operaciones |
| `Kafka volvió y lo guardado no salió` | Mira qué canal quedó en qué estado (lo dice el mensaje) y revisa la ruta `sondeo-outbox` en el log del router |
| `En la API hubo … creaciones para 2 órdenes` | La API creó alguna dos veces: ¿cambiaste la llave que int-rest le manda? (la 18) |
| `El outbox tiene … filas sin publicar` | Algo quedó atorado: revisa el log del router, debajo de `sondeo-outbox` |
| `El puerto 8080 está ocupado` | Quedaron arriba los servicios de otra corrida: `DONDE=mio docker compose -f compose.yaml down` |

---

## Lo que esto enseña fuera de la práctica

**Dos sistemas no comparten una transacción.** La base y Kafka confirman cada uno lo suyo. Si una acción tiene que
quedar en los dos, se guarda en uno solo (la base, con la orden) y el otro se alimenta desde ahí.

**Lo guardado es lo que sale.** El sondeo publica lo que el outbox dice, leyendo la base. Si se reinicia el router a
la mitad, la fila sigue ahí.

**Publicar de más se tolera; publicar de menos no.** Si el sondeo publica y se cae antes de marcar `publicado`, la
fila sale otra vez. Por eso quien lee tiene que reconocer repetidos (la 18 y la 29). Perder un evento, en cambio, no
lo nota nadie hasta que un cliente reclama.

**Un solo camino.** Mantener el envío directo «por si acaso» junto al outbox duplica todo y no arregla nada: el
outbox ya cubre el caso en que Kafka está arriba.

## Ejercicio: la charola

1. Detén Kafka con tus servicios arriba, manda una orden y corre la consulta del paso 5. ¿Qué dice la columna
   `publicado`? Levanta Kafka y vuelve a correrla.
2. En papel: el router guardó la orden y su fila del outbox, el sondeo la publicó y el router se cayó antes de marcar
   `publicado`. ¿Qué pasa al volver? ¿Qué pieza evita que la API la cree dos veces?
3. Haz una lista de las formas de perder una orden que viste en la práctica (la tienda que no recibe el `202`,
   el ERP caído, el 997 que no llega, el evento confirmado sin desenlace…). Al lado de cada una, la lección que la
   cierra.

---

## Por qué se hizo así

Lo que decidimos en esta lección, con lo que descartamos y qué nos haría cambiar de idea:

- [Lo que hay que publicar se guarda en la misma transacción (outbox), y un sondeo lo publica](../decisiones/34-el-outbox-publica-lo-guardado.md)

## Repaso

1. ¿Qué hueco queda entre guardar y publicar, y por qué no se arregla publicando primero?
2. ¿Qué guarda la tabla `outbox`, y en qué transacción se escribe?
3. ¿Por qué el TODO 2 deja fuera las operaciones `estacionada`?
4. ¿Qué pasaría si dejaras el envío directo junto al outbox?
5. **La pregunta de cierre:** una tienda manda una orden con Kafka caído. Cuenta, paso por paso, qué pasa desde el
   `202` hasta que el socio tiene su 850, y en qué tablas queda rastro.

**Siguiente:** con esta lección termina la práctica en Docker Compose. Si tu equipo trabaja con Kubernetes o quiere
ver la integración sin contenedores, el apéndice (lecciones 31 y 32) es de lectura y es opcional.

---

> **Qué hace Hábil**
>
> En esta práctica una orden cruzó una API, un ERP y un socio que habla X12, y aprendiste a que no se perdiera en
> ninguno de los huecos: el destino caído, la respuesta que no llega, el aviso repetido, el evento que no se publicó.
> En una empresa, esos huecos están entre sistemas que se hablan poco, y lo que se pierde ahí lo nota primero el
> cliente: «un pedido que la tienda cobró y que el almacén no vio».
>
> En Hábil construimos a la medida la integración entre su tienda, su ERP, sus socios y sus canales. Para ese
> problema está **[Hábil MDW](https://www.habil.mx/es/habil-mdw/?utm_source=github&utm_medium=referral&utm_campaign=2026-11_ni-una-orden-perdida_es)**. Hábil MDW es la capa que se pone entre los
> sistemas que ya tiene. Ahí pasa cada pedido, cada cobro, cada póliza y cada aviso, con la respuesta de quien lo
> recibió y el motivo si algo falló. Cada sistema entra con su forma, y la plataforma lo traduce a un modelo común: un
> pedido es un pedido, venga de donde venga.
>
> Conectamos los sistemas de un primer flujo completo —por ejemplo, del pedido al cobro y a la factura—, definimos con
> usted los pasos y las reglas, y lo dejamos funcionando con el rastro a la vista de su equipo.
>
> **[Pida su primera conversación, sin costo.](https://wa.me/525667541323?text=Hola%2C%20vengo%20de%20GitHub%20%28Ni%20una%20orden%20perdida%29.%20Me%20interesa%20una%20primera%20conversaci%C3%B3n.%0AIndustria%3A%0APuesto%3A%0ASistemas%20principales%3A)**
