# CloudEvents en modo binario como sobre

> **Dónde muerde:** lección 07, 08.

## Qué decidimos

Cuando un servicio le pasa la orden a otro, la orden va en el cuerpo tal cual, y el **sobre** va en encabezados
CloudEvents: `ce-specversion`, `ce-type`, `ce-source`, `ce-subject` (la orden) y `ce-id`. El `ce-id` sale de la llave
de negocio y del momento (`orden-1003-creada`).

## Por qué

- **Es una especificación abierta**, no un formato inventado por nosotros.
- **El cuerpo no cambia:** las rutas de las lecciones 02 a 06 siguen trabajando con la orden de siempre.
- **El id es determinístico:** el mismo evento reenviado conserva su id, y la especificación dice que con el mismo
  `source` e `id` se puede tratar como duplicado.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Un sobre JSON propio | Inventa un estándar que nadie más entiende |
| CloudEvents en modo estructurado | Envuelve el cuerpo y obliga a cambiar todos los mapeos anteriores |
| Solo un encabezado X-Correlation-Id | No le da identidad al evento, y la detección de duplicados la necesita |

## Qué nos haría reabrirla

- Que el transporte cambie a uno donde el modo binario no exista (en Kafka sí existe: los mismos atributos van en sus
  encabezados).

## Fuentes

- Especificación CloudEvents: https://github.com/cloudevents/spec/blob/main/cloudevents/spec.md
- CloudEvents sobre HTTP (modo binario): https://github.com/cloudevents/spec/blob/main/cloudevents/bindings/http-protocol-binding.md
