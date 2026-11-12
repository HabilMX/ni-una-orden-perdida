# Lección 05 — Todo o nada

> **Lo que vas a lograr:** que cuando un renglón de la orden esté mal, el ERP no se quede con un pedido a medias:
> o se guarda todo, o no se guarda nada. Lo logras agregando **un solo paso** a la ruta de la 04.

**Antes de empezar necesitas:** la lección 04 terminada y el ambiente de la 00 encendido:

```bash
docker compose -f infra/compose.yaml up -d --wait     # desde la carpeta raíz de la práctica
```

## Lo nuevo de hoy, en una frase

En la 04 tu ruta escribe en el ERP en varios pasos: la cabecera, luego cada renglón y al final el procedimiento.
Si un paso de en medio falla, los anteriores **ya quedaron escritos**. Hoy envuelves todos esos pasos en una
**transacción**: un paquete que el ERP confirma completo o deshace completo.

**La imagen:** una transferencia bancaria. Se le resta a una cuenta y se le suma a la otra. Si el banco hiciera la
resta y luego fallara la suma, el dinero desaparecería. Por eso las dos operaciones van juntas: o pasan las dos, o
no pasa ninguna.

| Palabra | Qué significa |
|---|---|
| **Transacción** | Un grupo de escrituras que la base trata como una sola |
| **Confirmar** (*commit*) | Al terminar bien, la base guarda todo el grupo de una vez |
| **Deshacer** (*rollback*) | Si algo falla, la base borra todo lo que el grupo había escrito, como si nunca hubiera pasado |

---

## 1. Prepara tu carpeta

```bash
cd lecciones/05-todo-o-nada
cp -R inicio mio
```

`inicio/` es la ruta de la 04 ya terminada, con split y `CALL` incluidos, más tres piezas nuevas que vienen hechas.

## 2. Conoce la orden que va a fallar

Abre `datos/ordenes/orden-1004-renglon-invalido.json`, en la carpeta raíz de la práctica. Tiene dos renglones:

| Renglón | Producto | Cantidad | Precio |
|---|---|---|---|
| 1 | Taza blanca | 1 | 89.50 |
| 2 | Mantel azul | **0** | 249.00 |

El ERP no acepta cantidad 0: la tabla `pedido_detalle` tiene una regla (`CHECK (cantidad > 0)`) que rechaza esa
fila. Es como un sistema real, que no deja registrar «cero manteles».

## 3. Predice

Tu ruta escribe primero la cabecera, luego el renglón 1 y luego el renglón 2. Contesta en un papel:

1. Cuando llegue la orden 1004, ¿en qué paso truena la ruta?
2. Al final, ¿qué queda en `pedido_cabecera` y qué queda en `pedido_detalle` para el pedido 1004?
3. ¿Con qué estatus queda la cabecera?

## 4. Compruébalo

Corre `verifica` con la ruta tal como viene, sin cambiar nada:

```bash
./verifica
```

Van a salir **dos marcas rojas**:

```
❌ Quedó la cabecera de la 1004 sin su detalle completo: falta la transacción
❌ Quedaron renglones sueltos de la 1004
```

Pregúntale al ERP qué se quedó:

```bash
docker compose -f ../../infra/compose.yaml exec erp \
  mariadb -uintegracion -ppractica erp -e "SELECT * FROM pedido_cabecera; SELECT * FROM pedido_detalle WHERE pedido='1004';"
```

La cabecera de la 1004 está ahí, en `RECIBIDO`, con un total de 89.50, y tiene **un** renglón: la taza. El mantel
nunca entró y el procedimiento nunca corrió. Es un pedido que el ERP cree que existe, que nadie va a surtir y que
alguien tendrá que encontrar y borrar a mano.

¿Acertaste? Fíjate en que la tienda sí se enteró del error (la ruta le contestó un 500), pero eso no limpia lo que
ya se había escrito.

## 5. Lee las piezas nuevas

Abre `mio/orden-al-erp.camel.yaml`. Arriba, junto a la conexión `erp` que ya conoces, hay dos beans nuevos:

| Bean | Qué hace | En la imagen del banco |
|---|---|---|
| `transacciones` (un `DataSourceTransactionManager`) | Abre, confirma o deshace la transacción sobre la conexión `erp` | El cajero que lleva la operación |
| `todoONada` (una `SpringTransactionPolicy`) | La regla que vas a usar en la ruta: «si ya hay una transacción abierta, súmate; si no, abre una» (`PROPAGATION_REQUIRED`) | La política del banco: las dos operaciones van juntas |

Y en `mio/dependencias.txt` hay dos piezas más que Camel descarga al arrancar: `camel-spring` y `spring-jdbc`. Son
las que saben manejar transacciones. No escribes Java: todo se declara en el YAML.

## 6. Agrega el paso

Busca en la ruta el comentario que dice **TE TOCA**. Ahí va un solo paso:

```yaml
- transacted:
    ref: todoONada
```

`transacted` quiere decir: **desde aquí hasta el final de la ruta, todo pasa dentro de una transacción**, con la
regla `todoONada`. Si cualquier paso de abajo falla, se deshace todo lo que se escribió desde este punto.

Fíjate en **dónde** va: antes de escribir la cabecera. ¿Qué pasaría si lo pusieras después del `INSERT` de la
cabecera?

Este patrón tiene nombre en los EIP: **Transactional Client**. La ruta no solo manda mensajes: le pide al destino
que trate varias escrituras como una sola.

## 7. Comprueba

```bash
./verifica
```

⚠️ Igual que en la 04, `verifica` **vacía las tablas del ERP** antes de probar.

Está terminada cuando ves las seis marcas verdes:

```
✅ El mapeo al ERP pasa sus casos dorados
✅ La ruta arrancó
✅ La orden 1004 (con un renglón de cantidad 0) fue rechazada (500)
✅ No quedó la cabecera de la 1004: se deshizo
✅ No quedó ningún renglón de la 1004
✅ La orden buena 1001 sí quedó completa y REGISTRADO
```

La última importa tanto como las otras: la transacción no debe estorbar a las órdenes buenas.

Vuelve a consultar el ERP como en el paso 4. Ahora de la 1004 no queda nada: ni cabecera, ni taza.

**¿No te corre?** `./verifica solucion` prueba la nuestra, y
`diff mio/orden-al-erp.camel.yaml solucion/orden-al-erp.camel.yaml` te dice en qué son distintas.

| Lo que ves | Qué haces |
|---|---|
| Las dos mismas marcas rojas del paso 4 | El paso `transacted` no está, o está después de la cabecera |
| `La ruta no arrancó` | Revisa la sangría del paso: `transacted` va al mismo nivel que los demás pasos |
| `El puerto 8080 está ocupado` | Quedó una ruta corriendo: detenla con `Ctrl+C` |

---

## Por qué se hizo así

Lo que decidimos en esta lección, con lo que descartamos y qué nos haría cambiar de idea:

- [En el ERP, todo o nada](../decisiones/06-todo-o-nada-en-el-erp.md)

## Repaso

1. ¿Qué es una transacción, y qué significan confirmar y deshacer?
2. Sin transacción, ¿qué quedó en el ERP con la orden 1004, y por qué es un problema?
3. ¿Por qué el paso `transacted` va antes del `INSERT` de la cabecera?
4. ¿Para qué sirve cada uno de los dos beans nuevos?
5. La tienda recibió un 500. ¿Qué debería hacer con esa orden? *(No hay respuesta todavía: en la tercera parte de la
   práctica, «Que no falle», vas a decidir qué se reintenta y qué se aparta.)*

**Siguiente:** en la lección 06 la orden viaja al socio comercial, que no recibe JSON ni tablas: recibe un archivo
EDI, un formato de texto que las empresas usan desde hace décadas para hablar entre ellas.
