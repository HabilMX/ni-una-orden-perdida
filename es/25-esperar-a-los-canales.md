# Lección 25 — Esperar a los canales

> **Lo que vas a lograr:** saber cuándo una orden está **completa**: cuando cada canal que le toca llegó a lo que se
> espera de él, incluido el 997 del socio. Y que un canal que tarda demasiado se **avise** sin dar la orden por
> cerrada, para que lo que llegue tarde todavía la complete, aunque el router se haya reiniciado en medio.

**Antes de empezar necesitas:** las lecciones 21 y 22 terminadas y el ambiente de la 00 encendido.

## Lo nuevo de hoy, en una frase

Desde la 10, el router **esparce**: manda cada momento de la orden a sus canales. Nunca lo **junta**: no hay un
lugar que diga «esta orden ya terminó». Y en la 21 viste que «terminar» no es lo mismo para todos: la API y el ERP
terminan cuando aceptan, el socio cuando manda su 997. Esparcir y luego juntar las respuestas es un patrón con
nombre, *Scatter-Gather*, y la parte de juntar se llama *Aggregator*. Hoy lo construyes, con una idea que viene de
la 15: **lo que hay que juntar ya está en la base.**

**La imagen:** organizar la cena de fin de año con tres primos que traen algo. La tía no da por lista la cena cuando
cada primo dijo «ahí voy»: la da por lista cuando la comida está en la mesa. Si a las nueve el del postre no ha
llegado, la tía no tacha el postre y declara la cena completa: le marca para preguntar. Y si el postre llega a las
nueve y media, la cena se completa entonces. Si la tía se sale un rato al patio, no pierde la cuenta: la mesa sigue
ahí con lo que ya llegó.

---

## 1. Prepara tu carpeta

```bash
cd lecciones/25-esperar-a-los-canales
cp -R inicio mio
```

Abre primero `mio/orden-router/reglas/maquina-de-estados.yaml` y busca la sección `completa`. Ahí está lo que se
espera de cada canal y cuánto se le espera:

| Canal | `llega_a` | `plazo_s` | Por qué |
|---|---|---|---|
| `rest` (la API) | `aceptada` | 10 | Su «sí» llega en la misma llamada |
| `bd` (el ERP) | `aceptada` | 10 | Igual |
| `edi` (el socio) | `confirmada` | 20 | Su «sí» es el 997, y llega después (la 21) |

Ahora abre `mio/orden-router/juntar.camel.yaml`. Arriba hay un dibujo de la 4001 a medio camino: la API y el ERP ya
llegaron, el socio todavía no. Y hay una ruta, `vigilar`, ya hecha: un **reloj** (`timer`) que cada 2 segundos lee
las reglas y llama a dos rutas:

| Ruta | Qué hace | Dónde lo anota |
|---|---|---|
| `juntar` | Da por COMPLETO cada momento cuyos canales llegaron todos a su `llega_a` | Tabla `completitud`, una fila por orden y momento |
| `vencidos` | Avisa de cada canal que pasó su plazo sin llegar | Tabla `alerta` y un `WARN` en el log |

¿Por qué un reloj y no esperar a que algo avise? Porque **lo que no llega no avisa**. Un 997 que nunca viene no
dispara ningún evento. Alguien tiene que mirar el reloj.

Fíjate en lo que no hay: no hay un agregador que guarde en la memoria del router qué canales ya contestaron. Cada
operación y su estado viven en la tabla `operacion` desde la 15. **Juntar es preguntarle a la base.**

## 2. Predice

Contesta en un papel:

1. La API y el ERP aceptaron la 4001, y el 850 quedó depositado. El socio no ha mandado el 997. ¿La orden está
   completa?
2. La 3001 es de una tienda propia: no lleva EDI (la 11). ¿Cuándo está completa?
3. Pasan 20 segundos y el 997 de la 4002 no llega. ¿Qué es mejor: dar la orden por completa para que deje de
   estorbar, o avisar y dejarla abierta? ¿Qué pasa en cada caso si el 997 llega a los 30 segundos?
4. Reinicias el router mientras la 4002 espera su 997. ¿Se pierde lo que ya llevaba juntado?

Mira cómo está hoy:

```bash
./verifica inicio
```

Sale mal desde la primera marca: la 4001 queda COMPLETA **antes** de su 997, porque la versión de `inicio/` da por
completo un momento cuando ningún canal sigue `pendiente`, y un 850 depositado está `aceptada`. Y la 4002, al vencer,
se cierra a la fuerza: queda completa sin que el socio haya contestado nada.

## 3. Junta y vigila

En `juntar.camel.yaml` hay dos TODO, uno en cada ruta. Los dos son una consulta SQL que ya está casi entera.

### TODO 1: completa es «cada canal llegó a lo suyo»

La consulta de `juntar` junta cada operación con la regla de su canal y agrupa por orden y momento:

```sql
INSERT INTO completitud (orden, momento)
SELECT p.orden, p.momento
FROM operacion p JOIN jsonb_each(CAST(:#${variable.reglas} AS jsonb)) AS r(canal, regla) ON r.canal = p.canal
WHERE NOT EXISTS (SELECT 1 FROM completitud c WHERE c.orden = p.orden AND c.momento = p.momento)
GROUP BY p.orden, p.momento
HAVING bool_and(p.estado NOT IN ('pendiente', 'estacionada'))     -- ← esta condición
ON CONFLICT DO NOTHING
RETURNING orden, momento
```

Se lee así:

| Pieza | Qué hace |
|---|---|
| `jsonb_each(…) AS r(canal, regla)` | Convierte las reglas en filas: una por canal, con su `llega_a` y su `plazo_s` |
| `JOIN … ON r.canal = p.canal` | A cada operación le pega la regla de su canal |
| `GROUP BY p.orden, p.momento` | Junta las operaciones de cada momento de cada orden |
| `HAVING bool_and(…)` | El momento pasa solo si la condición es cierta **para todas** sus operaciones |
| `ON CONFLICT DO NOTHING` | Un momento se completa una sola vez |

Tu trabajo es la condición del `HAVING`. Hoy dice «ninguna sigue pendiente ni estacionada». Tiene que decir **«cada
una está en un estado de su `llega_a`»**:

```sql
HAVING bool_and(jsonb_contains(r.regla -> 'llega_a', to_jsonb(p.estado)))
```

`r.regla -> 'llega_a'` es la lista de la regla, por ejemplo `["confirmada"]`; `to_jsonb(p.estado)` es el estado de la
operación como valor JSON; `jsonb_contains` pregunta si el segundo está en el primero.

Fíjate en algo que sale gratis: **la 3001 no tiene operación `edi`**, porque es de una tienda propia. Como no tiene
fila, no cuenta: nadie espera un 997 que no le toca.

### TODO 2: vencido avisa y no cierra

La consulta de `vencidos` hoy **escribe en `completitud`**: cierra el momento al vencer. Cámbiala para que escriba un
aviso en `alerta`, una vez por canal:

| Cambio | Qué escribes |
|---|---|
| A dónde | `INSERT INTO alerta (orden, momento, canal, tipo, estado, espera)` |
| Qué | `SELECT p.orden, p.momento, p.canal, 'vencido', p.estado, r.regla ->> 'llega_a'` (sin el `DISTINCT`) |
| Cuáles | Agrega al `WHERE`: `NOT jsonb_contains(r.regla -> 'llega_a', to_jsonb(p.estado))`, para avisar solo de los canales que **no** han llegado |
| Una vez | Deja el `ON CONFLICT DO NOTHING`: la tabla `alerta` tiene una `UNIQUE (orden, momento, canal, tipo)` |
| Para el log | Agrega `RETURNING orden, momento, canal, estado, espera` y `outputType: SelectList` |

Y después de la consulta, un `split` sobre `${body}` con un `log` en `WARN` por cada aviso nuevo, como el `split` de
`juntar`. El mensaje empieza con `VENCIDO orden ${body[orden]} momento ${body[momento]} canal ${body[canal]}`.

## 4. Rompe uno a propósito

Con todo terminado, quita del `WHERE` de `vencidos` la condición `NOT jsonb_contains(…)`. Corre `./verifica`.

Antes, predice: la 4002 espera su 997 más de 20 segundos. La API y el ERP la aceptaron a los pocos segundos. ¿Cuántos
avisos esperas?

Tres en vez de uno. Sin esa condición, el reloj avisa de **todo** canal que lleve más que su plazo, haya llegado o no:
la API y el ERP, que ya aceptaron, también salen como vencidos. Sale en rojo la marca del aviso de la 4002. Un aviso
que salta de lo que está bien enseña a la gente a ignorar los avisos, y entonces el que importa se pierde. Regresa la
condición.

## 5. Comprueba

```bash
./verifica
```

`verifica` manda una orden de tienda propia y una de franquicia, espera el 997; luego apaga el sistema del socio y
manda la 4002, espera a que venza, **reinicia el router** y vuelve a prender al socio. Está terminada cuando ves las
siete marcas verdes:

```
✅ Arrancaron el router (con su reloj que junta y vigila), los tres canales y el sondeo
✅ La 4001: API y ERP aceptada y el 850 depositado, pero sin 997 todavía no está COMPLETA
✅ La 3001 (tienda propia): COMPLETA con la API y el ERP; el socio no le toca y no se le espera
✅ La 4001: llegó el 997, el socio quedó confirmada y la orden COMPLETA
✅ La 4002 sin 997 pasó su plazo: un solo aviso (vencido, en «aceptada», se espera confirmada) y sigue abierta
✅ Log: el aviso del vencido está en el log del router
✅ Tras reiniciar el router, el 997 tardío confirmó al socio y la 4002 quedó COMPLETA, con su único aviso como historia
```

Lee la última con calma. El router se reinició **a media espera**, y la 4002 se completó igual. No escribiste nada
para eso: lo que se estaba juntando nunca vivió en la memoria del router, vivía en la base. Al volver, el reloj
preguntó otra vez y encontró lo mismo, más el 997 que acababa de llegar.

Mira las dos tablas:

```bash
docker compose -f ../../infra/compose.yaml exec estado psql -U router -d estado \
  -c "SELECT * FROM completitud ORDER BY completa" -c "SELECT orden, canal, estado, espera, creada FROM alerta"
```

**¿No te corre?** `./verifica solucion` prueba la nuestra, y
`diff mio/orden-router/juntar.camel.yaml solucion/orden-router/juntar.camel.yaml` te dice en qué son distintas.

| Lo que ves | Qué haces |
|---|---|
| `La 4001 con el socio en «aceptada» ya quedó COMPLETA` | El `HAVING` sigue con la condición de `inicio/`: cámbiala por el `jsonb_contains` |
| `La 3001 (tienda propia) no quedó COMPLETA` | El `JOIN` con las reglas no está, o cambiaste `bool_and` por otra cosa |
| `La 4002 sin 997: hay 0 avisos` y `completa=1` | `vencidos` sigue escribiendo en `completitud`: es en `alerta` |
| `La 4002 sin 997: hay 3 avisos` | Falta el `NOT jsonb_contains(…)` en el `WHERE` de `vencidos` |
| `no está el WARN del vencido de la 4002` | Falta el `RETURNING` con `outputType: SelectList`, o el `split` con su `log` en `WARN` |
| `No está arriba socio-bot` | Levanta el ambiente: `docker compose -f infra/compose.yaml up -d --wait` |
| `El puerto 8080 está ocupado` | Quedaron arriba los servicios de otra corrida: `DONDE=mio docker compose -f compose.yaml down` |

---

## Lo que esto enseña fuera de la práctica

**Esparcir es fácil; juntar es donde está el trabajo.** Mandar algo a varios destinos lo hace cualquier multicast.
Saber cuándo todos terminaron, cada uno con su propia idea de «terminé», es lo que le dice al negocio que la orden ya
está en todas partes.

**Lo que no llega no avisa.** Un sistema que solo reacciona a eventos nunca se entera de lo que falta. Hace falta algo
que mire el reloj y pregunte.

**Avisar no es cerrar.** Cerrar a la fuerza al vencer dice «terminó» de algo que no terminó, y si lo que faltaba llega
tarde ya no cambia nada. Avisar deja que una persona decida, y deja que lo tardío todavía cuente.

**Si el estado vive fuera del proceso, el reinicio no es un problema.** Lo dijimos en la 15 como teoría; hoy lo viste:
el router se reinició a media espera y no perdió nada, sin una línea extra de código.

## Ejercicio: la cena de fin de año

1. Antes de correr nada, escribe en papel tu predicción a la pregunta 4 de arriba: ¿se pierde lo juntado al
   reiniciar? Después de correr `./verifica`, compárala con lo que pasó y escribe dónde vivía lo juntado.
2. Cambia en las reglas el `plazo_s` del socio a `5` y corre `./verifica`. ¿Qué marcas cambian, y por qué? ¿Qué le
   pasaría a la gente que atiende los avisos si el plazo fuera tan corto en la vida real? Regrésalo a `20`.
3. Imagina que el router tuviera tres copias, cada una con su reloj. ¿Se completaría una orden tres veces? ¿Se avisaría
   tres veces un vencido? (Pista: busca las dos `UNIQUE` y el `ON CONFLICT`.)

---

## Por qué se hizo así

Lo que decidimos en esta lección, con lo que descartamos y qué nos haría cambiar de idea:

- [Juntar es preguntarle a la base, y un vencido avisa sin cerrar](../decisiones/29-juntar-es-preguntarle-a-la-base.md)

## Repaso

1. ¿Qué es un Scatter-Gather? ¿Qué parte de la práctica esparce y qué parte junta?
2. ¿Qué espera cada canal para darse por terminado, y dónde está escrito?
3. ¿Por qué hace falta un reloj para juntar?
4. ¿Por qué la 3001 se completa sin 997?
5. **La pregunta de cierre:** al vencer el plazo del socio, el router avisa y deja la orden abierta. ¿Por qué cerrarla
   a la fuerza sería peor? Usa el 997 tardío de la 4002 en tu respuesta.

**Siguiente:** en la lección 26 un cliente dice que su orden no llegó. Tienes la base, el log y los destinos. ¿Qué
consultas primero, y cómo reconstruyes el viaje completo de una orden?
