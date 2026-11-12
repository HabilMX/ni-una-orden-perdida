# Plantilla del X12 850

- **Solo acomoda.** Todo dato viene ya calculado por el mapeo (`mappings/edi/850/v1/mapeo.jsonata`): rellenos,
  fechas, precios con dos decimales, la cuenta de segmentos y el texto limpio.
- **Llaves triples `{{{ }}}`.** Con dobles, Mustache cambia `&` por `&amp;` (lo hace pensando en HTML) y el EDI
  saldría mal.
- **`body.`** es la orden ya traducida. Dentro de la sección de las líneas (`{{#body.lineas}} … {{/body.lineas}}`),
  cada campo es de un renglón.
- **Sin comentarios dentro de la plantilla:** mustache.java deja un renglón vacío donde estaba el comentario, y en un
  X12 eso sobra. Por eso la explicación vive en este archivo.
