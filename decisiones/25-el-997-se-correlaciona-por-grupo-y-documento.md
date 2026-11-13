# El 997 se correlaciona por el grupo y el documento, y solo él confirma

> **Dónde aparece:** lección 21.

## El problema

El 850 de la 4001 quedó en el SFTP del socio. Eso dice que lo depositamos, no que el socio lo recibió y lo entiende.
Su sistema contesta después, y por otro camino: deja un acuse funcional, el 997, en su carpeta `salida/`. Ese 997 no
trae el número de orden. ¿Cómo sabemos a qué envío contesta? ¿Y qué hacemos si cita algo que no mandamos?

## Qué decidimos

- El canal del socio tiene dos pasos: **`aceptada`** cuando el 850 quedó depositado y **`confirmada`** cuando llega su
  997 de aceptación. Con un 997 de rechazo, la operación pasa a **`rechazada`**, con los errores que señaló el socio.
- El router sondea `salida/` (una sola copia, como la bodega en la ficha 16), traduce cada 997 con su mapeo y lo
  **correlaciona por los números de control que mandamos**: `AK1-02` es el GS06 del grupo (el `numero_control` que
  asigna el router, ficha 19), `AK2-01` y `AK2-02` son el ST01 y el ST02 del documento. El `AK1-01` (PO, SH, QM)
  tiene que corresponder al tipo de documento.
- **El ISA del 997 no se usa para correlacionar**: es el sobre del socio, con su propio número.
- Cada 997 se anota en la tabla `acuse`. Si no corresponde a ninguna operación que esperara su acuse, queda ahí sin
  operación y se avisa con un `WARN`: no se pierde y no cambia nada.
- La API y el ERP no esperan acuse: su «sí» es la respuesta de la llamada, y se quedan en `aceptada`.

## Por qué

- **El 997 acusa grupos y documentos, no intercambios.** Su AK1 nombra al grupo funcional por su GS06 y su AK2 al
  documento por su ST02; así lo muestra el ejemplo público de X12 del 997 parcialmente aceptado.
- **Un número de control por documento** (desde la 15) hace que un 997 apunte a un solo envío: el 850 y el 856 de la
  misma orden no comparten número.
- **Depositado no es recibido.** Un archivo en el SFTP puede quedarse ahí sin que nadie lo procese; solo el acuse dice
  que el socio lo leyó y si lo aceptó.
- **Un acuse sin dueño también es información**: puede ser de otro sistema, de una prueba del socio o de un número que
  se repitió. Se ve en la tabla, no se tira.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Dar por confirmado lo depositado | Es lo que ya sabíamos; no dice si el socio lo procesó |
| Correlacionar por el ISA13 | Es el número del sobre del socio, no el nuestro |
| Correlacionar por el número de orden | El 997 no lo trae: acusa la sintaxis del documento, no su contenido de negocio |
| Esperar el 997 dentro de la misma llamada | Llega segundos u horas después, por otro camino; detener el reparto esperándolo frenaría todo |

## Qué nos haría reabrirla

- Que el socio mande varios documentos por grupo: el ST02 dejaría de ser `0001` en todos, y la correlación tendría que
  buscarlo por documento.
- Que el socio mande un 999 (el acuse de implementación) en vez de un 997: cambia el mapeo, no la correlación.
- Que el acuse no llegue: es la espera con tiempo límite de la lección 25.

## Fuentes

- Ejemplo de X12 de un 997 parcialmente aceptado, con detalle: https://x12.org/examples/004010x363/example-04-basic-functional-acknowledgment-partially-accepted-detail
- Segmento AK1 (X12 004010): https://www.stedi.com/edi/x12-004010/segment/AK1
- Segmento AK2 (X12 004010): https://www.stedi.com/edi/x12-004010/segment/AK2
- Request-Reply y Correlation Identifier, de *Enterprise Integration Patterns*: https://www.enterpriseintegrationpatterns.com/patterns/messaging/CorrelationIdentifier.html
