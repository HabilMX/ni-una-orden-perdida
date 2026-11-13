# Un archivo de la bodega se parte en sus embarques, y uno malo no detiene a los demás

> **Dónde aparece:** lección 14.

## El problema

La bodega deja un solo archivo con varios embarques. Si se trata como un mensaje, el router recibe algo que no es
un embarque. Y si uno de los embarques viene mal, no debe arrastrar a los demás: las otras órdenes sí salieron.

## Qué decidimos

El sondeo **parte el archivo con un Splitter** (`split` de Camel con la expresión JSONPath `$.embarques[*]`) y le
pasa cada embarque al router por separado, como si hubiera llegado solo. La opción **`stopOnException: false`**
queda escrita aunque sea la de omisión: si un embarque falla, se anota en la bitácora del sondeo cuál fue y qué
contestó el router, y se sigue con el siguiente. Al terminar, si alguno falló, el archivo completo se aparta a
`.error/`, para que alguien vea qué embarque no pasó.

## Por qué

- **Cada embarque es de una orden distinta**, y cada orden tiene su propio aviso al socio (un 856 por orden).
- **Que uno falle es lo normal en un lote**: una orden que no existe, un renglón mal escrito. Detener el lote
  entero dejaría sin avisar a las órdenes que sí salieron.
- **Escribir la opción, aunque sea la de omisión, es documentar una decisión.** Quien lea la ruta ve que se pensó.
- **El archivo en `.error/` dice «revisa este»**, y la bitácora dice cuál embarque. Lo que ya pasó, ya está en sus
  destinos.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Mandar el lote completo al router y que él lo parta | El router recibiría dos formas distintas para el mismo momento; partir es asunto de quien conoce el archivo |
| `stopOnException: true` | Un embarque malo deja sin procesar a los que venían después: en la lección, 2 de 4 buenos se quedan sin salir |
| Apartar el lote a `.camel/` aunque uno haya fallado | Nadie se entera de que un embarque no pasó |
| Reintentar el lote completo | Reenvía también los que ya pasaron; eso pide antes la idempotencia de la lección 18 |

## Qué nos haría reabrirla

- Que los embarques de un lote tengan que pasar todos o ninguno (por ejemplo, porque la bodega los factura juntos).
- Lotes muy grandes, donde convenga partir sin leer todo el archivo a memoria (la opción `streaming` del Splitter).

## Fuentes

- Splitter de Camel (`stopOnException`, `streaming`): https://camel.apache.org/components/next/eips/split-eip.html
- JSONPath en Camel: https://camel.apache.org/components/next/languages/jsonpath-language.html
- Splitter, de *Enterprise Integration Patterns*: https://www.enterpriseintegrationpatterns.com/patterns/messaging/Sequencer.html
