# JSON y YAML

**Para qué te sirve aquí:** las órdenes de la tienda llegan en JSON, y las rutas de Camel, los compose y casi toda la
configuración de la práctica se escriben en YAML.

## Lo que ves en tu terminal

```bash
cat datos/ordenes/orden-1001.json
```

Ves algo así:

```json
{
  "orden": "1001",
  "tienda": "T-014",
  "renglones": [
    { "renglon": 1, "sku": "SKU-0001", "cantidad": 2, "precio": 89.5 }
  ]
}
```

## Una imagen de la vida diaria

JSON y YAML son dos formas de llenar el mismo formulario. JSON lo llena con llaves y comillas, como un formulario
impreso con casillas; YAML lo llena con sangrías, como una lista escrita a mano.

## El concepto

**JSON** describe datos con tres piezas: **objetos** entre llaves (`{"llave": valor}`), **listas** entre corchetes
(`[1, 2, 3]`) y valores sueltos: texto entre comillas, números, `true`, `false` y `null`. Es el formato más común entre
sistemas que se hablan por HTTP.

**YAML** describe lo mismo, pero con sangrías en lugar de llaves:

```yaml
orden: "1001"
tienda: T-014
renglones:
  - renglon: 1
    sku: SKU-0001
```

El guion (`-`) marca un elemento de una lista; los dos puntos separan la llave del valor. Las sangrías se hacen con
**espacios, nunca con tabuladores**, y su profundidad dice qué está dentro de qué. Un espacio de más mueve una línea a
otro nivel, y eso cambia su significado.

## Lo que necesitas saber para la práctica

- Una ruta de Camel en YAML se lee de arriba abajo: `from` (de dónde llega), `steps` (qué le pasa) y `to` (a dónde va).
- Cuando un YAML no arranca, lo primero es revisar la sangría de la línea que dice el error y la de arriba.
- `#` empieza un comentario en YAML; JSON no tiene comentarios.
- Un número con decimales como `0.1 + 0.2` no siempre da exacto en una computadora; por eso los importes se redondean
  en el mapeo (lección 03).

## Compruébalo

```bash
python3 -c "import json; json.load(open('datos/ordenes/orden-1001.json')); print('JSON válido')"
```

Desde la raíz de la práctica debe salir `JSON válido`. Si cambias una coma en el archivo y lo corres otra vez, sale un
error que dice en qué línea está el problema.

## Si quieres ir más lejos

- JSON, la especificación (RFC 8259): https://www.rfc-editor.org/rfc/rfc8259
- YAML, la especificación 1.2: https://yaml.org/spec/1.2.2/
