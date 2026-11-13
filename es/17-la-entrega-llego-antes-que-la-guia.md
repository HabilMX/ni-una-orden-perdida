# Lección 17 — La entrega llegó antes que la guía

> **Lo que vas a lograr:** que una entrega que llega **antes** que su embarque no se pierda ni se mande a destiempo:
> se queda estacionada en la base y sale sola, una sola vez, en cuanto llega el embarque.

**Antes de empezar necesitas:** las lecciones 15 y 16 terminadas y el ambiente de la 00 encendido.

## Lo nuevo de hoy, en una frase

En la 16 todo llegó en orden: la orden se creó, luego se embarcó y al final se entregó. Pero la bodega y la
paquetería no se hablan entre sí, y a veces el aviso de entrega **llega antes** que el archivo de embarque. Si el
router lo reparte así, la API y el ERP lo rechazan: no se puede entregar algo que, para ellos, nunca salió. Y si lo
tira, el cliente nunca aparece como atendido. Hoy lo **estacionas** y lo **sueltas** cuando ya puede salir.

**La imagen:** el estacionamiento de un hospital. Llega una ambulancia de traslado, pero la cama del paciente
todavía no está lista. No la mandan de regreso, ni meten al paciente a un cuarto ocupado: se estaciona. Y en cuanto
alguien avisa «la cama ya está», la ambulancia pasa. Nadie sale cada cinco minutos a revisar el estacionamiento: lo
que mueve a la ambulancia es el aviso.

---

## 1. Prepara tu carpeta

```bash
cd lecciones/17-la-entrega-llego-antes-que-la-guia
cp -R inicio mio
```

Hoy el router trae piezas nuevas. Casi todas ya están hechas: tu trabajo es la última.

| Pieza | Dónde | ¿La escribes? |
|---|---|---|
| La máquina de estados: qué momento necesita a cuál, en cada canal | `mio/orden-router/reglas/maquina-de-estados.yaml` | No, léela |
| La decisión: con la máquina y lo guardado, dice `procesa`, `estaciona` o `invalido` | `direct:decidir`, con su mapeo en `mappings/estado/decision/v1/` y sus casos de oro | No, léela |
| El estacionamiento: al guardar un momento, cada operación nace `pendiente` o `estacionada` | `guardar-momento`, en `estado.camel.yaml` | No, ya viene |
| **El barrido**: suelta lo estacionado que ya puede salir | `barrido.camel.yaml` | **Sí** |

Abre la máquina de estados. Se lee así: en el canal `rest`, el momento `entregada` necesita que `embarcada` ya esté
**aceptada en ese mismo canal**. Va por canal porque cada destino lleva su propio orden: que la API ya tenga el
embarque no dice nada del ERP.

Abre también un caso de oro de la decisión, `cases/entrega-sin-embarque/`: su `entrada.json` y su `esperado.json`.
Es la misma idea de la 03: la regla se prueba con ejemplos, no con fe.

## 2. Predice

Contesta en un papel:

1. Llega la entrega de la 4002, y su embarque todavía no. ¿Qué tiene que quedar en la base? ¿Qué tiene que recibir
   cada destino?
2. Luego llega el embarque. ¿Quién se da cuenta de que la entrega ya puede salir? ¿Cuándo?
3. Si el barrido corre dos veces, ¿podría salir la entrega dos veces? ¿Qué lo impediría?

Mira cómo está hoy:

```bash
./verifica inicio
```

La entrega se estaciona bien, pero el barrido no suelta nada: aunque llega el embarque, la entrega se queda
estacionada para siempre. La 4002 nunca aparece como ENTREGADO.

## 3. Escribe el barrido

Abre `mio/orden-router/barrido.camel.yaml`. Arriba está `barrido-http`, la puerta para pedir un barrido a mano con
`POST /barrido`; ya está hecha. Tu trabajo es la ruta `barrido`, con cuatro TODO:

| TODO | Qué escribes | Para qué |
|---|---|---|
| 1 | Un `to` con `sql:SELECT id, orden, momento, canal FROM operacion WHERE estado = 'estacionada' ORDER BY id` | Traer lo estacionado, en el orden en que llegó |
| 2 | Un `split` sobre `${body}`; adentro, cuatro variables (`operacion`, `orden`, `momento`, `canal`, de `${body[id]}`, `${body[orden]}`…) y una llamada a `direct:decidir` | Volver a decidir cada una con lo que hoy hay en la base |
| 3 | Si `${variable.decision} == 'procesa'`: `UPDATE operacion SET estado = 'pendiente' WHERE id = … AND estado = 'estacionada'` | Pasarla a pendiente, **solo si sigue estacionada** |
| 4 | Si `${header.CamelSqlUpdateCount} == 1`: a `seda:repartir` con `pattern: InOnly` | Encolar el reparto, **solo si fuiste tú quien la cambió** |

Para el «si…» usa un `filter`: deja pasar el mensaje solo cuando la condición se cumple. El TODO 4 va dentro del
`filter` del TODO 3.

El `AND estado = 'estacionada'` del TODO 3 es el corazón de la lección. Imagina dos barridos al mismo tiempo: los dos
leen la entrega estacionada y los dos deciden «procesa». El primero la cambia: la base contesta «cambié 1 fila». El
segundo llega tarde, y como ya no está estacionada, la base contesta «cambié 0». Solo el que cambió 1 la encola. **La
base decide quién gana**, no la suerte.

¿Y quién llama al barrido? Abre `reparto.camel.yaml` y busca `direct:barrido`, casi al final: se llama **cada vez
que un canal acepta un momento**, porque eso es lo único que puede destrabar a otro. No hay un reloj que lo dispare
cada tanto.

## 4. Rompe uno a propósito

Con tu barrido terminado, comenta en `reparto.camel.yaml` el paso que llama a `direct:barrido`. Levanta los
servicios, crea la 4002, manda su entrega y luego deja el lote de la bodega, que trae su embarque:

```bash
DONDE=mio docker compose -f compose.yaml up -d
curl -s -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' -d @../../datos/ordenes/tramo-2/orden-4002.json
curl -s -XPOST localhost:8080/webhooks/paqueteria -H 'Content-Type: application/json' -d @../../datos/webhooks/entrega-4002.json
cp ../../datos/embarques/lote-bodega-0001.json buzon/
```

El lote trae embarques de otras órdenes que hoy no creaste; el router los rechaza con 404, y está bien. Espera unos
segundos y pregúntale a la base:

```bash
docker compose -f ../../infra/compose.yaml exec estado psql -U router -d estado \
  -c "SELECT momento, canal, estado FROM operacion WHERE orden = '4002' ORDER BY id"
```

Antes, predice: ¿en qué estado va la entrega?

El embarque ya está `aceptada` en los tres canales, pero la entrega sigue `estacionada`. Nadie avisó que algo
cambió. Ahora pide el barrido a mano y vuelve a preguntar:

```bash
curl -s -XPOST localhost:8080/barrido
```

Ahora sí sale. Eso es lo que hace el evento: avisar en el momento justo. Sin él, alguien tendría que pedirlo, o un
reloj tendría que revisar la base cada tanto aunque no haya pasado nada. Regresa la llamada y apaga:

```bash
DONDE=mio docker compose -f compose.yaml down
```

## 5. Comprueba

```bash
./verifica
```

`verifica` crea la 4002, manda su entrega **antes** que su embarque, revisa que se estacionó, deja el embarque en
el buzón y, al final, pide un barrido a mano para comprobar que nada sale dos veces. Está terminada cuando ves las
diez marcas verdes:

```
✅ Arrancaron el router (con su máquina de estados y el barrido), los tres canales y el sondeo
✅ La 4002 está creada: CREADO en la API y REGISTRADO en el ERP
✅ La entrega de la 4002 llegó antes que su embarque y el router contestó 202 «recibida»
✅ Estado: las tres operaciones de la entrega quedaron estacionadas, sin ningún intento
✅ Destinos: a nadie le llegó la entrega a destiempo (la API sigue en CREADO, el ERP sin entrega, sin 214)
✅ Estado: llegó el embarque, el barrido soltó la entrega y los seis quedaron aceptados; la orden va en «entregada»
✅ API: la 4002 está ENTREGADO, con su guía
✅ ERP: la 4002 está ENTREGADO y su fila de entrega tiene la guía
✅ Socio: el 214 de la 4002 está en su SFTP y el lector X12 no le encontró fallas
✅ Una sola vez: un intento por canal para la entrega y un solo 201 en la API, aunque el barrido corrió otra vez a mano
```

La cuarta y la última son las que más importan. Lo estacionado **no tiene intentos**: no salió a ningún lado. Y el
barrido extra no repitió nada.

**¿No te corre?** `./verifica solucion` prueba la nuestra, y
`diff mio/orden-router/barrido.camel.yaml solucion/orden-router/barrido.camel.yaml` te dice en qué son distintas.

| Lo que ves | Qué haces |
|---|---|
| `después del embarque, embarque y entrega quedaron` … con la entrega `estacionada` | Falta el TODO 3 o el 4, o el `filter` compara `decision` con otra palabra: es `'procesa'` |
| `${body[id]}` sale vacío | El `SELECT` no trae la columna `id`, o las variables quedaron fuera del `split` |
| `Esperaba el barrido a mano en 200, 3 intentos de la entrega y un 201` y hay más | El `UPDATE` no lleva `AND estado = 'estacionada'`, o no filtraste por `CamelSqlUpdateCount` |
| Nada se encola aunque el UPDATE cambió la fila | El `to` a `seda:repartir` quedó fuera del segundo `filter`, o falta `pattern: InOnly` |
| `El puerto 8080 está ocupado` | Quedaron arriba los servicios del paso 4: `DONDE=mio docker compose -f compose.yaml down` |

---

## Lo que esto enseña fuera de la práctica

**El orden en que llegan las cosas no es el orden en que pasaron.** Entre sistemas que no se coordinan, un aviso
puede adelantarse a otro. El patrón que lo resuelve se llama *Resequencer*: guardar lo que llegó fuera de orden y
soltarlo en el orden correcto.

**La regla de quién va antes vive en un solo lugar.** Está en un YAML que se lee como datos. Agregar un momento
nuevo, una devolución por ejemplo, es agregar una línea, no otra ruta.

**Cuando dos procesos compiten por lo mismo, que decida la base.** El `UPDATE … WHERE … AND estado = …` es una forma
de candado: solo uno puede pasar la fila de un estado a otro. Vas a encontrar esta idea en casi cualquier sistema
que reparte trabajo.

## Ejercicio: la regla como datos

Con tu versión terminada:

1. En la máquina de estados, ¿qué tendrías que agregar para un momento nuevo, `devuelta`, que necesite la entrega?
   Escríbelo. ¿Tendrías que tocar alguna ruta?
2. Abre los cinco casos de oro de la decisión. Para `entrega-con-embarque-pendiente`, ¿por qué la decisión es
   estacionar, si el embarque **ya llegó**?
3. En la base, después del `verifica`, cuenta los intentos de la entrega de la 4002:
   `SELECT o.canal, COUNT(i.id) FROM operacion o LEFT JOIN intento i ON i.operacion = o.id WHERE o.orden = '4002' AND o.momento = 'entregada' GROUP BY o.canal`.
   ¿Cuántos esperas por canal, y por qué no son dos aunque el barrido corrió dos veces?

---

## Lo que ya funciona

Con esta lección cierras la entrega 2. Además de lo de la entrega 1, una orden ya hace esto:

- se embarca desde un archivo de la bodega, con o sin lote, sin tomarlo a medias ni dos veces;
- se guarda antes de contestar, con una fila por canal y un registro de cada intento;
- se entrega desde el aviso de la paquetería, que recibe un 202 en cuanto quedó guardado;
- y si la entrega llega antes que el embarque, espera estacionada y sale una sola vez, en cuanto puede.

Lo que todavía no hace: reconocer un mensaje que llega repetido con otro contenido, ni reintentar lo que quedó en
`no_se_sabe`. Eso es la entrega 3.

## Por qué se hizo así

Lo que decidimos en esta lección, con lo que descartamos y qué nos haría cambiar de idea:

- [Lo que llega antes de tiempo se estaciona, no se rechaza](../decisiones/21-estacionar-lo-que-llega-antes.md)

## Repaso

1. ¿Qué quiere decir que una operación esté `estacionada`, y en qué se distingue de `pendiente`?
2. ¿Qué dice la máquina de estados sobre la entrega, y por qué va por canal?
3. ¿Para qué sirve el `AND estado = 'estacionada'` en el UPDATE del barrido?
4. ¿Por qué el barrido revisa `CamelSqlUpdateCount` antes de encolar?
5. **La pregunta de cierre:** el barrido lo dispara un evento, que un canal aceptó un momento, y no un temporizador
   que revisa la base cada minuto. ¿Qué ganas con el evento? ¿Qué perderías con el temporizador, y en qué caso sí te
   haría falta uno?

**Siguiente:** en la lección 18 empieza la entrega 3, «que no falle». Primero, una pregunta que parece fácil:
¿cuándo dos mensajes son el mismo?
