# Juntar es preguntarle a la base, y un vencido avisa sin cerrar

> **Dónde aparece:** lección 25 (y el reinicio que el plan tenía para la 26).

## El problema

El reparto manda cada momento de la orden a sus canales. Hace falta saber cuándo ese momento **terminó**: cuando
cada canal que le toca llegó a lo que se espera de él. La API y el ERP contestan al momento; el socio acepta el
archivo al depositarlo, pero lo que cuenta es su 997, que llega después y por otro camino (lección 21). Y si algo no
llega, alguien tiene que enterarse.

## Qué decidimos

- Lo que cada canal espera y su plazo se declaran en `reglas/maquina-de-estados.yaml`, sección `completa`: la API y
  el ERP, `aceptada` en 10 s; el socio, `confirmada` en 20 s.
- Un momento queda **COMPLETO** cuando todas sus operaciones están en un estado de su `llega_a`. Se anota una vez, en
  la tabla `completitud`. Los canales que no le tocan a la orden no tienen operación y no cuentan.
- Un canal que pasa su plazo sin llegar es un **vencido**: una fila en `alerta` (una por canal y momento) y un WARN
  en el log. **El momento no se cierra**: si lo que faltaba llega tarde, se completa entonces.
- Lo hace un reloj del router (`timer`, cada 2 s), con dos consultas a la base de estado. No hay un agregador en
  memoria ni un repositorio de agregación aparte.
- La lección 26 del plan (el agregador que sobrevive al reinicio, con `JdbcAggregationRepository`) se funde en esta:
  el `verifica` de la 25 reinicia el router a la mitad y la orden se completa igual.

## Por qué

- **Lo juntado ya está en la base**: desde la 15, cada operación y su estado viven en PostgreSQL, y el router es el
  único que escribe ahí. Un agregador guardaría una segunda copia de lo mismo, con su propia forma de perderse o de
  no coincidir con la primera.
- **Sobrevive al reinicio sin hacer nada más**: como no hay nada en memoria, reiniciar el router no pierde lo juntado.
  Era lo que la 26 iba a enseñar con un repositorio; aquí sale de la decisión de la 15.
- **Un reloj, porque lo que no llega no avisa**: un 997 que no viene no dispara ningún evento. Ya que el reloj revisa
  los plazos, también completa lo que está listo, y así lo que quedó a medias por un reinicio se completa en la
  siguiente vuelta.
- **Avisar sin cerrar**: cerrar a la fuerza diría «terminó» de algo que no terminó. El agregador de Camel, al vencer
  su `completionTimeout`, da el grupo por completo; un 997 tardío abriría otro grupo con solo ese acuse.
- **Las reglas en el YAML**, junto a lo que cada momento necesita antes: lo que el router sabe de los canales vive en
  un solo archivo.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| `aggregate` de Camel con el repositorio en memoria | Se pierde al reiniciar; y repite en memoria lo que ya está en la base |
| `aggregate` con `JdbcAggregationRepository` | Guarda la misma información en otras tablas, serializada; dos fuentes de lo mismo |
| Cerrar el momento al vencer | Da por terminado lo que no terminó, y el 997 tardío ya no cambia nada |
| Completar solo cuando llega un evento (sin reloj) | Un vencido no tiene evento; y lo que quedó a medias en un reinicio esperaría al siguiente aviso |
| «Completa» cuando ningún canal sigue pendiente | Un 850 depositado está `aceptada` sin 997, y un canal rechazado contaría como terminado |

## Qué nos haría reabrirla

- Que haya varias copias del router: las dos consultas ya son seguras (cada fila se escribe una vez), pero cada
  copia correría su reloj; con muchas copias convendría que uno solo vigile.
- Que lo juntado no esté en la base (un canal que contesta con datos que hay que combinar, no solo un estado): ahí sí
  haría falta un agregador, y su repositorio tendría que ser persistente.
- Que los plazos cambien por cliente o por hora del día: saldrían del YAML a una tabla.

## Fuentes

- Scatter-Gather, en Enterprise Integration Patterns: https://www.enterpriseintegrationpatterns.com/patterns/messaging/BroadcastAggregate.html
- Aggregator, en Enterprise Integration Patterns: https://www.enterpriseintegrationpatterns.com/patterns/messaging/Aggregator.html
- Process Manager, en Enterprise Integration Patterns: https://www.enterpriseintegrationpatterns.com/patterns/messaging/ProcessManager.html
- Aggregate EIP de Camel (`completionTimeout`): https://camel.apache.org/components/next/eips/aggregate-eip.html
- Componente SQL de Camel (incluye `JdbcAggregationRepository`): https://camel.apache.org/components/next/sql-component.html
- Funciones de JSON en PostgreSQL (`jsonb_each`, contención): https://www.postgresql.org/docs/current/functions-json.html
- Componente `timer` de Camel: https://camel.apache.org/components/next/timer-component.html
