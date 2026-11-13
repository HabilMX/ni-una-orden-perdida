# Guardar antes de contestar

> **Dónde aparece:** lección 15 (y desde ahí, en todo lo que recibe el router).

## El problema

Hasta la lección 14, el router contestaba cuando los tres canales terminaban, y lo que sabía de cada orden vivía en
su memoria. Si se caía a la mitad, nadie sabía qué canal ya la tenía y cuál no. Y quien mandó la orden se quedaba
esperando a que el destino más lento contestara.

## Qué decidimos

Cuando llega un momento de la orden (creada, embarcada, entregada), el router:

1. **lo guarda** en su base de estado (PostgreSQL): la orden en `orden` y **una operación por canal** en
   `operacion`, en `pendiente`, **en una sola transacción**;
2. **contesta `202 Accepted`** («recibida») en cuanto guardó, y no antes;
3. **reparte después**, por una cola interna (`seda`), leyendo de la base lo que tiene que mandar. Cada vez que
   manda algo a un canal escribe un **intento** (`intento`) **antes** de enviar, con la huella (sha256) y el tamaño
   de lo que sale; al tener respuesta anota la versión del mapeo y el desenlace.

Solo el router escribe en esa base. Los canales no guardan estado.

## Por qué

- **El 202 es una promesa: «ya lo tengo y no se pierde».** Solo se puede prometer si ya está escrito fuera del
  proceso. Si la base no contesta, el router no contesta 202, y quien mandó sabe que debe volver a intentar.
- **Una transacción, o queda todo o nada.** Una orden sin sus operaciones sería una orden que nadie reparte.
- **El reparto ya no depende de que el router siga vivo.** Lo pendiente está en la base, con su canal; en la
  lección 23 una sola puerta lo reprocesa.
- **El destino lento ya no detiene a quien manda.** Con el ERP caído, la tienda recibe su 202 y la operación del
  ERP queda guardada sin aceptar: se sabe exactamente qué falta.
- **El intento antes de enviar** deja constancia aunque el router se caiga justo después de mandar: en la base
  queda «lo intenté y no sé cómo me fue». Por qué eso importa se ve en la lección 20.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Contestar cuando los canales terminen (como hasta la 14) | Quien manda espera al canal más lento, y si el router se cae a la mitad no queda registro |
| Contestar 202 y guardar después | El 202 promete algo que todavía no es cierto: si el router se cae antes de guardar, la orden se pierde y quien la mandó cree que llegó |
| Una cola en memoria sin base | Al reiniciar el router se pierde lo encolado |
| Que cada canal guarde lo suyo | Ya no hay un solo lugar que diga cómo va la orden; y cuatro escritores sobre lo mismo se pisan |
| Un broker de mensajes desde ya | Es la Parte VI (lecciones 28 a 30). Aquí se ve primero la idea con una base que el alumno ya sabe consultar |

## Qué nos haría reabrirla

- Que el volumen haga de la base un cuello de botella: ahí entra el broker (lección 28) con el outbox (lección 30).
- Que quien manda necesite saber en el momento si el destino aceptó: entonces no basta un 202 y hace falta otra forma
  de contestar (por ejemplo, avisarle después por su propio webhook).

## Fuentes

- HTTP, `202 Accepted` (RFC 9110, sección 15.3.3): https://www.rfc-editor.org/rfc/rfc9110.html
- Guaranteed Delivery, de *Enterprise Integration Patterns*: https://www.enterpriseintegrationpatterns.com/patterns/messaging/GuaranteedMessaging.html
- Componente SQL de Camel: https://camel.apache.org/components/next/sql-component.html
- Componente SEDA de Camel (la cola interna): https://camel.apache.org/components/next/seda-component.html
