# Lección 13 — Llega el embarque

> **Lo que vas a lograr:** recibir el archivo que deja la bodega cuando una orden sale, sin tomarlo a medias, sin
> tomarlo dos veces y sin perder el que llega roto. Ese archivo se vuelve un 856 para el socio y el estatus
> EMBARCADO en la API y en el ERP.

**Antes de empezar necesitas:** la lección 12 terminada y el ambiente de la 00 encendido. Esta lección usa dos
tablas nuevas del ERP, `embarque` y `entrega`. Si tu ERP es de antes, recréalo una vez:

```bash
docker compose -f infra/compose.yaml up -d --wait --build --force-recreate -V
```

## Lo nuevo de hoy, en una frase

Hasta aquí, la orden **llegaba** a tu integración: la tienda llamaba al router. Con el embarque es al revés: la
bodega no llama a nadie, **deja un archivo en una carpeta** y se va. Alguien tiene que ir a ver si hay algo nuevo.
Eso se llama sondear (*Polling Consumer*), y hoy lo configuras.

**La imagen:** el buzón de un edificio. El cartero deja las cartas y no toca el timbre. El conserje baja cada tanto
a revisar, se lleva solo los sobres cerrados (no la carta que alguien sigue escribiendo en la mesa), aparta lo que
ya entregó y deja a un lado lo que llegó roto. Y hay **un solo conserje**: si fueran tres, dos podrían subir la
misma carta.

Esta es la parte 2: la orden ya se creó; ahora sale de la bodega (hoy), se parte en varias (14), se guarda su
estado (15) y llega con el cliente (16 y 17).

---

## 1. Prepara tu carpeta

```bash
cd lecciones/13-llega-el-embarque
cp -R inicio mio
```

Hoy hay un servicio nuevo, `sondeo-bodega`. Revisa la carpeta `buzon/` de la lección, que en el `compose.yaml` se
monta dentro del contenedor del sondeo. Todo tu trabajo va en **un solo archivo**:
`mio/sondeo-bodega/sondeo-bodega.camel.yaml`, y dentro de él, solo en los parámetros del `from`.

Abre también un embarque de ejemplo, `datos/embarques/embarque-4001.json`: trae la orden, la paquetería, la
**guía** (el número con que la paquetería sigue el paquete) y qué renglones salieron.

## 2. Predice

Contesta en un papel antes de correr nada:

1. En la 12 aprendiste a correr tres copias de un servicio. Si tres copias revisaran la misma carpeta, ¿qué podría
   pasar con un archivo?
2. La bodega escribe el archivo poco a poco. ¿Qué pasa si el sondeo lo toma cuando va a la mitad?
3. Llega un archivo roto, que no es JSON válido. ¿Qué debería pasar con él: borrarse, quedarse donde está o
   apartarse?

Mira cómo está hoy:

```bash
./verifica inicio
```

Varias marcas salen en rojo. Léelas: cada una te dice qué cuidado le falta al sondeo. El sondeo de `inicio/` lee
todo lo que hay en `buzon/` y no aparta nada (`noop: true` deja cada archivo donde estaba).

## 3. Lo que ya está hecho: los canales se reusan

Antes de escribir, mira lo que no tienes que escribir. Abre `mio/orden-router/orden-router.camel.yaml` y busca
`platform-http:/embarques`. Es la puerta por donde el sondeo le entrega cada embarque al router. Más abajo está el
mismo `multicast` de la 10, con los mismos tres canales: `direct:canal-api`, `direct:canal-erp` y
`direct:canal-socio`. Lo único distinto es el **paso**: antes cada canal llamaba a `/integrar`; ahora llama a
`/embarque`.

| Canal | Servicio | Qué hace con el embarque |
|---|---|---|
| API | int-rest | Le cuenta a la API que la orden se embarcó, como un evento con su llave (`orden-4001-embarcada`) |
| ERP | int-bd | Escribe la fila de `embarque` con la guía y pasa el pedido a EMBARCADO |
| Socio | int-edi | Arma un **856**, el aviso de embarque de X12, y lo deja en el SFTP con los cuidados de la 07 |

Y la regla de la 11 sigue: solo las franquicias reciben EDI. Por eso hoy las órdenes son de las tiendas T-501 y
T-502.

## 4. Los cuatro cuidados del sondeo

El componente `file` de Camel ya sabe sondear. Tu trabajo es decirle **qué cuidados tener**, con cuatro parámetros.
Cada TODO del archivo te dice cuál:

| TODO | Parámetro | El cuidado | Del buzón del edificio |
|---|---|---|---|
| 1 | `include` | Tomar solo los archivos terminados: `embarque-<algo>.json`. El `.tmp` no coincide | Solo los sobres cerrados |
| 2 | `readLock` con `changed` | Esperar a que el archivo deje de crecer antes de tomarlo | Esperar a que el cartero suelte la carta |
| 3 | `move` y `moveFailed` (y quitar `noop`) | Apartar lo que el router recibió a `.camel/` y lo que falló a `.error/` | Lo entregado a un lado, lo roto a otro |
| 4 | `delay` | Cada cuánto revisar la carpeta: cada 2 segundos, es decir, 2000 milisegundos | Cada cuánto baja el conserje |

Dos pistas:

- `include` es una **expresión regular** sobre el nombre del archivo. El punto de `.json` tiene que ir escapado, y en
  YAML entre comillas dobles la barra se escribe dos veces: `\\.`
- Para el nombre del archivo apartado usa `${file:name}`: así `embarque-4001.json` queda como
  `.camel/embarque-4001.json`.

Fíjate en lo que **no** haces: no escribes un ciclo, no lees la carpeta tú, no borras nada. Los cuatro cuidados son
configuración, y el componente hace el resto.

## 5. Rompe uno a propósito

Con tu sondeo terminado, quita la línea de `moveFailed` y corre `./verifica`. Antes, predice: ¿a dónde va el
archivo roto si no le dices a dónde mandarlo?

Sale en rojo `Buzón: el embarque roto no está en .error/`. El archivo se queda en `buzon/`, y en el log del sondeo
lo vas a ver fallar una vez tras otra: sin `moveFailed`, Camel lo deja donde estaba y lo intenta de nuevo en la
siguiente vuelta. Un archivo roto que se reintenta para siempre no se pierde, pero tampoco avisa: llena el log y
esconde los errores de los demás. Regresa la línea.

## 6. Comprueba

```bash
./verifica
```

`verifica` crea las órdenes 4001 y 4002 en los tres destinos y luego deja tres cosas en el buzón: el embarque de la
4001, terminado; el de la 4002, todavía como `.tmp`; y uno roto. Está terminada cuando ves las diez marcas verdes:

```
✅ Arrancaron el router, los tres canales y el sondeo de la bodega
✅ Antes del embarque: la 4001 está CREADO en la API y REGISTRADO en el ERP
✅ API: la 4001 está EMBARCADO con la guía PAQX770004001
✅ API: recibió el embarque de la 4001 una sola vez
✅ ERP: la 4001 está EMBARCADO y su fila de embarque tiene la guía
✅ Socio: el 856 de la 4001 está en su SFTP y el lector X12 no le encontró fallas
✅ Socio: el 856 lleva la guía (REF*CN) y la orden (PRF)
✅ Buzón: el embarque de la 4001 se apartó a .camel/
✅ Buzón: el embarque roto se apartó a .error/ (no se perdió ni se reintenta sin fin)
✅ Buzón: el .tmp que la bodega aún escribía no se tocó (la 4002 sigue CREADO)
```

Mira la prueba en la carpeta misma: `ls -A buzon buzon/.camel buzon/.error`. Y abre el 856 que llegó al socio,
`mio/salida/856-4001.edi`: busca la guía en el segmento `REF*CN` y la orden en `PRF`.

**¿No te corre?** `./verifica solucion` prueba la nuestra, y
`diff mio/sondeo-bodega solucion/sondeo-bodega` te dice en qué son distintas.

| Lo que ves | Qué haces |
|---|---|
| `el sondeo tomó el .tmp` | El patrón de `include` no termina en `\\.json`: con `embarque-.*` a secas, `embarque-4002.json.tmp` también entra. O falta el `include` |
| `el embarque de la 4001 no está en .camel/` | Quedó `noop: true`, o el `move` no lleva `${file:name}` |
| `el embarque roto no está en .error/` | Falta `moveFailed` |
| `le faltan las tablas embarque y entrega` | Tu ERP es de antes de esta lección: recréalo como dice «Antes de empezar» |
| `El puerto 8080 está ocupado` | Dejaste corriendo los servicios de otra lección: `docker compose … down` en su carpeta |

---

## Lo que esto enseña fuera de la práctica

**Muchos sistemas no llaman: dejan archivos.** Bodegas, bancos y sistemas viejos entregan su trabajo como archivos
en una carpeta o en un SFTP, a la hora que pueden. La integración se adapta a lo que el otro sabe hacer, no al
revés.

**Quien sondea va solo.** El sondeo es poco trabajo, y lo pesado (mapear, llamar a los destinos) lo hacen el router
y los canales, que sí pueden crecer en copias. Por eso el `compose.yaml` le pone `replicas: 1` al sondeo y deja que
el resto crezca del otro lado de una llamada HTTP.

**Apartar es mejor que borrar.** Con `.camel/` y `.error/`, cualquiera puede ver qué se procesó y qué hay que
revisar, sin abrir un log.

## Ejercicio: el segundo momento sin rutas nuevas

Con tu versión terminada:

1. En el router, ¿cuántas rutas de canal hay para el embarque y cuántas para la creación? ¿Qué cambia entre una
   llamada y la otra? (Pista: busca `${variable.paso}`.)
2. Cambia el `delay` a 10000 y corre `./verifica`. ¿Cuánto tarda ahora en aparecer el EMBARCADO? ¿Qué ganas y qué
   pierdes con un sondeo más lento? Regresa a 2000.
3. Abre el `compose.yaml` de la lección. ¿Qué servicio lleva `replicas: 1`? ¿Qué pasaría con el embarque de la 4001
   si le pusieras 3? Piensa en la API, el ERP y el socio por separado: ¿quién lo notaría y quién no? (Pista: la
   llave de la API.)

---

## Por qué se hizo así

Lo que decidimos en esta lección, con lo que descartamos y qué nos haría cambiar de idea:

- [Una carpeta, un solo sondeo](../decisiones/16-un-solo-sondeo-por-carpeta.md)

## Repaso

1. ¿Qué es sondear, y por qué hace falta con la bodega y no con la tienda?
2. ¿Para qué sirve cada uno de los cuatro cuidados: `include`, `readLock`, `move` y `moveFailed`, y `delay`?
3. ¿Por qué el `.tmp` de la 4002 no se tomó? ¿Qué pasaría si la bodega escribiera directo con el nombre final?
4. ¿Qué hace un archivo roto sin `moveFailed`, y por qué eso es un problema aunque no se pierda?
5. **La pregunta de cierre:** ¿por qué el sondeo corre en una sola copia, mientras el router y los canales pueden
   correr en varias? ¿Qué pasaría si lo hicieran todas las copias?

**Siguiente:** en la lección 14, la bodega ya no manda un archivo por orden: manda uno con todo lo que salió en la
mañana. Un archivo, varias órdenes. ¿Cuántos 856 esperas?
