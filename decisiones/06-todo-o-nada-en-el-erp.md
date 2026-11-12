# En el ERP, todo o nada

> **Dónde muerde:** lección 04b.

## Qué decidimos

El pedido entra al ERP en varios pasos (la cabecera, cada renglón y el procedimiento que lo registra) y todos van
dentro de **una transacción**: si uno falla, se deshacen todos. La transacción se declara en el YAML (`transacted`),
sin escribir Java.

## Por qué

- **Un pedido a medias es peor que ninguno.** Una cabecera sin renglones engaña a quien la consulta.
- **Lo cuida la base de datos**, que es quien sabe deshacer, y no un código nuestro que intente «borrar lo que ya
  escribió».
- Es el patrón *Transactional Client* del libro de patrones de integración.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Insertar paso a paso y borrar a mano si algo falla | Si el proceso muere entre el error y el borrado, queda basura |
| Un solo INSERT gigante | El ERP expone tablas y un procedimiento; la integración se adapta a él, no al revés |

## Qué nos haría reabrirla

- Que el destino deje de ser una base de datos con transacciones (por ejemplo, una API): ahí la herramienta es otra
  y llega en lecciones posteriores.

## Fuentes

- Transactional Client en Camel: https://camel.apache.org/components/next/eips/transactional-client.html
