# Lección 03 — Traducir la orden

> **Lo que vas a lograr:** escribir tú el traductor que convierte la orden de la tienda en el pedido de la API,
> comprobarlo con casos de prueba antes de correr la ruta, y descubrir por qué una computadora no suma centavos como
> tú.

**Antes de empezar necesitas:** la lección 02 terminada y el ambiente de la 00 encendido:

```bash
docker compose -f infra/compose.yaml up -d --wait     # desde la carpeta raíz de la práctica
```

## Lo nuevo de hoy, en una frase

En la 02 el traductor ya venía hecho. Hoy lo escribes tú, y la API ahora pide más: en cada partida, su **importe**
(cantidad × precio), y en el pedido, el **total**. Si el total no cuadra **al centavo** con las partidas, la API lo
rechaza.

**El traductor se llama mapeo** y está escrito en **JSONata**, un lenguaje pequeño para transformar un JSON en otro.
Lo vas a leer antes de escribirlo: casi todo se entiende a la primera.

---

## 1. Prepara tu carpeta

```bash
cd lecciones/03-traducir-la-orden
cp -R inicio mio
```

Esta vez **te quedas en la carpeta de la lección**, no entras a `mio/`: la herramienta de pruebas se corre desde aquí.

## 2. Lee el mapeo que ya conoces

Abre `mio/mappings/api/pedido/v1/mapeo.jsonata`. Es el de la 02:

```
{
  "pedido": orden,
  "tienda": tienda,
  "fecha": creada,
  "partidas": [renglones.{
    "posicion": renglon,
    "sku": sku,
    "cantidad": cantidad,
    "precio": precio
  }]
}
```

Se lee así: **a la izquierda, el nombre que quiere la API; a la derecha, de dónde sale en la orden de la tienda.**
`"pedido": orden` quiere decir «el campo `pedido` vale lo que traiga el campo `orden`».

La línea rara es `renglones.{ … }`. El punto quiere decir **«para cada uno»**: para cada renglón de la orden, arma
una partida con estos campos. Los corchetes de afuera aseguran que siempre salga una lista, aunque la orden traiga
un solo renglón.

¿Por qué el archivo vive en una carpeta `v1`? Porque hoy vas a escribir la `v2` **al lado**, sin tocar la v1. Si
algo sale mal, la ruta puede volver a la versión anterior cambiando un solo número.

## 3. Lee los casos dorados

Abre la carpeta `mio/mappings/api/pedido/v2/cases/`. Tiene tres casos, y cada uno es una pareja de archivos:

| Archivo | Qué es |
|---|---|
| `entrada.json` | Una orden de la tienda |
| `esperado.json` | Exactamente lo que tu mapeo tiene que producir con esa orden |

A esto se le llama **casos dorados**: el resultado correcto, escrito antes de programar. Son el control del error de
esta lección: no tienes que preguntarle a nadie si tu mapeo quedó bien, los casos te lo dicen.

Abre `cases/dos-renglones/esperado.json` y compáralo con lo que produce la v1. ¿Qué campos son nuevos?

## 4. Predice

Abre `cases/centavos/entrada.json`. Es la orden 1003, de una papelería:

| Renglón | Cantidad | Precio |
|---|---|---|
| Clip | 3 | 0.10 |
| Liga | 7 | 0.70 |
| Libreta | 1 | 19.99 |

Antes de seguir, escribe en un papel:

1. ¿Cuánto es el importe del clip (3 × 0.10)?
2. ¿Cuál es el total del pedido?

Guarda tus respuestas: las vas a necesitar en el paso 6.

## 5. Escribe la v2

Abre `mio/mappings/api/pedido/v2/mapeo.jsonata`. Solo tiene unas instrucciones y unas llaves vacías `{ }`.

1. **Copia ahí el contenido de la v1**, sin el comentario de arriba.
2. **Agrega el importe** en cada partida. En JSONata la multiplicación es `*`, y dentro de `renglones.{ … }` ya
   estás parado en un renglón, así que `cantidad` y `precio` son los de ese renglón.
3. **Agrega el total** del pedido, después de `"partidas"`. JSONata tiene una función `$sum( … )` que suma una
   lista de números. Pista: `renglones.(cantidad * precio)` produce la lista de importes.

Cuando lo tengas, pruébalo contra los casos dorados, desde la carpeta de la lección:

```bash
../../herramientas/probar-mapeo mio/mappings/api/pedido/v2
```

La herramienta corre tu mapeo con cada `entrada.json` y compara el resultado con su `esperado.json`. Al final dice
`Casos: 3 · pasan … · fallan …`. Si un caso falla, te muestra lo que obtuvo; ábrelo junto a su `esperado.json` y busca la diferencia.

La primera vez tarda unos 20 segundos, porque Camel baja lo que necesita.

## 6. Compara con tu predicción

Si escribiste el importe como `cantidad * precio`, lo más probable es que **el caso `centavos` haya fallado**. Mira
lo que obtuvo:

```
"importe": 0.30000000000000004      ← el clip
"importe": 4.8999999999999995       ← la liga
"total": 25.189999999999998
```

Tú predijiste 0.30, y tenías razón. La computadora no. **No guarda los decimales como tú los escribes:** los guarda
en binario, y 0.10 en binario no tiene un final exacto, igual que 1/3 en decimal es 0.3333… sin fin. Al multiplicar,
se asoma ese pedacito que sobra.

Para una persona, 0.30000000000000004 y 0.30 son lo mismo. **Para la API, no:** compara el total contra la suma de
las partidas al centavo, y si no cuadran rechaza el pedido con un **422**: «El total 25.189999999999998 no cuadra
con las partidas, que suman 25.19». En un sistema de ventas real, un centavo
de diferencia es una factura que no cuadra.

La solución es **redondear a dos decimales** con `$round(numero, 2)`. Aplícala en el importe y en el total, y vuelve
a probar hasta ver:

```
Casos: 3 · pasan 3 · fallan 0
```

¿Ves por qué los casos dorados se escriben antes de programar? Si hubieras comparado «a ojo», 0.30000000000000004 se
ve como 0.30 y el error se habría ido hasta la API.

## 7. Cambia la ruta a la v2

Tu mapeo nuevo existe, pero la ruta todavía usa el viejo. Abre `mio/orden-a-api.camel.yaml` y busca la línea:

```yaml
uri: jsonata:mappings/api/pedido/v1/mapeo.jsonata
```

Cámbiala para que apunte a la v2. Es el único cambio de la ruta.

Si quieres verla correr, haz lo mismo que en la 02: desde `mio/`, arranca la ruta con
`../../../herramientas/camel run orden-a-api.camel.yaml` y en otra terminal manda la orden 1003
(`-d @../../../datos/ordenes/orden-1003.json`). Después pregúntale a la API: `curl localhost:8081/pedidos/1003`.

## 8. Comprueba

Detén la ruta con `Ctrl+C` si la dejaste corriendo y, desde la carpeta de la lección:

```bash
./verifica
```

Está terminada cuando ves las cuatro marcas verdes:

```
✅ El mapeo v2 pasa todos sus casos dorados
✅ La ruta usa la versión 2 del mapeo
✅ La API aceptó la orden 1003 (201)
✅ El pedido quedó con total 25.19
```

Las dos primeras revisan tu trabajo; las dos últimas las confirma **la API**. Si el mapeo pasara sus casos pero la
ruta siguiera en la v1, la API recibiría un pedido sin total.

**¿No te corre?** `./verifica solucion` prueba la nuestra, y
`diff mio/mappings/api/pedido/v2/mapeo.jsonata solucion/mappings/api/pedido/v2/mapeo.jsonata` te dice en qué son
distintas.

| Lo que ves | Qué haces |
|---|---|
| `Casos: 3 · pasan 2 · fallan 1` en `centavos` | Falta redondear: vuelve al paso 6 |
| `No hay casos en …` | Escribiste mal la carpeta: debe terminar en `pedido/v2` |
| `La API no aceptó la orden 1003: llegó 422` | El total no cuadra al centavo: revisa los `$round` del paso 6 |
| `El pedido quedó con total ninguno` | La ruta sigue en la v1, que no manda total: vuelve al paso 7 |
| `El puerto 8080 está ocupado` | Quedó una ruta corriendo: detenla con `Ctrl+C` |

---

## Repaso

1. En `"pedido": orden`, ¿qué lado es la API y qué lado es la tienda?
2. ¿Qué hace el punto en `renglones.{ … }`?
3. ¿Qué es un caso dorado, y por qué se escribe antes del mapeo?
4. ¿Por qué 3 × 0.10 no da exactamente 0.30 en una computadora?
5. ¿Para qué sirve que la v2 viva al lado de la v1 y no encima?

**Siguiente:** en la lección 04 la orden llega a un sistema que no habla JSON: el ERP, que guarda todo en tablas.
