# El recorrido es una vista por número de orden, y los cuerpos van enteros aparte

> **Dónde aparece:** lección 27.

## El problema

Alguien pregunta «¿qué pasó con la 4006?». La respuesta está repartida: la operación de cada canal, cada intento
(los que no supieron, los que no se enviaron, el rerun de una persona), el 997 del socio, el aviso de vencido y si
el momento quedó completo. El log lo cuenta a pedazos, mezclado con todo lo demás, recorta los cuerpos grandes y se
va con el contenedor. Y del intento solo guardábamos la huella y el tamaño de lo que salió, no lo que salió.

## Qué decidimos

- Lo que sale en cada intento se guarda **completo** en la tabla `payload`, una fila por huella (sha256), antes de
  enviar. El intento la cita por su `sha256`. La columna es `text`: sin largo máximo.
- El recorrido es una **vista**, `recorrido`: una fila por intento de cada operación, con el estado del canal, el
  intento (tipo, quién lo pidió y por qué, versión del mapeo, desenlace, código), el cuerpo, los acuses del socio,
  los avisos y cuándo quedó completo el momento.
- Se consulta por la **llave de negocio**, el número de orden: `SELECT * FROM recorrido WHERE orden = '4006'`, o
  `GET /ordenes/4006/recorrido` en el router, que devuelve lo mismo en JSON y en orden (momento, canal, intento).
- La vista la declara el router al arrancar, desde `recorrido.sql`: vive junto al código que la usa.

## Por qué

- **Una consulta, no una búsqueda**: todo lo que el router sabe de una orden ya está en la base desde la 15; la
  vista solo lo junta en el orden en que pasó.
- **Por número de orden**, porque es lo que trae quien pregunta. El hilo (`correlacion`) sirve para seguir una
  petición entre servicios; la orden, para seguir lo que le pasó a lo largo de días y de varios momentos.
- **El cuerpo entero y aparte**: para diagnosticar hay que ver lo que salió, no un resumen. Aparte, para que el
  intento siga siendo una fila chica y para que un reintento que manda lo mismo no lo guarde dos veces.
- **La huella como llave** permite comprobar que el cuerpo guardado es el que se mandó: se vuelve a calcular y se
  compara con la del intento.
- **Todos los intentos**: el último dice cómo está; la historia dice por qué. El `no_enviado` y el rerun manual son
  justo lo que se busca al diagnosticar.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Buscar en el log | Recorta los cuerpos grandes, mezcla órdenes y se pierde al recrear el contenedor |
| Una fila por operación con su último intento | Es un tablero de hoy: no cuenta la historia que se busca al diagnosticar |
| El cuerpo dentro de `intento` | Cada reintento lo repetiría; y la tabla que más se consulta crecería con lo que menos se lee |
| Una columna con largo máximo (`varchar(n)`) | Recorta en silencio lo que pase del largo, justo en las órdenes grandes |
| Guardar el cuerpo solo cuando falla | Lo que salió bien también se diagnostica: un «aceptado» con datos mal mapeados |

## Qué nos haría reabrirla

- Que los cuerpos pesen tanto que la base crezca de más: se guardarían en un almacén de objetos, y `payload` solo
  diría dónde, con la misma huella para comprobarlos.
- Que haya que guardar también lo que contestó cada destino, completo: iría a la misma tabla, por su huella.
- Que el recorrido lo consulte gente fuera del equipo: los datos personales del cuerpo tendrían que enmascararse
  antes de guardarse (lección 28).

## Fuentes

- Message History, en Enterprise Integration Patterns: https://www.enterpriseintegrationpatterns.com/patterns/messaging/MessageHistory.html
- Message Store, en Enterprise Integration Patterns: https://www.enterpriseintegrationpatterns.com/patterns/messaging/MessageStore.html
- Tipos de texto en PostgreSQL (`text` sin largo máximo, hasta cerca de 1 GB por valor): https://www.postgresql.org/docs/current/datatype-character.html
- `CREATE VIEW` en PostgreSQL: https://www.postgresql.org/docs/current/sql-createview.html
- Componente SQL de Camel (`useMessageBodyForSql`): https://camel.apache.org/components/next/sql-component.html
