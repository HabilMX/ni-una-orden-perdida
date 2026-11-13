# El número de control del X12 lo asigna el router

> **Dónde aparece:** lecciones 15 en adelante (antes, en la 06 y la 13, salía del número de orden).

## El problema

Cada documento X12 lleva un número de control en su sobre (ISA13, y el del grupo en GS06). Mientras ese número
salía del número de orden, el 850 y el 856 de la misma orden llevaban el **mismo** número, y un socio que lleva la
cuenta de lo que recibe rechazaría el segundo como repetido. Además, cuando el socio conteste (el 997 de la lección
21), con ese número tenemos que saber a qué documento se refiere.

## Qué decidimos

El número lo asigna el **router**, con una secuencia de la base de estado (`numero_control_seq`), al guardar la
operación del canal del socio. Queda en `operacion.numero_control`, viaja a `int-edi` en el sobre
(`ce-numerocontrol`) y el mapeo (versión 2 del 850 y del 856) lo pone en el ISA y en el GS. Si no llega, el mapeo
se niega: un X12 sin número de control no sale. La secuencia arranca en 100000001 para que todos midan los 9
dígitos que pide el ISA.

## Por qué

- **Uno nuevo por documento**, aunque sean de la misma orden: el 850 y el 856 ya no chocan.
- **Se asigna al guardar, no al enviar.** Si el envío se repite, sale con el mismo número: es el mismo documento.
- **Lo asigna quien lleva el estado.** El router es el único que escribe en la base, y es quien después recibe el
  997 y tiene que encontrar la operación por ese número.
- **Una secuencia de la base** no se repite aunque el router tenga varias copias, cosa que un contador en memoria
  no garantiza.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Seguir usando el número de orden | Dos documentos de la misma orden comparten número |
| Número de orden más un dígito por tipo de documento | Funciona hasta el día en que una orden necesite dos documentos del mismo tipo (una corrección) |
| Que `int-edi` lleve su propio contador | `int-edi` no guarda estado, y con varias copias cada una contaría por su lado |
| La hora del envío como número | Dos envíos en el mismo instante chocan, y un reenvío saldría con otro número |

## Qué nos haría reabrirla

- Que el socio pida un rango de números propio, o que reinicie la cuenta cada año: la secuencia se ajusta a lo que
  diga su guía de implementación.
- Que haya más de un socio: cada uno lleva su propia cuenta y hace falta una secuencia por socio.

## Fuentes

- Secuencias de PostgreSQL (`CREATE SEQUENCE`, `nextval`): https://www.postgresql.org/docs/17/sql-createsequence.html
- Segmento ISA en X12 004010 (ISA13, número de control del intercambio): https://www.stedi.com/edi/x12-004010/segment/ISA
- Conjuntos de transacciones de X12 (850, 856, 214, 997): https://x12.org/products/transaction-sets
