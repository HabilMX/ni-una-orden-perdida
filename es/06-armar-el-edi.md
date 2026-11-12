# Lección 06 — Armar el EDI

> **Lo que vas a lograr:** convertir la orden en el documento que espera el socio comercial, un **X12 850**, y
> comprobar con un lector en español que quedó bien armado. Hoy no hace falta el ambiente: solo Docker.

**Antes de empezar necesitas:** la lección 03 terminada. Esta lección usa la misma idea de mapeo y casos dorados.

## Lo nuevo de hoy, en una frase

La API habla JSON y el ERP habla tablas. El socio comercial habla **EDI**: un formato de texto que las empresas usan
desde antes de que existiera internet para mandarse órdenes de compra, facturas y avisos de embarque. El que usa
este socio se llama **X12**, y la orden de compra es el documento **850**.

Así se ve una orden 850:

```
ISA*00*          *00*          *ZZ*TIENDANORTE    *ZZ*SOCIO01        *261112*1015*U*00401*000001001*0*P*>~
GS*PO*TIENDANORTE*SOCIO01*20261112*1015*1001*X*004010~
ST*850*0001~
BEG*00*SA*1001**20261112~
N1*ST*Tienda T-014*92*T-014~
PO1*1*2*EA*89.50**VN*SKU-0001~
PID*F****Taza blanca~
PO1*2*1*EA*249.00**VN*SKU-0042~
PID*F****Mantel azul~
CTT*2~
SE*9*0001~
GE*1*1001~
IEA*1*000001001~
```

No tiene nombres de campos como el JSON: **cada dato vale por su posición**. Parece ilegible, pero hoy vas a
aprender a leerlo.

---

## 1. Prepara tu carpeta

```bash
cd lecciones/06-armar-el-edi
cp -R inicio mio
```

## 2. Predice

Mira el 850 de arriba. Sin buscar nada, contesta en un papel:

1. Hay un `*` y un `~` en cada línea. ¿Qué crees que separa cada uno?
2. ¿En qué línea crees que va el número de la orden (1001)? ¿Y los renglones?
3. Las palabras `TIENDANORTE` y `SOCIO01` aparecen dos veces. En la primera línea llevan espacios de más al final, y
   en la segunda no. ¿Por qué crees que es?

## 3. Léelo con el lector

La práctica trae un lector que explica un X12 en español. Desde la carpeta de la lección:

```bash
../../herramientas/leer-x12 ../../datos/edi/850-ejemplo.edi
```

Compara con tu predicción:

- **`~` cierra un segmento.** Un segmento es como un renglón del documento, y su nombre son las primeras letras
  (`ISA`, `BEG`, `PO1`…).
- **`*` separa los elementos** de un segmento. El lector los numera: en `BEG*00*SA*1001**20261112`, el elemento 03
  es `1001`, el número de la orden. Dos `*` seguidos quieren decir que ese elemento va vacío.
- **Los renglones** de la orden son los pares `PO1` (cantidad, unidad, precio y producto) y `PID` (descripción).

El documento va **dentro de dos sobres**, como una carta dentro de un paquete:

| Abre | Cierra | Qué es |
|---|---|---|
| `ISA` | `IEA` | El sobre de afuera: quién manda, a quién y un número de control |
| `GS` | `GE` | Un grupo de documentos del mismo tipo (aquí, órdenes de compra) |
| `ST` | `SE` | El documento: la orden 850 |

Cada sobre se cierra repitiendo su número de control, y `SE` además **cuenta** cuántos segmentos tiene el
documento, de `ST` a `SE`. Así el socio sabe si algo se perdió en el camino.

¿Y los espacios de la pregunta 3? El `ISA` es **de ancho fijo**: siempre mide 106 caracteres, y cada dato ocupa un
lugar exacto. `TIENDANORTE` tiene 11 letras y su lugar es de 15, así que se rellena con 4 espacios.

Al final, el lector hace seis revisiones (el ancho del ISA, los números de control, la cuenta del SE…). Si alguna
falla, sale en rojo.

## 4. El mapeo calcula, la plantilla acomoda

La ruta `mio/orden-a-edi.camel.yaml` arma el 850 en dos pasos, cada uno con un solo trabajo:

| Pieza | Su trabajo | Dónde vive |
|---|---|---|
| **El mapeo** (JSONata, como en la 03) | **Calcula** todo: los rellenos de 15, el control de 9 dígitos (1001 → `000001001`), las fechas, los precios con dos decimales, la cuenta del `SE` y el texto limpio | `mio/mappings/edi/850/v1/` · viene hecho |
| **La plantilla** (Mustache) | **Acomoda** lo que ya viene calculado en su lugar del documento. No calcula nada | `mio/templates/edi/850/v1/850.mustache` · **te toca** |

Pruébalo como en la 03, y abre `mio/mappings/edi/850/v1/cases/dos-renglones/esperado.json` para ver qué datos le
llegan a la plantilla:

```bash
../../herramientas/probar-mapeo mio/mappings/edi/850/v1
```

**¿Por qué separar los dos trabajos?** Porque cada uno se prueba por su lado. El cálculo tiene sus casos dorados; la
plantilla solo tiene que poner cada dato en su lugar. Si mezclaras los cálculos dentro de la plantilla, no habría
forma de probarlos.

## 5. Termina la plantilla

Abre `mio/templates/edi/850/v1/850.mustache`. Ya trae el sobre, el inicio del documento y a quién va:

```
ISA*00*          *00*          *ZZ*{{{body.isa.emisor}}}*ZZ*{{{body.isa.receptor}}}*…~
GS*PO*{{{body.gs.emisor}}}*…~
ST*850*{{{body.st.control}}}~
BEG*00*SA*{{{body.beg.orden}}}**{{{body.beg.fecha}}}~
N1*ST*{{{body.tienda.nombre}}}*92*{{{body.tienda.clave}}}~
```

Cada `{{{ … }}}` es un hueco que Mustache llena con un dato del mapeo. Te faltan dos partes. Las instrucciones
completas están en `LEEME.md`, junto a la plantilla.

**Los renglones.** Necesitas un `PO1` y un `PID` por cada renglón, y los renglones están en `body.lineas`. En Mustache,
una **sección** repite lo que tiene adentro una vez por cada elemento de una lista:

```
{{#body.lineas}}
… lo que va por cada renglón, usando {{{n}}}, {{{cantidad}}}, {{{precio}}}, {{{sku}}} y {{{descripcion}}} …
{{/body.lineas}}
```

Dentro de la sección ya estás parado en un renglón, igual que dentro de `renglones.{ … }` en JSONata. Usa el 850
de ejemplo y el lector para saber en qué posición va cada dato.

**El cierre.** Después de los renglones: `CTT` (cuántos renglones), `SE` (la cuenta de segmentos y el control del
`ST`), `GE` e `IEA`. Todos los números ya vienen calculados en `body.ctt`, `body.se.segmentos` y los controles.

Dos reglas:

- **No escribas comentarios dentro de la plantilla:** donde había un comentario, Mustache deja un renglón vacío, y un
  renglón vacío es un error en un X12.
- **Usa llaves triples `{{{ }}}`.** En el paso 7 vas a ver por qué.

## 6. Córrela y léela

Desde `mio/`, pon dos órdenes en `entrada/` y arranca la ruta:

```bash
cd mio
mkdir -p entrada
cp ../../../datos/ordenes/orden-1001.json ../../../datos/ordenes/orden-1005-caracteres.json entrada/
../../../herramientas/camel run orden-a-edi.camel.yaml
```

Cuando veas `Generé el 850 de la orden 1005`, detenla con `Ctrl+C` y lee lo que salió:

```bash
../../../herramientas/leer-x12 salida/850-1001.edi
diff salida/850-1001.edi ../../../datos/edi/850-ejemplo.edi
```

Si el lector da las seis marcas verdes y `diff` no imprime nada, tu 850 de la orden 1001 es idéntico al de ejemplo.

## 7. Los caracteres que rompen un documento

Abre `datos/ordenes/orden-1005-caracteres.json`, en la carpeta raíz de la práctica. Sus dos productos tienen nombres
incómodos:

| Producto en la orden | Cómo sale en el 850 |
|---|---|
| `Taza & plato` | `Taza & plato` |
| `Juego 4*6 ~ cristal` | `Juego 4 6   cristal` |

**Predice:** ¿por qué el segundo nombre cambió? Piensa qué haría el socio al leer `PID*F****Juego 4*6 ~ cristal~`.

El `*` y el `~` son los separadores del documento. Si van dentro de un dato, el socio leería un elemento de más y un
segmento cortado a la mitad. Por eso el mapeo los cambia por espacios antes de que lleguen a la plantilla, y el caso
dorado `caracteres-especiales` comprueba que lo haga.

Ahora el `&`. Cambia **solo** `{{{descripcion}}}` por `{{descripcion}}`, con llaves dobles, y vuelve a correr la
ruta. Mira el `PID` de la orden 1005:

```
PID*F****Taza &amp; plato~
```

Mustache nació para hacer páginas web, y con llaves dobles «protege» el texto como si fuera HTML: el `&` se
convierte en `&amp;`. En una página web es lo correcto. En un X12, el socio recibiría un producto que se llama
«Taza &amp; plato». Las llaves triples le dicen a Mustache: **pon el dato tal cual**. Regresa las llaves triples.

## 8. Comprueba

Desde la carpeta de la lección:

```bash
cd ..
./verifica
```

`verifica` vacía `mio/entrada/` y `mio/salida/`, genera el 850 de las órdenes 1001 y 1005, y los revisa con el
lector. Está terminada cuando ves las cinco marcas verdes:

```
✅ El mapeo del 850 pasa sus casos dorados
✅ El 850 de la 1001 es un X12 bien armado (el lector no encontró fallas)
✅ El 850 de la 1005 es un X12 bien armado (el lector no encontró fallas)
✅ El 850 de la 1001 es idéntico al de ejemplo
✅ El 850 de la 1005 (con & y *) es el esperado
```

**¿No te corre?** `./verifica solucion` prueba la nuestra, y
`diff mio/templates/edi/850/v1/850.mustache solucion/templates/edi/850/v1/850.mustache` te dice en qué son distintas.

| Lo que ves | Qué haces |
|---|---|
| `trae &amp;: usa llaves TRIPLES` | Alguna llave quedó doble: vuelve al paso 7 |
| `no pasa la revisión del lector` | Corre `leer-x12` sobre ese archivo: la revisión en rojo dice qué falta (casi siempre el cierre) |
| `no es idéntico al de ejemplo` | Compáralos con `diff`: suele ser un elemento en otra posición o un `*` de menos |

---

## Lo que aprendimos armando esta lección

Dos errores reales que salieron al construirla, y por qué no llegaron a ti:

- **Una función que «limpia» puede romper el ancho fijo.** JSONata tiene `$trim`, que quita espacios. También junta
  los espacios repetidos en uno solo, y eso habría borrado el relleno del `ISA`. Por eso el mapeo usa `$pad`, que
  rellena, y nunca `$trim` sobre un campo de ancho fijo.
- **Contar a mano falla.** Al escribir el 850 de ejemplo, el `SE` decía 10 segmentos donde había 9. El lector lo
  marcó en rojo. Por eso la cuenta la hace el mapeo y la revisa una herramienta, no una persona.

---

## Por qué se hizo así

Lo que decidimos en esta lección, con lo que descartamos y qué nos haría cambiar de idea:

- [El EDI se arma con una plantilla sin lógica](../decisiones/07-mustache-sin-logica-para-el-edi.md)

## Repaso

1. En un X12, ¿qué separa el `*` y qué separa el `~`?
2. ¿Cuáles son los tres sobres del documento, y qué segmento abre y cierra cada uno?
3. ¿Por qué el `ISA` lleva espacios de relleno?
4. ¿Qué hace el mapeo y qué hace la plantilla? ¿Por qué conviene no mezclarlos?
5. ¿Por qué no puede ir un `*` dentro del nombre de un producto, y qué pasa con un `&` si usas llaves dobles?

**Siguiente:** en la lección 07 el 850 deja de quedarse en `salida/`: viaja al buzón SFTP del socio, con cuidado de
que nunca vea un archivo a medias.
