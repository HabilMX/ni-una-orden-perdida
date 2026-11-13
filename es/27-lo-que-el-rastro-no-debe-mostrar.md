# Lección 27 — Lo que el rastro no debe mostrar

> **Lo que vas a lograr:** que el rastro de una orden (lo que guarda la base para diagnosticar y lo que enseña el
> recorrido de la 26) no muestre quién es la clienta: ni su nombre, ni su correo, ni su teléfono, ni su calle. Y que,
> tapado, siga sirviendo para entender qué pasó. Al destino, la orden sigue saliendo completa.

**Antes de empezar necesitas:** la lección 26 terminada y el ambiente de la 00 encendido.

## Lo nuevo de hoy, en una frase

En la 26 armaste el recorrido: cualquiera que pueda consultarlo ve cada intento con lo que salió, completo. Eso
incluye a la persona que compró. Hoy la orden de prueba es la 4008, que trae nombre, correo, teléfono y dirección de
una clienta (inventados, como todo en esta práctica). Quien atiende soporte necesita saber qué le pasó a la orden;
**no** necesita saber dónde vive la clienta. Hoy **enmascaras** los datos personales antes de que entren al rastro.

**La imagen:** el estado de cuenta de tu tarjeta en la app del banco. Te muestra `**** **** **** 0428`: lo suficiente
para que sepas cuál es, no lo suficiente para que alguien la use. El banco sí tiene el número completo, y lo usa para
cobrar; lo que tapa es **lo que se enseña**.

---

## 1. Prepara tu carpeta

```bash
cd lecciones/27-lo-que-el-rastro-no-debe-mostrar
cp -R inicio mio
```

Abre `datos/ordenes/tramo-2/orden-4008.json` y señala con un lápiz cada dato que diga **quién es** la clienta. Luego
abre `mio/orden-router/reparto.camel.yaml` y busca en la ruta `intentar` el comentario «Desde la 27». Antes de guardar
en `payload`, el cuerpo pasa por un mapeo nuevo:

```
lo que sale ──→ al canal (completo)
            └─→ mappings/rastro/mascara/v1 ──→ variable rastro ──→ payload (tapado)
```

| Pieza | Qué guarda |
|---|---|
| El cuerpo que va al canal | La orden **completa**: la API, el ERP y el socio la necesitan para entregar |
| `payload.cuerpo` | Lo que salió, **tapado** |
| `intento.sha256` | La huella de lo que salió **de verdad**, sin tapar: sigue sirviendo para reconocer repetidos (la 18) |

La ruta ya está conectada. Tu trabajo es el mapeo: `mio/orden-router/mappings/rastro/mascara/v1/mapeo.jsonata`. Hoy
devuelve todo tal como llega (`$`). Y tiene sus casos dorados en `cases/`, como los mapeos de la 03.

## 2. Predice

Contesta en un papel:

1. En el recorrido de la 4008, ¿qué datos de la clienta verías hoy? ¿Quién puede consultar ese recorrido?
2. Si tapas **todo** lo de la clienta, ¿podrías saber todavía si su correo estaba bien escrito o si el código postal
   era válido?
3. La tabla `orden` también guarda la 4008 completa. ¿Por qué esa sí puede tener los datos?

Mira cómo está hoy:

```bash
./verifica inicio
```

La primera marca sale en rojo: los casos dorados esperan los datos tapados y el mapeo los deja pasar. Y más abajo, la
búsqueda encuentra el nombre, el correo, el teléfono y la calle de la clienta en el rastro y en el recorrido.

## 3. Tapa lo justo

Antes de escribir, abre los tres casos dorados: `cases/orden-con-cliente`, `cases/embarque` y `cases/entrega`. Cada
uno tiene su `entrada.json` y su `esperado.json`. Compáralos y llena esta tabla:

| Dato | Llega | Se guarda | Lo que todavía dice |
|---|---|---|---|
| `cliente.nombre` | `Ana Ejemplo Prueba` | `A. E. P.` | Sus iniciales |
| `cliente.correo` | `ana.prueba@example.com` | `a***@example.com` | Si el dominio era válido |
| `cliente.telefono` | `+52 55 0000 0428` | `*** **** **28` | Los dos últimos dígitos, para reconocerlo |
| `cliente.direccion.calle` | `Calle de Ejemplo 28` | `***` | Nada |
| `recibio` (en una entrega) | `Ana Ejemplo Prueba` | `A. E. P.` | Sus iniciales |
| `tienda`, `renglones`, `cp`, `colonia`… | | Igual | Todo lo que sirve para diagnosticar |

El mapeo tiene dos TODO.

### TODO 1: la función `$mascara`

En JSONata se define una función con `function($v, $modo) { … }` y se guarda en una variable con `:=`. La tuya recibe
un valor y un modo, y regresa el valor tapado. Así se ve su forma, con cada modo como una pregunta `? :`:

```
(
  $mascara := function($v, $modo) {
    $not($exists($v)) ? $v
    : $modo = 'iniciales' ? …
    : $modo = 'correo'    ? …
    : $modo = 'telefono'  ? …
    : '***'
  };
  …   (aquí va el TODO 2)
)
```

La primera línea cuida un caso que se olvida: **si el valor no viene, que siga sin venir.** Un embarque no trae
cliente; sin esa línea, aparecería un `cliente` inventado.

Las funciones de JSONata que necesitas para cada modo:

| Modo | Idea | Funciones |
|---|---|---|
| `iniciales` | Partir el nombre en palabras, tomar la primera letra de cada una con un punto, y unirlas con espacio | `$split($trim($v), ' ')`, `$map(…, function($p) { $substring($p, 0, 1) & '.' })`, `$join(…, ' ')` |
| `correo` | La primera letra, `***@` y lo que va después de la arroba | `$substring($v, 0, 1)`, `$substringAfter($v, '@')`, y `&` para unir textos |
| `telefono` | `*** **** **` y los dos últimos dígitos | `$replace($v, /[^0-9]/, '')` deja solo los dígitos; `$substring(…, -2)` toma los dos últimos |

### TODO 2: aplicarla donde va

JSONata tiene un operador para cambiar **solo una parte** de un documento y dejar el resto igual, el de
transformación: `~> | ruta | { cambios } |`. Se lee «en lo que está en `ruta`, pon estos cambios».

```
$
  ~> | cliente | {
       "nombre": $mascara(nombre, 'iniciales'),
       "correo": $mascara(correo, 'correo'),
       "telefono": $mascara(telefono, 'telefono')
     } |
  ~> | cliente.direccion | { "calle": $mascara(calle, 'todo') } |
  ~> | $ | { "recibio": $mascara(recibio, 'iniciales') } |
```

Son tres transformaciones encadenadas: una en `cliente`, una en `cliente.direccion` y una en la raíz (`$`), para el
`recibio` de una entrega. Lo que no nombras se queda igual: por eso el CP y los renglones pasan sin tocarse.

Prueba tu mapeo antes de levantar nada:

```bash
../../herramientas/probar-mapeo mio/orden-router/mappings/rastro/mascara/v1
```

## 4. Rompe uno a propósito

Con tu mapeo terminado, agrega el código postal a lo que se tapa, en la transformación de `cliente.direccion`:
`"cp": $mascara(cp, 'todo')`. Corre `./verifica`.

Antes, predice: el rastro ahora tapa más. ¿Eso es mejor?

Salen en rojo la primera marca (el caso dorado esperaba el CP) y la última: el rastro ya no dice el CP. Si una entrega
falla porque el CP no existe, el rastro ya no te deja verlo. **Tapar de más también es un error**: el rastro existe
para diagnosticar, y uno que no dice nada no sirve. Un CP solo, sin calle ni nombre, difícilmente señala a la clienta.
Regrésalo como estaba.

## 5. Comprueba

```bash
./verifica
```

`verifica` prueba tus casos dorados, manda la 4008, espera a que sus tres canales contesten y luego **busca** los
cuatro datos de la clienta en el rastro de la base, en el recorrido y en los logs de todos los servicios. Está
terminada cuando ves las siete marcas verdes:

```
✅ El mapeo rastro/mascara/v1 pasa sus casos dorados
✅ Arrancaron el router (que tapa los datos personales del rastro), los tres canales y el sondeo
✅ Control: la orden guardada sí tiene los 4 datos personales (la búsqueda encuentra lo que hay)
✅ En el rastro de la base (payload, intentos, acuses, avisos, conflictos): 0 datos personales
✅ GET /ordenes/4008/recorrido: la orden sí sale, y sin sus datos personales
✅ En los logs: la 4008 aparece (…) veces y sus datos personales 0
✅ Lo tapado sirve para diagnosticar: (…)
```

Mira la tercera. Antes de contar ceros, la verifica busca los mismos cuatro datos donde **sí** tienen que estar, en
la tabla `orden`, y los encuentra. Si no los encontrara ahí, el «0» de las marcas siguientes no probaría nada: podría
ser que la búsqueda estuviera mal escrita. A eso se le llama un **control positivo**: antes de creerle a un cero,
comprueba que la búsqueda sabe encontrar. Y lee el cero con su alcance: «0 datos personales» quiere decir 0
apariciones de los cuatro datos que la verifica busca, en los lugares donde los busca.

**¿No te corre?** `./verifica solucion` prueba la nuestra, y
`diff mio/orden-router/mappings/rastro/mascara/v1/mapeo.jsonata solucion/orden-router/mappings/rastro/mascara/v1/mapeo.jsonata`
te dice en qué son distintas.

| Lo que ves | Qué haces |
|---|---|
| `no pasa sus casos dorados` | Corre `probar-mapeo` como en el paso 3: te dice qué caso y qué campo no coincide |
| El caso `embarque` falla con un `cliente` que no debería estar | Falta la primera línea de `$mascara`: si el valor no viene, que siga sin venir |
| `En el rastro de la base hay … apariciones` | Alguna transformación no se aplica: revisa la ruta de cada `~> | … |` |
| `GET /ordenes/4008/recorrido: … de los 4 datos personales a la vista` | Lo mismo: el recorrido lee de `payload` |
| `Lo que guarda el rastro de la 4008` sin el CP o sin el dominio | Tapaste de más: el CP y el dominio del correo se quedan |
| `El puerto 8080 está ocupado` | Quedaron arriba los servicios de otra corrida: `DONDE=mio docker compose -f compose.yaml down` |

---

## Lo que esto enseña fuera de la práctica

**Se tapa lo que se enseña, no lo que se usa.** Los destinos necesitan los datos completos para entregar: la
paquetería necesita la dirección. El rastro es para diagnosticar, y lo leen más personas de las que deberían conocer a
la clienta. Por eso se tapa ahí y no en el envío.

**Tapar es un diseño, no un borrado.** Las iniciales, el dominio del correo, los dos últimos dígitos y el CP dicen lo
suficiente para reconocer y diagnosticar, y mucho menos que el dato completo. Decidir qué se queda es la parte difícil.
Y tapar campos no es anonimizar: juntos, o con otras fuentes, algunos datos pueden volver a señalar a la persona (el
ejercicio 2 lo explora). Un rastro real necesita además decidir quién lo puede leer y cuánto tiempo se guarda.

**La huella sigue siendo la de lo real.** El rastro guarda lo tapado, pero la huella de la 18 se saca de lo que salió
de verdad. Así se sigue reconociendo un repetido sin guardar el dato.

**Antes de creerle a un cero, busca donde sí está.** Un «0 datos personales» puede ser un rastro limpio o una búsqueda
que no sabe buscar. El control positivo es lo que los distingue, y sirve para cualquier medición que te dé cero.

## Ejercicio: lo que se ve y lo que no

1. Pide el recorrido de la 4008 (`curl -s localhost:8080/ordenes/4008/recorrido`) y llena dos columnas: lo que puedes
   saber de la orden y lo que **no** puedes saber de la clienta.
2. En el `orden-4008.json`, busca otro dato que, junto con los que quedaron sin tapar, podría ayudar a identificar a la
   clienta. ¿Lo taparías? ¿Qué perderías para diagnosticar si lo tapas?
3. Alguien de soporte necesita llamarle a la clienta porque su entrega falló. ¿De dónde debería sacar el teléfono: del
   rastro o de otro lado? ¿Quién debería poder verlo?

---

## Por qué se hizo así

Lo que decidimos en esta lección, con lo que descartamos y qué nos haría cambiar de idea:

- [El rastro se guarda tapado](../decisiones/31-el-rastro-se-guarda-tapado.md)

## Repaso

1. ¿Qué datos de la 4008 son personales, y cuáles no?
2. ¿Por qué se tapa en el rastro y no en lo que va al destino?
3. ¿Qué hace el operador de transformación de JSONata, `~> | ruta | { … } |`?
4. ¿Por qué el CP y el dominio del correo se dejan a la vista?
5. **La pregunta de cierre:** la verifica busca los datos en la tabla `orden` antes de contar ceros en el rastro. ¿Para
   qué sirve eso, y qué pasaría con la prueba si no lo hiciera?

**Siguiente:** en la lección 28 la integración deja de hablar solo por HTTP y archivos: los momentos de la orden
viajan por un tópico, y cada integración los lee con su propio grupo.
