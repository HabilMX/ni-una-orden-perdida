# Quién recibe qué se decide por el contenido, en un solo lugar

> **Dónde muerde:** lección 10.

## Qué decidimos

No todo va a todos: el 850 solo se le manda al socio si la orden es de una **tienda franquicia** (T-501 y T-502).
La decisión la toma la ruta del canal del socio con un Content-Based Router, y la regla vive en **una sola condición**.
Si la orden no le toca, el canal contesta `{"canal":"socio","resultado":"no aplica"}`.

## Por qué

- **Decidir por el contenido** es el patrón cuando el destino depende de algo de la orden.
- **Una regla en un solo lugar** se cambia sin buscarla por todas partes; el siguiente paso es sacarla a configuración
  (ejercicio 10.3).
- **«No aplica» no es silencio:** quien lee la respuesta sabe que se decidió no mandarla, no que se olvidó.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Mandar todo a todos y que el socio descarte | Le llegan documentos que no son suyos, y eso puede ser un problema de confidencialidad |
| Repetir la condición en varios servicios | Un cambio en la regla obliga a tocar varios lugares, y tarde o temprano no coinciden |

## Qué nos haría reabrirla

- Que la regla de quién es socio cambie seguido o la decida alguien de negocio: entonces vive en configuración o en un
  catálogo, no en la ruta.

## Fuentes

- Content-Based Router (EIP): https://www.enterpriseintegrationpatterns.com/patterns/messaging/ContentBasedRouter.html
- Choice (Content-Based Router) en Camel: https://camel.apache.org/components/next/eips/choice-eip.html
