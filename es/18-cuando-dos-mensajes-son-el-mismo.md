# Lección 18 — ¿Cuándo dos mensajes son el mismo?

> **Lo que vas a lograr:** que el router reconozca un aviso que ya recibió, aunque llegue escrito distinto, y no
> lo reparta otra vez. Y que distinga ese repetido de un aviso que trae la misma llave con otro contenido: eso no
> es un repetido, es un conflicto, y se avisa.

**Antes de empezar necesitas:** las lecciones 15 y 16 terminadas y el ambiente de la 00 encendido. Esta lección
usa una tabla nueva de la base de estado, `conflicto`. Si tu base es de antes, recréala una vez:

```bash
docker compose -f infra/compose.yaml up -d --wait --force-recreate -V estado
```

## Lo nuevo de hoy, en una frase

En la 16 aprendiste que quien manda un webhook **reintenta** si no recibe su 2xx a tiempo. Eso tiene una
consecuencia: tarde o temprano el mismo aviso llega dos veces. Hoy el router aprende a reconocerlo. A eso se le
llama *Idempotent Consumer*: recibir dos veces lo mismo deja el mismo resultado que recibirlo una vez.

**La imagen:** la caseta de la entrada de un edificio con su libreta de visitas. Llega la señora del 302 a las
10:00 y la anotan. A las 10:05 regresa porque olvidó el gafete: no la anotan otra vez, ya está en la libreta. Pero
si llega alguien diciendo que es la señora del 302 y no se parece a la de la foto, el guardia no la deja pasar ni
la anota encima: le habla a quien vive ahí. El primer caso es un **repetido**; el segundo, un **conflicto**.

---

## 1. Prepara tu carpeta

```bash
cd lecciones/18-cuando-dos-mensajes-son-el-mismo
cp -R inicio mio
```

El router tiene un archivo nuevo, `orden-router/reconocer.camel.yaml`. Ábrelo y lee el comentario de arriba. Dice
con qué se reconoce un mensaje:

| Concepto | Qué es | En la práctica |
|---|---|---|
| **Llave** | Lo que dice **de qué** es el mensaje | `orden-<orden>-<momento>`, por ejemplo `orden-4001-entregada`. Cada canal la lleva una vez: es la `UNIQUE (orden, momento, canal)` de `operacion` |
| **Huella** | Lo que dice **qué trae** el mensaje | El sha256 del cuerpo, ya normalizado. Dos cuerpos con el mismo contenido tienen la misma huella |

Con esas dos piezas salen tres casos:

| Caso | Llave | Huella | Qué contesta el router | Qué hace |
|---|---|---|---|---|
| **nuevo** | No hay operaciones con esa llave | — | 202 «recibida» | Guarda y reparte, como hasta la 17 |
| **repetido** | Ya existe | La misma | 202 «repetido» | Nada: ya lo tenía |
| **conflicto** | Ya existe | Otra | 409 «conflicto» | Lo anota en la tabla `conflicto`, avisa en el log y no pisa lo guardado |

Abajo del archivo está la ruta `recibir-momento`, que ya usa esa decisión: lee `${variable.visto}` y contesta o
guarda según el caso. Tu trabajo es la ruta que llena esa variable, `reconocer`.

## 2. Predice

Contesta en un papel:

1. La paquetería avisa dos veces la entrega de la 4001, con el mismo contenido. ¿Cuántos 214 esperas en el SFTP del
   socio? ¿Cuántos eventos en la API?
2. El segundo aviso trae lo mismo, pero en una sola línea y con los campos en otro orden. ¿Es el mismo mensaje?
3. Llega un tercer aviso de la 4001 con la misma llave y **otra guía**. ¿Qué debería pasar con la guía que ya tienen
   la API y el ERP?

Mira cómo está hoy:

```bash
./verifica inicio
```

El primer aviso pasa. El segundo **contesta un error**: la ruta de `inicio/` ve todo como nuevo, intenta guardar las
operaciones otra vez y la `UNIQUE` de `operacion` lo detiene. No se duplicó nada, pero la paquetería recibe un error
por algo que sí teníamos, y como aprendiste en la 16, un emisor que recibe error **vuelve a mandar**. Y el aviso
con otra guía tampoco se distingue: es un error más.

## 3. Reconoce lo que llega

Abre `mio/orden-router/reconocer.camel.yaml` y busca la ruta `reconocer`. Hoy solo pone `visto` en `nuevo`. Tiene
cuatro TODO:

| TODO | Qué escribes | Para qué |
|---|---|---|
| 1 | Un `to` con `sql:` que regrese dos columnas: `nueva`, la huella de lo que llegó, y `guardada`, la huella de la primera operación con esa orden y ese momento | Comparar lo que llegó con lo que ya tienes, en una sola consulta |
| 2 | Un `setVariable` `huella` con la huella nueva | `guardar-momento` la escribe en cada operación, para comparar la próxima vez |
| 3 | Un `choice` de tres ramas que pone `visto` en `nuevo`, `repetido` o `conflicto` | La decisión que lee `recibir-momento` |
| 4 | En la rama de conflicto: un `INSERT INTO conflicto` y un `log` en `WARN` que empiece con `CONFLICTO orden-<orden>-<momento>` | Que el conflicto quede escrito y alguien lo vea |

Cuatro pistas:

- La consulta del TODO 1 está casi completa en el comentario. Lleva `dataSource: "#estado"`, `outputType: SelectOne`
  y `variableReceive: huellas`. Con eso, `${variable.huellas[nueva]}` y `${variable.huellas[guardada]}` son las dos
  huellas.
- Para `guardada`, una subconsulta: `(SELECT huella FROM operacion WHERE orden = … AND momento = … ORDER BY id
  LIMIT 1)`. Si no hay operaciones, regresa `NULL`.
- El `choice` compara así: `${variable.huellas[guardada]} == null` es nuevo; `${variable.huellas[guardada]} ==
  ${variable.huella}` es repetido; lo demás (`otherwise`) es conflicto.
- La tabla `conflicto` está en `infra/postgres/01-estado.sql`. Sus columnas son `llave`, `orden`, `momento`,
  `huella_guardada`, `huella_nueva`, `cuerpo` (como `jsonb`, igual que en la 15) y `correlacion`.

Fíjate en la línea de la huella:

```sql
encode(sha256(convert_to(CAST(CAST(:#${body} AS jsonb) AS text), 'UTF8')), 'hex')
```

Se lee de adentro hacia afuera: el cuerpo se convierte en `jsonb`, el `jsonb` se vuelve texto, el texto se vuelve
bytes, a los bytes se les saca el sha256 y el resultado se escribe en hexadecimal. El paso que importa es el
primero. **PostgreSQL guarda el `jsonb` sin los espacios de más y con las llaves en un orden suyo**, así que dos
cuerpos con el mismo contenido, escritos distinto, salen iguales al volverse texto. Sin ese `CAST`, la huella sería
la del texto tal cual llegó.

## 4. Rompe uno a propósito

Con tu `reconocer` terminado, quita el `CAST(… AS jsonb)` de adentro: deja `convert_to(:#${body}, 'UTF8')`. Corre
`./verifica`.

Antes, predice: el segundo aviso de la verifica trae el mismo contenido en una sola línea y con los campos al revés.
¿Cómo lo vas a reconocer?

Sale en rojo la marca de los dos avisos: el router le contestó 409 al segundo. La huella se sacó del texto, y el texto cambió aunque el
contenido no. **Tomaste un reenvío por un cambio**, y alguien va a tener que revisar un conflicto que no existe.
Regresa el `CAST`.

## 5. Comprueba

```bash
./verifica
```

`verifica` crea y embarca la 4001, hace de paquetería con su aviso de entrega, lo manda otra vez escrito distinto y
al final manda uno con otra guía. Está terminada cuando ves las nueve marcas verdes:

```
✅ Arrancaron el router (con su reconocedor de repetidos), los tres canales y el sondeo
✅ Antes de los avisos: la 4001 está EMBARCADO
✅ Los dos avisos contestaron 202, y el segundo se reconoció como repetido aunque viniera escrito distinto
✅ Estado: la entrega tiene 3 operaciones con su huella sha256 y 3 intentos, uno por canal
✅ Destinos: un solo 201 de la entrega en la API, un solo 214 en el socio y el ERP en ENTREGADO
✅ Un aviso con la misma llave y otra guía contestó 409 «conflicto»
✅ Estado: el conflicto quedó en su tabla, con la llave, lo que llegó y las dos huellas distintas
✅ Log: el router avisó el conflicto con un WARN
✅ Nada se pisó: la orden guarda la guía original, sigue habiendo 3 intentos y la API tiene la guía con la que salió
```

Mira la tabla del conflicto:

```bash
docker compose -f ../../infra/compose.yaml exec estado psql -U router -d estado \
  -c "SELECT llave, cuerpo->>'guia' AS guia, huella_guardada <> huella_nueva AS distintas FROM conflicto"
```

**¿No te corre?** `./verifica solucion` prueba la nuestra, y
`diff mio/orden-router/reconocer.camel.yaml solucion/orden-router/reconocer.camel.yaml` te dice en qué son
distintas.

| Lo que ves | Qué haces |
|---|---|
| `Esperaba 202 y 202 «repetido»` y el segundo contestó 500 | Tu `choice` no llegó a poner `repetido`: revisa que la consulta lleve `variableReceive: huellas` y que `guardada` busque por orden **y** momento |
| `Esperaba 202 y 202 «repetido»` y el segundo contestó 409 | La huella se saca del texto: falta el `CAST(:#${body} AS jsonb)` de adentro |
| `esperaba 3 operaciones con huella` y hay 0 huellas | Falta el TODO 2: la variable `huella` no existe cuando `guardar-momento` la escribe |
| `Esperaba 409 para el aviso con otra guía` | La rama de conflicto no pone `visto` en `conflicto`, o el `choice` la toma por nuevo |
| `esperaba una fila en conflicto` | Falta el `INSERT INTO conflicto`, o no lleva `dataSource: "#estado"` |
| `no hay un WARN «CONFLICTO orden-4001-entregada»` | El `log` no lleva `loggingLevel: WARN`, o el mensaje no empieza con `CONFLICTO orden-${variable.orden}-${variable.momento}` |
| `El puerto 8080 está ocupado` | Quedaron arriba los servicios de otra corrida: `DONDE=mio docker compose -f compose.yaml down` |

---

## Lo que esto enseña fuera de la práctica

**En integración, un mensaje llega «al menos una vez», no «exactamente una vez».** Los reintentos protegen contra
la pérdida, y a cambio traen repetidos. Quien recibe se encarga de reconocerlos. Por eso casi toda API seria de
pagos o de envíos pide una llave de idempotencia.

**La llave sale del negocio, no del azar.** Un número al azar nuevo en cada envío haría que cada reenvío pareciera
otro mensaje. `orden-4001-entregada` es la misma cada vez que la paquetería reenvía, y por eso sirve.

**Llave y huella contestan preguntas distintas.** La llave dice si ya viste algo **de esto**; la huella, si es **lo
mismo**. Con solo la llave, un cambio pasa por repetido y se pierde. Con solo la huella, el mismo aviso con otra
fecha de envío pasaría por nuevo.

**Un conflicto no lo resuelve el programa.** Quedarse con el primero o con el último en silencio sería decidir por
alguien. Se anota, se avisa y lo resuelve una persona.

## Ejercicio: la libreta de la caseta

Con tu versión terminada:

1. Escribe en papel la llave de estos tres mensajes: el embarque de la 4002, la entrega de la 4002 y la entrega de la
   4003. ¿Por qué no sirve como llave un número al azar que el router genere al recibir?
2. Saca a mano, con `psql`, la huella de `{"orden":"4001","guia":"X"}` y de `{ "guia": "X", "orden": "4001" }`, con y
   sin el `CAST` a `jsonb`. Usa `SELECT encode(sha256(convert_to(CAST('…' AS jsonb)::text, 'UTF8')), 'hex')`. ¿Cuántas
   huellas distintas te salen en cada caso?
3. En el aviso de la paquetería, ¿qué campo podría cambiar entre un reenvío y otro sin que cambie lo que pasó? (Pista:
   piensa en cuándo se **mandó** el aviso, no en cuándo se entregó.) ¿Qué pasaría con la huella? Escribe tu
   respuesta: no la resuelve esta lección.

---

## Por qué se hizo así

Lo que decidimos en esta lección, con lo que descartamos y qué nos haría cambiar de idea:

- [Un mensaje se reconoce por su llave, y su contenido por su huella](../decisiones/22-la-llave-y-la-huella.md)

## Repaso

1. ¿Qué es un Idempotent Consumer? Dilo con el ejemplo de la caseta.
2. ¿Qué es la llave de un momento en esta práctica, y de dónde sale?
3. ¿Qué es la huella, y por qué se saca después de convertir el cuerpo en `jsonb`?
4. ¿Por qué el repetido se contesta 202 y no un error? (Regresa a la 16.)
5. **La pregunta de cierre:** un repetido y un conflicto tienen la misma llave. ¿Qué los distingue, qué contesta el
   router a cada uno y por qué el conflicto no pisa lo guardado?

**Siguiente:** en la lección 19 el problema es el contrario. No llega dos veces: no llega. La API no contesta, y el
router tiene que volver a intentar sin desordenar los momentos de la orden.
