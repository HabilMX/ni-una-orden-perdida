# Lección 11 — No todo va a todos

> **Lo que vas a lograr:** que el router decida, orden por orden, si va al socio o no, según **de qué tienda** viene.
> La API y el ERP siguen recibiendo todas; el socio, solo las suyas.

**Antes de empezar necesitas:** la lección 10 terminada y el ambiente de la 00 encendido (con el ERP recreado en la
10).

## Lo nuevo de hoy, en una frase

En la 10 cada orden iba a los tres destinos. Pero el socio comercial no opera todas las tiendas: solo las
**franquicias**, la T-501 y la T-502. Si le mandas la orden de una tienda propia, le llega un pedido que no es suyo y
que podría surtir. Hoy el canal del socio **mira la orden antes de mandarla**.

**La imagen:** el cartero que reparte en una colonia. No deja todas las cartas en todas las casas: lee la dirección de
cada una y decide.

Este patrón se llama **Content-Based Router**: el destino depende del contenido del mensaje.

| | Multicast (la 10) | Content-Based Router (hoy) |
|---|---|---|
| ¿A quién va? | A todos los destinos, siempre | Solo a los que cumplen una condición |
| ¿Quién decide? | Nadie: la lista es fija | Una regla que lee la orden |

Hoy usas los dos juntos: el multicast sigue repartiendo a los tres canales, y **dentro** del canal del socio una
condición decide si la orden sigue o no.

---

## 1. Prepara tu carpeta

```bash
cd lecciones/11-no-todo-va-a-todos
cp -R inicio mio
```

## 2. Predice

Abre la carpeta `datos/ordenes/lote-10/` en la raíz de la práctica. Tiene diez órdenes y un `LEEME.md`. Sin correr
nada, abre las órdenes, mira su campo `"tienda"` y contesta en un papel:

1. ¿Cuántas de las diez son de franquicia (T-501 o T-502)?
2. Con el router como está hoy, ¿cuántos archivos esperas en el buzón del socio? ¿Y cuando termines?
3. ¿Cuántas órdenes deberían tener la API y el ERP?

Comprueba la de hoy:

```bash
./verifica
```

Salen dos marcas rojas. El socio tiene **los diez** 850, y la API y el ERP también tienen diez. Hoy el canal del socio
manda todo.

## 3. Lee el canal del socio

Abre `mio/orden-router/orden-router.camel.yaml` y busca la ruta `canal-socio`. Ahora tiene dos partes:

| Ruta | Qué hace |
|---|---|
| `canal-socio` | **Decide** si la orden va al socio. Tiene el TODO |
| `al-socio` | **Manda** la orden a int-edi y deja la línea de lo que contestó, como el canal de la 10 |

Separarlas deja la decisión en un solo lugar: si mañana cambia quién es socio, se toca una línea y no la llamada.

## 4. Escribe la condición

En el TODO, cambia el `to: direct:al-socio` por un `choice`. Ya usaste uno en la 09, en el router, para decidir de
dónde salía el hilo:

```yaml
- choice:
    when:
      - expression:
          simple:
            expression: "…tu condición…"
        steps:
          - to:
              uri: direct:al-socio
    otherwise:
      steps:
        # … el canal contesta que no aplica
```

**La condición.** La tienda de la orden ya está guardada en `${variable.tienda}`. En el lenguaje `simple` de Camel,
`in` pregunta si un valor está en una lista separada por comas:

```
${variable.tienda} in 'T-501,T-502'
```

**El otherwise.** Las órdenes que no son de franquicia no van al socio, pero el canal **tiene que decirlo**: un
`setBody` con `{"canal":"socio","resultado":"no aplica"}`. Si el canal se quedara callado, la tienda no sabría si el
router decidió no mandarla o se le olvidó. «No aplica» y «falló» no son lo mismo, y la respuesta lo debe distinguir.

## 5. Compara con tu predicción

```bash
./verifica
```

Ahora el socio tiene cuatro 850, los de las órdenes 2002, 2005, 2007 y 2010, y la tienda recibió «no aplica» en las
otras seis. ¿Acertaste el 4? La API y el ERP siguen con las diez: la condición solo vive en el canal del socio.

Si sale `Socio: … y tiene «850-2002.edi 850-2007.edi»`, tu condición deja fuera a la T-502. Revisa la lista.

## 6. Comprueba

Está terminada cuando ves las cinco marcas verdes:

```
✅ Arrancaron el router y los tres canales
✅ API: tiene las 10 órdenes del lote
✅ ERP: tiene las 10 órdenes REGISTRADO
✅ Socio: solo llegaron los 850 de franquicia (850-2002.edi 850-2005.edi 850-2007.edi 850-2010.edi)
✅ El router le dijo a la tienda «no aplica» en las 6 que no son de franquicia
```

Las marcas de la API, el ERP y el socio cuentan **en el destino**: el registro de la API, las filas del ERP y los
archivos del buzón.

**¿No te corre?** `./verifica solucion` prueba la nuestra, y
`diff mio/orden-router/orden-router.camel.yaml solucion/orden-router/orden-router.camel.yaml` te dice en qué son
distintas.

| Lo que ves | Qué haces |
|---|---|
| El socio tiene los diez | La condición no está, o el `to` quedó fuera del `when` |
| El socio tiene dos | La lista de la condición tiene una sola tienda |
| `Esperaba «no aplica» en 6 respuestas y lo vi en 0` | Falta el `setBody` del `otherwise` |
| `Tu ERP no tiene la columna correlacion` | Recrea el ERP como dice la 10 |

---

## Ejercicio: la regla fuera de la ruta

Hoy la lista de franquicias está **escrita dentro de la ruta**. Si el socio abre otra franquicia, alguien tiene que
editar el YAML, revisarlo y volver a desplegarlo para cambiar un dato del negocio. Muévela a **configuración**: un
valor que se cambia sin tocar la ruta.

**1.** En tu condición, cambia la lista por una variable de entorno con un valor por omisión:

```
${variable.tienda} in '{{env:FRANQUICIAS:T-501,T-502}}'
```

`{{env:FRANQUICIAS:…}}` quiere decir: «usa la variable de entorno `FRANQUICIAS`; si no existe, usa lo que va después
de los dos puntos». Corre `./verifica`: debe seguir en cinco de cinco, porque usa el valor por omisión.

**2.** Ahora cambia la regla **sin tocar la ruta**. Crea `mio/franquicias.yaml` con esto:

```yaml
services:
  orden-router:
    environment:
      FRANQUICIAS: "T-501"
```

Es un archivo que **se suma** al compose de la lección: agrega una variable de entorno al router y no cambia nada más.

**3.** Vacía el buzón del socio, levanta los servicios con los dos archivos, manda el lote y mira el buzón:

```bash
docker compose -f ../../infra/compose.yaml exec socio-sftp sh -c 'rm -f /home/socio/entrada/*'
(cd mio/int-edi && ../../../../herramientas/confiar-en-socio)
DONDE=mio docker compose -f compose.yaml -f mio/franquicias.yaml up -d
for f in ../../datos/ordenes/lote-10/orden-*.json; do
  curl -s -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' -d @$f; echo
done
docker compose -f ../../infra/compose.yaml exec socio-sftp ls /home/socio/entrada
DONDE=mio docker compose -f compose.yaml down
```

Antes de correrlo, predice: ¿cuántos 850 esperas en el buzón, y cuántas respuestas con «no aplica»?

En las respuestas, fíjate solo en la línea del canal `socio`. La API y el ERP ya tenían esas órdenes de tu último
`verifica`, así que sus líneas dicen otra cosa; para el socio, el buzón empezó vacío. Al terminar, corre `./verifica`
para dejar todo como estaba.

**4.** Contesta: ¿qué cambió y qué no? ¿Quién debería poder cambiar `FRANQUICIAS`: el que programa la ruta o el que
administra el negocio?

---

## Repaso

1. ¿Qué diferencia hay entre un Multicast y un Content-Based Router? Da un ejemplo de cuándo usarías cada uno.
2. ¿Qué hace `in` en la condición `${variable.tienda} in 'T-501,T-502'`?
3. ¿Por qué el canal contesta «no aplica» en lugar de quedarse callado?
4. ¿Por qué la decisión vive en `canal-socio` y la llamada en `al-socio`?
5. ¿Qué ganas al mover la lista de franquicias a una variable de entorno?

**Siguiente:** en la lección 12 un servicio deja de ser uno solo: corres varias copias del mismo para que aguante más
órdenes.
