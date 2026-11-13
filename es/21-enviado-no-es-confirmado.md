# Lección 21 — Enviado no es confirmado

> **Lo que vas a lograr:** que el canal del socio no se dé por terminado cuando el 850 queda en su SFTP, sino cuando
> el socio contesta con su acuse, el **997**. Ese acuse llega después, en otro archivo y sin el número de orden: tu
> trabajo es saber a qué envío contesta.

**Antes de empezar necesitas:** las lecciones 20, 09 (el hilo de correlación) y 06 (leer X12) terminadas, y el
ambiente de la 00 encendido. Esta lección usa un servicio nuevo del ambiente, `socio-bot`, que hace de sistema del
socio y contesta los 997. Si no lo tienes arriba:

```bash
docker compose -f infra/compose.yaml up -d --wait
```

## Lo nuevo de hoy, en una frase

Con la API y el ERP, la respuesta llega en la misma llamada: preguntas, te contestan, y sabes. Con el socio no. El
router deja el 850 en una carpeta del SFTP y ahí termina la llamada. **Que el archivo esté ahí dice que lo
depositaste, no que el socio lo leyó ni que lo entendió.** El socio contesta cuando su sistema procesa el archivo:
segundos después en esta práctica, horas en la vida real. Y contesta por otro camino, dejando un 997 en su carpeta
`salida/`.

**La imagen:** dejas un sobre en el buzón de una empresa. El buzón no te dice si alguien lo abrió. Días después te
llega por correo una carta que dice «Recibimos su documento con folio 100000002 y está completo». La carta no
menciona tu nombre ni de qué se trataba: menciona **el folio que tú le pusiste al sobre**. Si no anotaste qué
mandaste con ese folio, no sabes qué te están confirmando.

---

## 1. Prepara tu carpeta

```bash
cd lecciones/21-enviado-no-es-confirmado
cp -R inicio mio
```

El router tiene un archivo nuevo, `orden-router/acuses.camel.yaml`. Arriba tiene la ruta `sondeo-acuses`, ya hecha:
lee cada `997-<algo>.edi` de la carpeta `salida/` del socio, lo traduce con su mapeo JSONata y se lo pasa a
`correlacionar-acuse`, que es tu parte.

Antes de escribir, **lee un 997**. Abre
`inicio/orden-router/mappings/edi/997/v1/cases/aceptacion/entrada.json`. Es un solo renglón con `\n` entre
segmentos; separado se ve así:

```
ISA*00*          *00*          *ZZ*SOCIO01        *ZZ*TIENDANORTE    *261113*0100*U*00401*700000001*0*P*>~
GS*FA*SOCIO01*TIENDANORTE*20261113*0100*700000001*X*004010~
ST*997*0001~
AK1*PO*100000002~
AK2*850*0001~
AK5*A~
AK9*A*1*1*1~
SE*6*0001~
GE*1*700000001~
IEA*1*700000001~
```

| Segmento | Qué dice | ¿De quién es el número? |
|---|---|---|
| `ISA … *700000001*` | El sobre del 997, con su número de control | **Del socio.** Lo puso él al mandar su acuse |
| `AK1*PO*100000002` | «Acuso el grupo de órdenes de compra (`PO`) número 100000002» | **Tuyo.** Es el GS06 que mandaste en el 850 |
| `AK2*850*0001` | «Dentro de ese grupo, el documento 850 número 0001» | **Tuyo.** Es el ST02 del 850 |
| `AK5*A` | El documento se acepta (`A`) o se rechaza (`R`) | — |

Ahora abre el de rechazo, `cases/rechazo/entrada.json`. Trae dos segmentos más antes del `AK5*R`: `AK3*PO1*6**8`
dice que el problema está en el segmento `PO1`, el sexto del documento; `AK4*7*234*7*SKU-9999` dice que el elemento 7
de ese segmento trae `SKU-9999`, un producto que el socio no conoce.

Y abre los dos `esperado.json`: es lo que el mapeo saca de cada 997 y lo que tu ruta recibe en el cuerpo.

```json
{"socio": {"control": "700000001"}, "grupo": {"tipo": "PO", "control": "100000002"},
 "documento": {"tipo": "850", "control": "0001"}, "acuse": "A", "errores": []}
```

## 2. Predice

Contesta en un papel:

1. Depositaste el 850 de la 4001. ¿Ya lo recibió el socio? ¿Cómo lo sabrías?
2. El 997 no trae el número de orden. ¿Con qué número lo juntarías con la operación de la 4001? Busca en la tabla
   `operacion` de `infra/postgres/01-estado.sql` qué columna guarda ese número, y desde qué lección.
3. Llega un 997 que cita un grupo que nunca mandaste. ¿Qué debería pasar con él?

Mira cómo está hoy:

```bash
./verifica inicio
```

La primera marca pasa: el 850 queda depositado y sin acuse. Después el socio contesta, el router lee el 997… y no
hace nada con él. El canal del socio se queda en `aceptada` para siempre, aunque el socio ya acusó, y del rechazo de la
4006 no se entera nadie.

## 3. Correlaciona el acuse

Abre `mio/orden-router/acuses.camel.yaml` y busca la ruta `correlacionar-acuse`. Hoy solo escribe en el log. Tiene
cuatro TODO, y todos menos el último caben en **una sola instrucción SQL**:

| TODO | Qué hace | Para qué |
|---|---|---|
| 1 | Anota el acuse en la tabla `acuse`, **corresponda o no** a algo | Que ningún acuse se pierda |
| 2 | Busca la operación del socio que esperaba este acuse | La correlación: la única dificultad de hoy |
| 3 | La pasa a `confirmada` (AK5 = A) o `rechazada` (AK5 = R), y guarda en la fila del acuse a qué operación correspondió | Que el canal termine solo con el acuse |
| 4 | Si no correspondió a ninguna, lo avisa con un `WARN` que diga `<archivo> no corresponde` | Que alguien vea el acuse sin dueño |

La forma de la instrucción ya te la damos. Primero un `UPDATE … RETURNING` dentro de un `WITH`, que cambia la
operación y regresa cuál cambió; después el `INSERT` del acuse, que usa lo que regresó:

```yaml
- to:
    uri: >-
      sql:WITH op AS (
        UPDATE operacion SET estado = CASE WHEN CAST(:#${body[acuse]} AS text) = 'R' THEN 'rechazada' ELSE 'confirmada' END
        WHERE canal = 'edi' AND estado = 'aceptada'
          AND …                               -- TODO 2: tus condiciones
        RETURNING id, orden, momento, estado)
      INSERT INTO acuse (archivo, control_socio, grupo_tipo, grupo_control, documento_tipo, documento_control,
                         codigo, errores, operacion)
      SELECT :#${variable.archivo}, :#${body[socio][control]}, :#${body[grupo][tipo]}, :#${body[grupo][control]},
             :#${body[documento][tipo]}, :#${body[documento][control]}, :#${body[acuse]},
             CAST(:#${variable.leido} AS jsonb) -> 'errores', (SELECT id FROM op)
      RETURNING COALESCE((SELECT orden || ' ' || momento || ' → ' || estado FROM op), '') AS resultado
    variableReceive: correlacion997
    parameters:
      dataSource: "#estado"
      outputType: SelectOne
```

Si el `UPDATE` no encuentra nada, `op` queda vacío: el acuse se anota igual, con `operacion` en `NULL`, y `resultado`
sale como texto vacío. Eso es lo que usa el TODO 4.

**Tu trabajo son las condiciones del TODO 2.** La operación que espera este acuse es la que cumple las cuatro:

| Condición | En SQL | Por qué |
|---|---|---|
| Su número de control es el del grupo que cita el AK1 | `numero_control = CAST(:#${body[grupo][control]} AS text)` | Es el número que **tú** pusiste en el GS06 desde la 15 |
| Es del momento que corresponde al documento | `momento = CASE CAST(:#${body[documento][tipo]} AS text) WHEN '850' THEN 'creada' WHEN '856' THEN 'embarcada' WHEN '214' THEN 'entregada' END` | Un 850 acusa la creación; un 856, el embarque |
| El tipo del grupo cuadra con el documento | `PO` va con `850`, `SH` con `856` y `QM` con `214` | Un acuse mal armado no debe confirmar nada |
| Es el primer documento del grupo | `CAST(:#${body[documento][control]} AS text) = '0001'` | Cada grupo nuestro lleva un solo documento, con ST02 `0001` |

Escribe la tercera tú, con un `CASE` como el de la segunda.

Para el TODO 4, un `choice`: si `${variable.correlacion997} == ''`, un `log` en `WARN` con el mensaje
`El 997 ${variable.archivo} no corresponde a ninguna operación que esperara su acuse`; si no, un `log` normal que
diga qué orden confirmó.

Fíjate en lo que **no** usas: el `ISA13` del 997, `${body[socio][control]}`. Se guarda en `control_socio`, pero no
sirve para correlacionar: es el número que el socio le puso **a su sobre**, no el tuyo.

## 4. Rompe uno a propósito

Con todo terminado, cambia la primera condición para que compare con el número del socio:
`numero_control = CAST(:#${body[socio][control]} AS text)`. Corre `./verifica`.

Antes, predice: ¿qué pasa con la 4001 cuando llegue su 997?

Se queda en `aceptada`. El 997 se anota en la tabla `acuse` sin operación, y en el log aparece tu `WARN` de «no
corresponde». Sale en rojo la marca de la confirmación. El ISA del socio (`7000000…`) nunca va a coincidir con tu
número (`1000000…`): son dos numeraciones de dos sistemas distintos. Si no lo hubieras anotado como acuse sin dueño,
se habría perdido en silencio. Regresa el `grupo`.

## 5. Comprueba

```bash
./verifica
```

`verifica` manda la 4001 y espera su acuse; luego la 4006, que pide un producto que el socio no conoce; y al final
deja en el SFTP un 997 que cita un grupo que nunca mandamos. Está terminada cuando ves las siete marcas verdes:

```
✅ Arrancaron el router (con el sondeo de los acuses), los tres canales y el sondeo de la bodega
✅ El 850 de la 4001 quedó depositado («aceptada») y todavía no hay acuse: enviado no es confirmado
✅ Llegó el 997 de aceptación: el canal pasó a «confirmada», correlacionado por el GS06 (…) y el ST02, no por el ISA del socio
✅ El 997 que leyó el router pasa el lector X12, y su AK1 cita el grupo (…)
✅ La API y el ERP no esperan un acuse: se quedan en «aceptada» (bd=aceptada rest=aceptada)
✅ La 4006: el 997 de rechazo dejó el canal en «rechazada», con el error que señaló el socio (PO1, SKU-9999)
✅ Un 997 que cita un grupo que no mandamos quedó anotado sin operación, con su WARN, y no confirmó nada más
```

Abre el 997 que confirmó la 4001: la verifica lo copia a `mio/salida/997-<número>.edi`. Busca tu número en el `AK1` y
el del socio en el `ISA`.

**¿No te corre?** `./verifica solucion` prueba la nuestra, y
`diff mio/orden-router/acuses.camel.yaml solucion/orden-router/acuses.camel.yaml` te dice en qué son distintas.

| Lo que ves | Qué haces |
|---|---|
| `Esperaba la 4001 confirmada` y el acuse es «nada» | Falta el `INSERT INTO acuse`, o la instrucción no lleva `dataSource: "#estado"` |
| `Esperaba la 4001 confirmada` y sí hay acuse | El `UPDATE` no encontró la operación: revisa que compares con `body[grupo][control]` y que el `CASE` del momento esté completo |
| `La 4006: esperaba rechazada` y sigue `aceptada` | El `SET estado` no lee `body[acuse]`, o tu condición del tipo de grupo no deja pasar `PO` con `850` |
| `El 997 sin dueño` y hay 0 acuses sin operación | El acuse sin dueño no se anotó: el `INSERT` tiene que correr aunque el `UPDATE` no cambie nada |
| `El 997 sin dueño` y falta el WARN | El `choice` del TODO 4 no compara con `''`, o el `log` no lleva `loggingLevel: WARN` |
| `No está arriba socio-bot` | Levanta el ambiente como dice «Antes de empezar» |
| `El puerto 8080 está ocupado` | Quedaron arriba los servicios de otra corrida: `DONDE=mio docker compose -f compose.yaml down` |

---

## Lo que esto enseña fuera de la práctica

**Depositar no es entregar, y entregar no es que lo entiendan.** Un archivo en un SFTP puede quedarse ahí sin que
nadie lo procese. En EDI, el 997 es la manera estándar de que el otro diga «lo recibí y lo pude leer», o «lo recibí y
está mal en tal segmento».

**Una respuesta asíncrona se correlaciona por lo que tú mandaste.** El 997 no sabe de órdenes: sabe del número de
control que traía tu archivo. Por eso, desde la 15, el router asigna un número único por documento y lo guarda. Sin
ese número guardado, el acuse no tendría a quién apuntar.

**Cada sistema numera lo suyo.** El ISA del 997 es del socio; el GS06 que cita es tuyo. Confundirlos es el error más
común al correlacionar, y no truena: simplemente no encuentra nada.

**Lo que no corresponde se anota y se avisa.** Un acuse sin dueño puede ser un error del socio, un envío viejo o una
prueba. Tirarlo lo esconde; anotarlo deja que alguien lo investigue.

## Ejercicio: dos acuses, dos números

1. En papel, con los dos `entrada.json` de los casos dorados, contesta para cada 997: ¿qué número de control cita el
   AK1? ¿Cuál es el del ISA? ¿Acepta o rechaza? Si rechaza, ¿qué segmento y qué valor señala?
2. Después de correr `./verifica`, consulta la tabla de acuses:
   `SELECT archivo, control_socio, grupo_control, codigo, operacion FROM acuse ORDER BY id;`
   ¿Cuál de las tres filas no tiene operación, y por qué?
3. De los tres canales de la práctica, ¿cuál termina en `aceptada` y cuál en `confirmada`? Escribe una frase para
   cada uno que explique por qué ese es su final.

---

## Por qué se hizo así

Lo que decidimos en esta lección, con lo que descartamos y qué nos haría cambiar de idea:

- [El 997 se correlaciona por el grupo y el documento, y solo él confirma](../decisiones/25-el-997-se-correlaciona-por-grupo-y-documento.md)

## Repaso

1. ¿Qué es un 997, y quién lo manda?
2. ¿Qué cita el AK1 y qué cita el AK2?
3. ¿Por qué el ISA13 del 997 no sirve para correlacionar?
4. ¿Qué pasa con un 997 que no corresponde a ninguna operación? ¿Por qué no se tira?
5. **La pregunta de cierre:** «depositado» y «confirmado» no son lo mismo. ¿Qué canal de la práctica espera cuál, y
   por qué la API y el ERP no esperan un acuse?

**Siguiente:** en la lección 22 el SFTP del socio se cae, y con los reintentos de la 19 eso detiene también a la API
y al ERP. ¿Cómo dejas de llamar a un destino caído sin parar a los demás?
