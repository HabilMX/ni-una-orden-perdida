# Plantilla del X12 850 — TE TOCA TERMINARLA

La plantilla ya trae el sobre (ISA, GS), el inicio del documento (ST, BEG) y a quién va (N1). Falta:

1. **Un PO1 y un PID por cada renglón.** Las líneas están en `body.lineas`; dentro de la sección cada renglón trae
   `n`, `cantidad`, `precio`, `sku` y `descripcion`. Mira el 850 de ejemplo (`datos/edi/850-ejemplo.edi`) con
   `herramientas/leer-x12` para ver cómo va cada elemento.
2. **CTT** (cuántos renglones), **SE** (cuántos segmentos y el control del ST), **GE** e **IEA**.

Reglas:
- **Solo acomoda.** Todo ya viene calculado por el mapeo: no calcules nada aquí.
- **Llaves triples `{{{ }}}`.** Con dobles, Mustache cambia `&` por `&amp;` (lo hace pensando en HTML).
- **Sin comentarios dentro de la plantilla:** mustache.java deja un renglón vacío donde estaba el comentario.
