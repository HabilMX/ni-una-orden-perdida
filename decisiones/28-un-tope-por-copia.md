# Un tope por copia hacia el ERP, contando lo que va en curso

> **Dónde aparece:** lección 24.

## El problema

Desde la 24 el router reparte 10 órdenes a la vez, para que un lote no espere orden por orden. int-bd corre en 3
copias (como en la 12), y cada copia acepta lo que le llegue. Con 30 órdenes de golpe y un ERP que tarda 1.5 s en
registrar cada una, el ERP llegó a tener 10 registros a la vez. Un ERP aguanta un número de conexiones de
integración; pasado ese número se pone lento para todos o empieza a rechazar.

## Qué decidimos

- El tope va **en int-bd**, el servicio que habla con el ERP, como un `throttle` al entrar a `/integrar`.
- Con **`mode: ConcurrentRequests`, escrito**: cuenta los pedidos **en curso** (los que entraron y no han
  contestado). El modo por omisión de Camel es `TotalRequests`, que cuenta cuántos entran por periodo.
- El tope es **por copia**: lo que aguanta el ERP entre el número de copias. Con 6 y 3 copias, 2 por copia. Con 5
  copias, el ERP vería hasta 10: al escalar int-bd hay que bajar el tope.
- Lo que pasa del tope **espera su turno**, no se rechaza: el router no lo ve como error ni lo reintenta.
- El ERP mide lo que ve: cada registro anota cuántos había en curso al entrar (`muestra_concurrencia`). El máximo
  de esa columna es la prueba, no la configuración.

## Por qué

- **En int-bd y no en el router**: int-bd es quien sabe del ERP (lo que aguanta, cómo se le habla). Si mañana otro
  servicio también registra pedidos, cada uno trae su tope.
- **En curso y no por segundo**: lo que llena al ERP son los registros abiertos al mismo tiempo. Con 2 por segundo y
  1.5 s cada uno, por la ley de Little habría unos 3 abiertos por copia, y cuando el ERP se pone más lento serían
  más: un tope por periodo no lo frena justo cuando más lo necesita.
- **Esperar y no rechazar**: un rechazo volvería al router como un 5xx, se reintentaría y abriría el cortacircuito
  del ERP, que está bien. La espera solo cuesta tiempo: en la práctica, 30 órdenes pasaron de 6 s a 16 s.
- **Medirlo en el ERP**: la configuración dice lo que debería pasar; la muestra dice lo que pasó.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Un solo consumidor en el reparto, como hasta la 23 | Un lote espera orden por orden, y un destino lento frena a todos los canales |
| Tope por periodo (`TotalRequests`) | No cuenta lo que sigue abierto; si el ERP se pone lento, se le acumulan |
| `rejectExecution: true` | Rechaza lo que pasa del tope: se volvería un reintento o un `no_se_sabe` sin que nada haya fallado |
| Un tope único para todas las copias | Pide algo compartido entre copias (una base o un candado distribuido) para contar; por copia basta con dividir |
| Que el ERP limite sus conexiones (`max_user_connections`) | El ERP lo haría con errores; el tope del lado nuestro lo hace esperando |

## Qué nos haría reabrirla

- Que int-bd escale solo (con más o menos copias según la carga): el tope por copia tendría que ajustarse con el
  número de copias, o pasar a un contador compartido.
- Que el embarque y la entrega también carguen al ERP: hoy el tope está solo en el registro de pedidos; las rutas de
  `/embarque` y `/entrega` tendrían que pasar por el mismo.
- Que el ERP publique cuánto aguanta en cada momento: el tope podría leerse de ahí en vez de ser un número fijo.

## Fuentes

- Throttle EIP de Camel (modos `TotalRequests` y `ConcurrentRequests`): https://camel.apache.org/components/next/eips/throttle-eip.html
- Componente SEDA de Camel (`concurrentConsumers`): https://camel.apache.org/components/next/seda-component.html
- Motor MEMORY de MariaDB (la tabla `en_curso`): https://mariadb.com/kb/en/memory-storage-engine/
- Ley de Little: https://en.wikipedia.org/wiki/Little%27s_law
