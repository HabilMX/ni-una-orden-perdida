# Lección 23 — Reprocesar lo que falló

> **Lo que vas a lograr:** una puerta en el router, `POST /rerun`, para que una persona vuelva a mandar **solo** el
> paso que se quedó atorado, sin duplicar nada en los destinos y dejando escrito quién lo pidió, por qué y con qué
> versión salió.

**Antes de empezar necesitas:** las lecciones 22 y 18 terminadas, y el ambiente de la 00 encendido.

## Lo nuevo de hoy, en una frase

En la 20 dejaste lo que no se supo en `conciliacion_pendiente`, y en la 22 lo que el cortacircuito no mandó en
`pendiente`. Las dos cosas esperan lo mismo: que alguien arregle la causa y lo vuelva a mandar. Hasta hoy, ese
alguien no tenía cómo. El **rerun** es esa herramienta: un intento más de la misma operación, pedido por una persona
y anotado como tal.

**La imagen:** el mostrador de reclamaciones de una paquetería. Llevas tu guía y dices «este paquete se quedó en la
bodega por la tormenta». No te reenvían todo lo que mandaste ese día: buscan **ese** paquete, revisan que de verdad
esté atorado y lo vuelven a despachar. En el sistema queda anotado que lo reenvió el mostrador, a petición tuya, por
la tormenta. Si llevas la guía de un paquete que ya se entregó, te dicen que no.

---

## 1. Prepara tu carpeta

```bash
cd lecciones/23-reprocesar-lo-que-fallo
cp -R inicio mio
```

El router tiene un archivo nuevo, `orden-router/rerun.camel.yaml`, con una sola ruta: `rerun`, que escucha en
`POST /rerun`. Recibe esto:

```json
{"orden": "4001", "momento": "creada", "canal": "edi",
 "pedido_por": "ana.soporte", "motivo": "el SFTP del socio estuvo caído"}
```

Ábrelo y lee el comentario de arriba. La versión de `inicio/` **funciona, pero no alcanza**. Marca la operación
como `pendiente` y la echa otra vez al reparto. Eso tiene tres problemas:

| Problema | Por qué importa |
|---|---|
| Sale como un intento `auto`, sin nombre ni motivo | Mañana nadie sabe que lo pidió una persona, ni por qué |
| Reabre lo que ya había salido bien, y lo manda otra vez | Un rerun de algo aceptado vuelve a llamar al destino |
| Contesta 202 «devuelta al reparto» | Quien lo pidió no sabe cómo le fue |

Y hay algo que **no** tienes que construir: la tabla `intento` ya tiene las columnas `tipo`, `pedido_por` y
`motivo`, y el `INSERT` de la ruta `intentar` (en `reparto.camel.yaml`) ya las llena con tres variables. Si no
existen, `tipo` queda en `auto`.

## 2. Predice

Contesta en un papel:

1. Vuelve el SFTP del socio. Si reprocesas **la orden completa** (los tres canales) de la 4001, ¿qué le pasa a la API
   y al ERP, que ya la tenían?
2. La API tardó y el canal cortó: la 4005 se quedó sin saber cómo le fue. Haces el rerun. ¿Se crea otro pedido en la API?
   (Pista: la 02 y la 18.)
3. Alguien pide un rerun de una operación que ya está `aceptada`. ¿Qué debería contestar el router?

Mira cómo está hoy:

```bash
./verifica inicio
```

Los reprocesos salen, y por eso varias marcas pasan. Pero salen como intentos `auto`, el rerun de lo que ya estaba
aceptado vuelve a llamar al destino en vez de negarse, y uno sin motivo se acepta igual.

## 3. Un intento manual, con nombre y motivo

Abre `mio/orden-router/rerun.camel.yaml`. Después de las cinco variables que se sacan del cuerpo hay cuatro TODO, y
los cuatro reemplazan lo que está abajo: el `UPDATE` a `pendiente`, el envío a `seda:repartir` y la respuesta 202.

### TODO 1: sin nombre o sin motivo, no

Primero dos encabezados, para que la respuesta salga limpia: `removeHeaders` con `pattern: "*"` y un `setHeader` de
`Content-Type` en `application/json`. Luego un `filter` que deja pasar **solo lo que está mal**: si falta `orden`,
`momento`, `canal`, `pedido_por` o `motivo`, contesta 400 y se detiene.

```yaml
- filter:
    expression:
      simple:
        expression: >-
          ${variable.orden} == null || ${variable.orden} == '' || …
          ${variable.pedido_por} == null || ${variable.pedido_por} == '' ||
          ${variable.motivo} == null || ${variable.motivo} == ''
    steps:
      - setHeader:
          name: CamelHttpResponseCode
          expression:
            constant:
              expression: "400"
              resultType: java.lang.Integer
      - setBody:
          expression:
            constant:
              expression: '{"resultado":"no reprocesado","motivo":"faltan datos: orden, momento, canal, pedido_por y motivo"}'
      - stop: {}
```

El `stop` termina la ruta ahí: lo que viene abajo no corre.

### TODO 2: solo lo que falló

Lee la operación **de la base**, no del cuerpo. Copia la consulta de la ruta `repartir` de `reparto.camel.yaml` y
cámbiale dos cosas: que filtre también por `p.canal = :#${variable.canal}` en vez de por `p.estado = 'pendiente'`,
y que regrese también `p.estado`. Va con `outputType: SelectOne`, así que el cuerpo queda como **una** fila (o
`null`).

Después, un `choice` con dos ramas que contestan y se detienen, como el `filter` del TODO 1:

| Si | Contesta | Por qué |
|---|---|---|
| `${body} == null` | 404 | No hay esa operación |
| `${body[estado]} in 'aceptada,confirmada,estacionada'` | 409 | Lo aceptado y lo confirmado salió bien; lo estacionado lo suelta el barrido (la 17) |

Todo lo demás (`pendiente`, `no_se_sabe`, `conciliacion_pendiente`, `rechazada`) sigue al TODO 3. Antes de seguir,
guarda el estado en una variable `antes`: lo vas a contestar al final.

### TODO 3: un intento, aquí mismo

Esta es la dificultad de hoy. El reproceso **no** se echa a la cola: se hace un intento en ese momento, con las mismas
rutas que usa el reparto.

1. Un `setVariable` `tipo` con `constant` `manual`. Con eso, el `INSERT` de `intentar` anota quién y por qué (las
   variables `pedido_por` y `motivo` ya existen).
2. Un `to` a `direct:preparar`, con la fila en el cuerpo. Arma el sobre, la huella y el número de control como lo
   hace el reparto.
3. Un `doTry` con un `to` a `direct:intentar`. En su `doCatch` de `java.io.IOException` (el «no se supo» de la 20),
   un `UPDATE operacion SET estado = 'conciliacion_pendiente' WHERE id = :#${variable.operacion}`: un intento manual
   no se reintenta solo, regresa a la vista de quien lo pidió.
4. Un `filter`: si `${variable.desenlace} == 'aceptada'`, un `to` a `direct:barrido`, como hace el reparto. Algo
   estacionado podía estar esperando este paso (la 17).

### TODO 4: dile cómo le fue

Contesta 200 con lo que quedó en las variables:

```yaml
- setBody:
    expression:
      simple:
        expression: '{"resultado":"reprocesado","operacion":${variable.operacion},"intento":${variable.intento},"antes":"${variable.antes}","desenlace":"${variable.desenlace}","codigo":${variable.codigo},"version":"${variable.version}"}'
```

Fíjate en el último campo. El intento sale con **lo que hoy está desplegado**: el mapeo y la plantilla vigentes, no
los del día en que falló. Si entre la caída y el rerun alguien corrigió el mapeo, el rerun sale corregido, y la
versión con la que salió queda anotada en el intento (`version_mapeo`) y en la respuesta.

## 4. Rompe uno a propósito

Con todo terminado, quita la segunda rama del `choice` del TODO 2, la del 409. Corre `./verifica`.

Antes, predice: la verifica pide un rerun de la 4001, que ya está `aceptada` en el socio. ¿Qué pasa en el destino?

El router hace el intento manual: vuelve a llamar al canal del socio, y la operación tiene un intento más. Sale en
rojo la marca del rerun de lo aceptado. El rastro ya tiene un reproceso que nadie necesitaba, y en un destino que no
reconociera repetidos habría un duplicado. **El router no le pregunta al
destino si ya lo tiene: se lo pregunta a su propia base.** Regresa la rama.

## 5. Comprueba

```bash
./verifica
```

`verifica` apaga el SFTP y manda cuatro órdenes; pone la API en modo lento y manda la 4005; luego regresa todo y
hace cinco reruns, uno por cada paso atorado. Al final pide uno de algo aceptado y uno sin motivo. Está terminada
cuando ves las once marcas verdes:

```
✅ Arrancaron el router (con su POST /rerun), los tres canales y el sondeo
✅ Con el SFTP caído, el canal del socio de la 4001 a la 4004 quedó pendiente (lo que se reprocesa)
✅ Con la API lenta, el canal rest de la 4005 no supo cómo le fue y quedó (…)
✅ Rerun: el canal del socio de la 4001 a la 4004 y el rest de la 4005 quedaron aceptados
✅ Cada rerun quedó como intento «manual», con quién lo pidió, por qué y la versión del mapeo con que salió
✅ Sin duplicados: un 850 por orden en el socio, y la API creó la 4005 una vez y al rerun contestó 200 «reenvío»
✅ Solo el paso que falló: la API y el ERP de la 4001 a la 4004 no recibieron intentos nuevos
✅ Rerun de lo que ya está aceptado: 409, sin intento nuevo y la operación sigue (…)
✅ Rerun sin motivo: 400 (sin quién y por qué, el rastro quedaría mudo)
✅ El ERP reconoce un pedido que ya tiene: 200 «ya registrado», sin renglones nuevos
✅ El 997 del socio confirmó los cuatro 850 reprocesados
```

Mira la sexta. La API **ya tenía** la 4005: la llamada lenta la registró después de que el canal cortó, como en la 20.
El rerun la mandó otra vez con la misma llave, y la API contestó 200 «reenvío», no 201. Eso es lo que hace seguro el
rerun de un «no se sabe».

Haz tú un rerun a mano y mira la respuesta:

```bash
curl -s -XPOST localhost:8080/rerun -H 'Content-Type: application/json' \
  -d '{"orden":"4001","momento":"creada","canal":"edi","pedido_por":"yo","motivo":"probar"}'
```

**¿No te corre?** `./verifica solucion` prueba la nuestra, y
`diff mio/orden-router/rerun.camel.yaml solucion/orden-router/rerun.camel.yaml` te dice en qué son distintas.

| Lo que ves | Qué haces |
|---|---|
| `Esperaba 5 intentos manuales` y hay 0 | Falta el `setVariable` `tipo` en `manual`, o pusiste el intento en la cola en vez de llamar a `direct:intentar` |
| `Se reprocesó de más: la API y el ERP … pasaron de … a … intentos` | Tu consulta no filtra por canal: está tomando las tres operaciones del momento |
| `Rerun de la 4001 ya aceptada: contestó …` | Falta la rama del 409, o no lleva `stop` |
| `Rerun sin motivo: esperaba 400` | Al `filter` del TODO 1 le falta `motivo`, o le falta el `stop` |
| `de las 5 operaciones reprocesadas, … quedaron aceptadas` | Falta `direct:preparar` antes de `direct:intentar`: sin él no hay sobre ni número de control |
| `No pude poner la API en modo lento` | Tu API es de antes: recréala como en la 20 |
| `El puerto 8080 está ocupado` | Quedaron arriba los servicios de otra corrida: `DONDE=mio docker compose -f compose.yaml down` |

---

## Lo que esto enseña fuera de la práctica

**Un reproceso es un intento más, no una operación nueva.** Si fuera nueva, cada destino la vería como otra orden.
Como es la misma operación, sale con la misma llave y el mismo número de control, y cada destino puede reconocerla.

**Se reprocesa el paso, no la orden.** Volver a mandar todo «por si acaso» llena el rastro de ruido y apuesta a que
todos los destinos aguantan un repetido. Se manda lo que falló y se le niega a lo que salió bien.

**Quién y por qué no son burocracia.** Dentro de un mes alguien va a preguntar por qué la 4005 tiene dos intentos.
Sin `pedido_por` ni `motivo`, la respuesta es «no sé»; con ellos, «lo pidió soporte porque la API estuvo lenta».

**Sale con lo de hoy, y se anota.** Un rerun no revive el pasado: usa el mapeo vigente. Por eso cada intento guarda su
versión, y por eso se puede saber qué recibió el destino cada vez.

## Ejercicio: el mostrador de reclamaciones

Con tu versión terminada, corre `./verifica` y luego:

1. Consulta los intentos del canal del socio de la 4001:
   `SELECT n, tipo, pedido_por, motivo, desenlace, version_mapeo FROM intento i JOIN operacion p ON p.id = i.operacion
   WHERE p.orden = '4001' AND p.canal = 'edi' ORDER BY n;`
   Cuenta la historia de esa operación en tres frases, con lo que dice cada renglón.
2. Pide tres reruns a mano: uno de una orden que no existe (`9999`), uno de la 4001 en el canal `rest` y uno sin
   `pedido_por`. Anota qué contesta cada uno y por qué.
3. Imagina que mañana corriges el mapeo del socio a la versión 2 y haces un rerun de un 850 que había fallado ayer.
   ¿Con qué versión sale? ¿Dónde lo puedes comprobar? ¿Por qué no sería mejor que saliera con la versión de ayer?

---

## Por qué se hizo así

Lo que decidimos en esta lección, con lo que descartamos y qué nos haría cambiar de idea:

- [El reproceso a mano es un intento más, con nombre y motivo](../decisiones/27-el-rerun-es-un-intento-manual.md)

## Repaso

1. ¿Qué estados de una operación se pueden reprocesar, y cuáles no? ¿Por qué lo estacionado tampoco?
2. ¿Por qué el rerun lee la operación de la base y no confía en lo que dice el cuerpo?
3. ¿Qué evita que un rerun de la 4005 cree otro pedido en la API?
4. ¿Por qué sin `pedido_por` o sin `motivo` se contesta 400?
5. **La pregunta de cierre:** el rerun sale con la configuración vigente y deja anotada su versión. ¿Por qué con la
   de hoy, y para qué sirve anotarla?

**Siguiente:** en la lección 24 el problema no es que el destino esté caído, sino que lo tumbes tú. Con tres copias
de int-bd, ¿cuántas llamadas al mismo tiempo recibe el ERP?
