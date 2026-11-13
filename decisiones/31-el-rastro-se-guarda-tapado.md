# El rastro se guarda con los datos personales tapados; al destino sale completo

> **Dónde aparece:** lección 28.

## El problema

Desde la 27, el rastro guarda entero lo que salió en cada intento, y lo enseña el recorrido. Una orden trae el
nombre, el correo, el teléfono y la dirección de quien compró. El rastro lo consulta quien diagnostica, que no
necesita saber quién es la clienta, y se guarda más tiempo y en más lugares que la orden misma. Lo que se mira no
debería mostrar a la persona.

## Qué decidimos

- Antes de guardar en `payload`, el router pasa el cuerpo por un mapeo JSONata, `mappings/rastro/mascara/v1`, con una
  función `$mascara`. Al destino sale lo mismo que antes, sin tapar.
- Se tapa por campo, con una forma para cada tipo: nombre en iniciales («A. E. P.»), correo con su primera letra y su
  dominio («a***@example.com»), teléfono con sus dos últimos dígitos («*** **** **28»), calle completa («***»), y
  quien recibe una entrega en iniciales.
- Lo demás queda igual: orden, tienda, renglones, totales, código postal y ciudad.
- La huella del intento (`sha256`) sigue siendo la de lo que salió de verdad, para poder comprobarlo contra el
  destino. El tamaño del `payload` es el de lo guardado.
- La orden guardada (`orden.datos`) se queda completa: es de donde el router lee para enviar y reenviar.
- El mapeo tiene casos dorados (una orden con cliente, una entrega, un embarque sin datos personales), y el `verifica`
  busca los cuatro datos de la clienta de prueba en el rastro, en el recorrido y en los logs.

## Qué se desvió del plan

El plan decía «por canal». El router manda el mismo cuerpo a los tres canales (cada servicio de canal lo traduce
después), así que una regla por canal repetiría la misma tres veces. La regla quedó **por campo**, la misma para
los tres. Si un canal llegara a recibir algo distinto del router, tendría su propio mapeo de máscara, con su versión.

## Por qué

- **Tapar al guardar y no al mostrar**: lo que no se guarda no se puede filtrar después, ni en un respaldo ni en una
  consulta que se olvidó de tapar.
- **Dejar ver lo que sirve**: un correo con otro dominio, un teléfono que cambió de final o un código postal raro
  explican un rechazo. Tapar todo dejaría un rastro que ya no diagnostica.
- **En un mapeo con versión y casos dorados**, como los de los canales: cambiar qué se tapa es un cambio que se
  prueba y se anota, no una línea perdida en una ruta.
- **Con control positivo**: el `verifica` encuentra los cuatro datos en la orden guardada antes de decir que no
  están en el rastro. Un cero sin control podría ser una búsqueda que no ve.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| No guardar el cuerpo en el rastro | El rastro deja de servir para diagnosticar (lección 27) |
| Tapar al leer el recorrido | Lo guardado sigue completo en la base y en sus respaldos |
| Tapar todo el bloque `cliente` | Se pierde lo que explica un rechazo: el dominio del correo, el código postal |
| Cifrar el cuerpo del rastro | Quien diagnostica tendría que descifrarlo, y vería todo; tapar deja ver solo lo útil |
| Tapar también `orden.datos` | El router no podría reenviar ni reprocesar: el destino necesita la orden real |

## Qué nos haría reabrirla

- Que un destino reciba datos personales: su servicio de canal también tendría que cuidarlos en su propio log.
- Que la orden guardada (`orden.datos`) se consulte fuera del router: habría que cifrarla o separar los datos
  personales en otra tabla con su propio acceso.
- Que la ley o el cliente pidan borrar los datos de una persona: con el rastro tapado, el borrado se hace en la orden.
- Que aparezcan campos personales nuevos: entran al mapeo, con su caso dorado, en una versión nueva.

## Fuentes

- OWASP Logging Cheat Sheet (qué datos no van en un registro): https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html
- Operador de transformación de JSONata: https://docs.jsonata.org/other-operators
- Funciones definidas en JSONata: https://docs.jsonata.org/programming
- Componente JSONata de Camel: https://camel.apache.org/components/next/jsonata-component.html
- RFC 2606, dominios reservados para ejemplos (example.com): https://www.rfc-editor.org/rfc/rfc2606
