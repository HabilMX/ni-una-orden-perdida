# Lección 26 — Seguir una orden

> **Lo que vas a lograr:** contestar «¿qué pasó con la orden 4006?» con una sola consulta: cada canal, cada intento,
> lo que salió en cada uno completo, lo que contestó el socio y si la orden quedó completa. Y servir esa respuesta
> por HTTP, buscando por el número de orden.

**Antes de empezar necesitas:** las lecciones 15 y 25 terminadas y el ambiente de la 00 encendido.

## Lo nuevo de hoy, en una frase

Llevas once lecciones guardando cosas en la base de estado: la orden (15), cada operación y cada intento (15), los
conflictos (18), los acuses del socio (21), los avisos y lo completo (25). Cada pieza responde una pregunta chica.
Hoy las juntas para responder la grande, la que hace un cliente o alguien de soporte: **«mi orden no llegó, ¿qué le
pasó?»**. Y la respondes por la **llave de negocio**, el número de orden, que es lo único que trae quien pregunta.

**La imagen:** el rastreo de un paquete en la página de una paquetería. Escribes el número de guía y ves la historia
completa: recibido en sucursal, en tránsito, intento de entrega fallido porque no había nadie, entregado. No ves solo
el estado de hoy: ves **cada paso**, con su hora. Si solo dijera «entregado», no sabrías que hubo un intento fallido,
ni por qué tardó dos días.

---

## 1. Prepara tu carpeta

```bash
cd lecciones/26-seguir-una-orden
cp -R inicio mio
```

El router tiene dos archivos nuevos:

| Archivo | Qué es | ¿Lo escribes? |
|---|---|---|
| `orden-router/recorrido.camel.yaml` | Al arrancar, declara la vista con lo que diga `recorrido.sql`. Y sirve `GET /ordenes/{orden}/recorrido` | No, léelo |
| `orden-router/recorrido.sql` | La vista `recorrido` de la base de estado | **Sí** |

Una **vista** es una consulta guardada con nombre. Se lee como si fuera una tabla (`SELECT * FROM recorrido WHERE
orden = '4006'`), pero no guarda nada propio: cada vez que la lees, vuelve a juntar las tablas de verdad. Por eso no
se desactualiza.

Y hay una tabla nueva desde hoy, `payload`. Cada intento guarda en `intento` su **huella** (`sha256`) y su
**tamaño**, y el cuerpo completo de lo que salió vive en `payload`, una vez por huella. Si dos intentos mandaron lo
mismo, comparten la fila.

Abre `infra/postgres/01-estado.sql` y ubica las tablas que vas a juntar: `operacion`, `intento`, `payload`, `acuse`,
`alerta` y `completitud`.

## 2. Predice

Contesta en un papel:

1. Un cliente dice que la 4006 no llegó. ¿Qué consultas primero: el log del router, la API, el ERP o la base de
   estado? ¿Por qué?
2. La API estuvo caída cuando llegó la 4006; después alguien hizo un rerun. Si la vista muestra solo el último
   intento de cada canal, ¿qué parte de la historia se pierde?
3. La 4007 trae 150 renglones. ¿El log del router la muestra completa?

Mira cómo está hoy:

```bash
./verifica inicio
```

La vista existe y el `GET` contesta, pero es un **tablero de hoy**: una fila por operación, con su último intento. La
historia del canal rest de la 4006 se ve como un solo `aceptada`, sin el primer intento que no supo ni quién hizo el
rerun. Y la columna `cuerpo` sale vacía: no dice qué salió.

## 3. Arma el recorrido

Abre `mio/orden-router/recorrido.sql`. Es un `CREATE VIEW` con un `SELECT` que ya trae las columnas con su nombre
final; algunas salen en `NULL` por ahora. Tiene tres TODO:

| TODO | Qué cambias | Para qué |
|---|---|---|
| 1 | El `LEFT JOIN LATERAL (… LIMIT 1) u` por un `LEFT JOIN intento i ON i.operacion = p.id`, y las columnas `u.` por `i.` | Una fila por **cada** intento, no solo el último |
| 2 | Un `LEFT JOIN payload d ON d.sha256 = i.sha256`, y `cuerpo` sale de `d.cuerpo` | Lo que salió en cada intento, completo |
| 3 | Tres columnas que hoy son `NULL`: `acuses`, `alertas` y `completa` | Lo que vino después del envío |

Para el TODO 3, dos de las columnas son una **subconsulta** que junta varias filas en un texto con `string_agg`:

```sql
(SELECT string_agg(a.codigo, ',' ORDER BY a.id) FROM acuse a WHERE a.operacion = p.id) AS acuses,
```

Esa da, por ejemplo, `A` o `R`: lo que dijo el socio de esa operación. La de `alertas` es igual, sobre la tabla
`alerta`, para la misma orden, el mismo momento y el mismo canal, con `l.tipo || ' en ' || l.estado`. Y `completa`
sale de un `LEFT JOIN completitud c ON c.orden = p.orden AND c.momento = p.momento`, con `c.completa`.

¿Por qué `LEFT JOIN` y no `JOIN`? Porque una operación que todavía no tiene intentos, o un intento sin acuse, o un
momento que no se ha completado, **también tiene que salir**. Con `JOIN` desaparecerían, y lo que falta es justo lo
que más importa al seguir una orden.

¿Y por qué subconsultas para acuses y alertas, y no más `JOIN`? Porque un `JOIN` contra una tabla con varias filas por
operación **multiplica** las filas: cada intento saldría una vez por cada acuse. La subconsulta los junta en una sola
celda.

Deja los nombres de las columnas como están: el `GET` los usa.

## 4. Rompe uno a propósito

Con tu vista terminada, quita el `LEFT JOIN completitud` y deja `completa` como `CAST(NULL AS timestamptz)`. Corre
`./verifica`.

Antes, predice: la 4007 se completó. ¿Qué dice el recorrido?

Que no. Sale en rojo la última marca: la vista dice que **ni** la 4007 **ni** la 4006 quedaron completas. Nada falló:
la vista simplemente no miraba esa tabla, y una celda vacía se lee como «no pasó». **Un recorrido que no muestra algo
no prueba que no pasó.** Por eso se arma con `LEFT JOIN` contra todo lo que la base sabe. Regresa la línea.

## 5. Comprueba

```bash
./verifica
```

`verifica` apaga la API, manda la 4006 (que pide un producto que el socio no conoce) y la 4007 (grande, con 150
renglones); vuelve a prender la API, hace un rerun del canal rest de las dos y pide el recorrido. Está terminada
cuando ves las ocho marcas verdes:

```
✅ Arrancaron el router (con su GET /ordenes/{orden}/recorrido), los tres canales y el sondeo
✅ El router declaró la vista recorrido al arrancar
✅ GET /ordenes/4006/recorrido: (…) filas, una por cada intento de la 4006
✅ El canal rest de la 4006 cuenta su historia: (…)
✅ El canal del socio de la 4006: rechazada, con su 997 de rechazo (R) en el recorrido
✅ Cada intento del recorrido trae lo que salió en él (cuerpo)
✅ La 4007 (grande): su cuerpo está entero en el recorrido, (…) bytes con su huella y sus 150 renglones
✅ El recorrido dice que la 4007 quedó COMPLETA y la 4006 no
```

Pide tú el recorrido:

```bash
curl -s localhost:8080/ordenes/4006/recorrido | python3 -m json.tool | less
```

**¿No te corre?** `./verifica solucion` prueba la nuestra, y
`diff mio/orden-router/recorrido.sql solucion/orden-router/recorrido.sql` te dice en qué son distintas.

| Lo que ves | Qué haces |
|---|---|
| `No existe la vista recorrido` | Tu `recorrido.sql` tiene un error de SQL: el router lo dice en el log, debajo de esa marca |
| `la vista tiene … y la 4006 tiene … intentos` | Sigue el `LATERAL … LIMIT 1`, o el `JOIN` de un acuse multiplicó las filas |
| `El canal rest de la 4006 en el recorrido: «aceptada/manual»` sin lo de antes | Solo ves el último intento: falta el TODO 1 |
| `intentos del recorrido sin su cuerpo` | Falta el `LEFT JOIN payload`, o une por otra columna: es `sha256` |
| `El canal del socio de la 4006 … (esperaba «rechazada R»)` | La columna `acuses` sigue en `NULL` |
| `Completa en el recorrido` | Falta el `LEFT JOIN completitud` |
| `El puerto 8080 está ocupado` | Quedaron arriba los servicios de otra corrida: `DONDE=mio docker compose -f compose.yaml down` |

Si cambias `recorrido.sql` con los servicios arriba, reinicia el router: la vista se declara al arrancar.

---

## Lo que esto enseña fuera de la práctica

**Se busca por lo que trae quien pregunta.** Nadie de soporte sabe el `id` de una operación ni el número de control
de un X12. Sabe el número de orden. El recorrido se arma desde ahí.

**El log no es el registro.** El log recorta los cuerpos grandes, se mezcla con el de otras órdenes y se va con el
contenedor. La base guarda cada intento con lo que salió, completo, mientras alguien no lo borre a propósito.

**La historia vale más que el estado.** «Aceptada» dice cómo terminó; la historia dice que primero no se supo, que el
cortacircuito no la mandó (la 22), que alguien de la mesa de control hizo un rerun y que la API contestó 201. En la
verifica se ve así: `no_se_sabe/auto no_enviado/auto aceptada/manual`. Con eso se
explica por qué tardó.

**Una vista no se desactualiza.** No copia los datos: los vuelve a juntar cada vez. Es la forma más barata de tener
una respuesta nueva sin una segunda fuente de verdad.

## Ejercicio: la orden problema

Con tu versión terminada, corre `./verifica` y luego:

1. Pide el recorrido de la 4006 y, **solo con él**, escribe un párrafo para el cliente que dice que su orden «no
   llegó». ¿Le llegó a la API? ¿Al ERP? ¿Al socio? ¿Qué pasó con el socio, en qué renglón y con qué producto? (Pista:
   el cuerpo del intento del socio y el 997 de la 21.)
2. Busca la 4007 en el log del router: `DONDE=mio docker compose -f compose.yaml logs orden-router | grep 4007`. ¿Ves
   sus 150 renglones? Compara con la columna `tamano` del recorrido.
3. Escribe la consulta que le darías a alguien de soporte para ver **solo los canales que no terminaron bien** de una
   orden, con su último desenlace. Empieza con `SELECT canal, estado_canal, …  FROM recorrido WHERE orden = '4006'`.

---

## Por qué se hizo así

Lo que decidimos en esta lección, con lo que descartamos y qué nos haría cambiar de idea:

- [El recorrido es una vista](../decisiones/30-el-recorrido-es-una-vista.md)

## Repaso

1. ¿Qué es una vista, y por qué no se desactualiza?
2. ¿Por qué el recorrido se busca por el número de orden?
3. ¿Qué guarda la tabla `payload`, y cómo se une con cada intento?
4. ¿Por qué `LEFT JOIN` y no `JOIN`? ¿Por qué subconsultas para los acuses?
5. **La pregunta de cierre:** un cliente dice que su orden no llegó. ¿Qué consultas primero, y por qué el recorrido y
   no el log?

**Siguiente:** en la lección 27 vuelve a mirar el recorrido de la 4008. Trae el nombre, la dirección, el teléfono y el
correo de una persona. ¿Quién debería poder verlos?
