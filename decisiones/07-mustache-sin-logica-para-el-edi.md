# El EDI se arma con una plantilla sin lógica

> **Dónde muerde:** lección 06.

## Qué decidimos

El X12 850 se escribe con una **plantilla Mustache** que solo acomoda datos. Todo cálculo —rellenos de ancho fijo,
fechas, decimales, la cuenta de segmentos, limpiar los separadores dentro de un dato— lo hace antes el mapeo JSONata.
Regla de una línea: **la plantilla no rellena, no cuenta y no decide.**

## Por qué

- **El cálculo vive en un solo lugar**, el mapeo, que tiene casos dorados.
- **La plantilla se parece al documento:** al leerla ves el 850.
- Mustache no evalúa expresiones, así que no deja meter lógica de negocio por accidente.
- Se usa siempre la llave triple `{{{campo}}}`: la doble escapa HTML, y una `&` saldría como `&amp;` dentro del EDI.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| FreeMarker u otra plantilla con lógica | Invita a meter el relleno, y luego las reglas de negocio, en la plantilla |
| Armar todo el texto en JSONata | Funciona, pero el resultado ya no se parece al documento |
| Una biblioteca de EDI | Es lo que sigue en producción con muchos socios; aquí pediría Java |

## Qué nos haría reabrirla

- Que haya muchos socios, cada uno con su guía de implementación: una plantilla no valida esas guías, y entonces
  conviene un traductor de EDI.

## Fuentes

- Mustache: https://mustache.github.io/mustache.5.html
- Componente Mustache de Camel: https://camel.apache.org/components/next/mustache-component.html
