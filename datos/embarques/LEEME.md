# Embarques de la bodega (lecciones 13 a 17)

La bodega B-NORTE deja un archivo por embarque, o un lote con varios. Todos los datos son inventados.

| Archivo | Qué trae | Lección |
|---|---|---|
| `embarque-4001.json`, `embarque-4002.json` | Un embarque cada uno: la orden, su guía, la paquetería y lo que salió | 13 en adelante |
| `embarque-roto.json` | Un archivo que se cortó a la mitad: no es JSON válido | 13 |
| `lote-bodega-0001.json` | Cinco embarques en un solo archivo (órdenes 4001 a 4005) | 14 |
| `lote-bodega-0002.json` | Cinco embarques; el tercero es de la orden 9999, que la tienda nunca creó | 14 |

Las órdenes 4001 a 4005 están en `datos/ordenes/tramo-2/` y son de franquicias (T-501 y T-502): por eso al socio
le toca su aviso de embarque, el 856.

Antes de correr la 14: el lote 0002 trae cinco embarques y uno viene mal. ¿Qué crees que pasa con los otros cuatro?
Esa es tu predicción.
