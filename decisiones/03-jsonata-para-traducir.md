# JSONata para traducir entre formatos

> **Dónde muerde:** lección 03.

## Qué decidimos

Toda traducción de JSON a JSON (la orden de la tienda al pedido de la API, a las filas del ERP o a los datos del 850)
vive en un **archivo JSONata**, con versión en su ruta: `mappings/<destino>/<mensaje>/<versión>/mapeo.jsonata`.

## Por qué

- **Expresa lo que hace falta** en un solo lenguaje: aritmética (importes, totales), condiciones, listas y formato
  (rellenos, fechas, decimales).
- **Separa el flujo de la traducción.** La ruta dice *a dónde* va la orden; el mapeo dice *cómo* se ve al llegar.
- **Se prueba sola**, en el navegador o con `herramientas/probar-mapeo`, sin levantar nada.
- Camel lo trae como componente, con soporte en modo nativo.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| JSLT | Sólida y expresa lo mismo; la encuentras en menos lugares |
| JOLT | No tiene aritmética ni condiciones reales |
| jq | Muy conocido en la terminal, pero sería un segundo lenguaje sin aportar nada |
| Expresiones dentro de la ruta | Mezcla el flujo con la traducción: la ruta crece y el mapeo deja de poder probarse solo |

## Qué nos haría reabrirla

- Que el puerto de JSONata que usa Camel deje de mantenerse o se aleje de la especificación.
- Que un destino pida algo que JSONata no exprese.

## Fuentes

- JSONata: https://jsonata.org/
- Componente JSONata de Camel: https://camel.apache.org/components/next/jsonata-component.html
