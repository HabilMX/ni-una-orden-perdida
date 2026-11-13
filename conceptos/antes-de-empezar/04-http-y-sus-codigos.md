# HTTP y sus códigos

**Para qué te sirve aquí:** la tienda le manda las órdenes al router por HTTP, el router se las pasa a los servicios por
HTTP, y la API de pedidos contesta con un código que dice qué pasó. Leer ese código es la mitad del oficio.

## Lo que ves en tu terminal

Con el ambiente de la lección 00 arriba:

```bash
curl -i localhost:8081/pedidos/nada     # -i: muestra también los encabezados de la respuesta
```

La primera línea dice `HTTP/1.1 404 Not Found`. Abajo vienen los encabezados (`Content-Type: application/problem+json`)
y al final el cuerpo, que explica el error: `{"type": "…/no-existe", "detail": "No existe el pedido nada", …}`.

## Una imagen de la vida diaria

HTTP es la ventanilla de un trámite. Llevas una solicitud con un sello que dice qué quieres (el **método**) y a qué
oficina va (la **ruta**). Te regresan un folio con un número (el **código**) y, a veces, una hoja con el detalle (el
**cuerpo**).

## El concepto

Una **petición** HTTP tiene un método (`GET` para pedir algo, `POST` para mandar algo nuevo), una ruta (`/pedidos`),
**encabezados** (datos sobre el mensaje, como `Content-Type: application/json` o `Idempotency-Key: 1001`) y, a veces,
un cuerpo. La **respuesta** trae un código de tres cifras, sus encabezados y su cuerpo.

La primera cifra del código dice de qué familia es:

| Familia | Quiere decir | En la práctica |
|---|---|---|
| **2xx** | Salió bien | `201` lo creé · `200` ya lo tenía, no lo dupliqué |
| **4xx** | El error es de quien pidió | `400` te faltó algo · `404` no existe · `422` lo entendí, pero no tiene sentido |
| **5xx** | El error es de quien contesta | El servicio se cayó o no pudo terminar |

La diferencia importa para decidir qué hacer: un **4xx** no se arregla reintentando lo mismo, salvo tres
que dicen «ahora no, prueba después» (`408`, `425` y `429`, lección 20); un **5xx** o una respuesta que nunca llegó a
veces sí.

Un mismo código puede tener varias causas. Por eso la API de la práctica explica cada error en el cuerpo, en el formato
estándar de problemas de HTTP: su campo `type` dice cuál es.

## Lo que necesitas saber para la práctica

- `curl -i` muestra todo; `curl -s -o /dev/null -w '%{http_code}'` solo el código.
- `-H 'Nombre: valor'` agrega un encabezado; `-d @archivo.json` manda un archivo como cuerpo.
- Un 200 del transporte dice que alguien recibió el mensaje, no que el negocio quedó hecho. Lo vas a ver con el socio.

## Compruébalo

```bash
curl -s -o /dev/null -w '%{http_code}\n' localhost:8081/pedidos/nada
```

Con el ambiente arriba debe salir `404`. Si sale `000`, la API no está corriendo: vuelve a la lección 00.

## Si quieres ir más lejos

- HTTP, la semántica y sus códigos (RFC 9110): https://www.rfc-editor.org/rfc/rfc9110
- MDN, códigos de estado (en español): https://developer.mozilla.org/es/docs/Web/HTTP/Reference/Status
- RFC 9457, el formato de problemas de HTTP: https://www.rfc-editor.org/rfc/rfc9457
