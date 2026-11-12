# La orden sale a todos los canales, y el router cuenta lo que pasó

> **Dónde aparece:** lección 10.

## El problema

Si un canal se cae y el router contesta «salió a todos», la tienda cree algo que no pasó.

## Qué decidimos

El router manda la misma orden **a la vez** a sus canales (Multicast en paralelo). Cada canal es una ruta pequeña
que **siempre** deja una línea con lo que dijo su servicio —`{"canal":"erp","codigo":200}`— o, si no contestó,
`{"canal":"erp","error":"…"}`. El router le contesta a la tienda la lista de esas líneas.

## Por qué

- **Que un canal falle no detiene a los otros:** en la 10 apagas int-bd y la API y el socio reciben la orden igual.
- **El router no inventa resultados.** Contestar «salió a los tres» sin saberlo es mentir; la lista dice lo que de
  verdad pasó.
- **Una respuesta nunca debe llevar la orden con datos del cliente** por accidente: sin la ruta de canal, un canal caído
  devolvía el cuerpo original, con nombre y correo.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Llamar a los canales uno tras otro | Funciona, pero tarda la suma de los tres y un canal lento atrasa a los demás |
| Contestar un texto fijo | Dice lo que esperábamos, no lo que pasó |
| Parar todo al primer error | Un ERP caído dejaría sin la orden a la API y al socio |

## Qué nos haría reabrirla

- Que la tienda necesite una respuesta inmediata aunque los canales tarden: entonces el router contesta «recibido» al
  guardar la orden y los canales siguen por su cuenta (desde la lección 15, con la base de estado).

## Fuentes

- Multicast en Camel: https://camel.apache.org/components/next/eips/multicast-eip.html
- En el libro, mandar a una lista fija de destinos es un caso de Recipient List; Camel lo llama Multicast.
  Recipient List (EIP): https://www.enterpriseintegrationpatterns.com/patterns/messaging/RecipientList.html
