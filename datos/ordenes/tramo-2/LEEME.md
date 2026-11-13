# Órdenes de la segunda y la tercera entrega (lecciones 13 en adelante)

Ocho órdenes, de la 4001 a la 4008, todas de franquicias (T-501 y T-502): a cada una le toca el canal del socio,
así que se ven sus tres momentos en los tres destinos (creada → 850, embarcada → 856, entregada → 214).
Sus embarques están en `datos/embarques/` y sus entregas en `datos/webhooks/`.

La 4006 (lección 21) pide en su segundo renglón el `SKU-9999`, un producto que el socio no tiene en su catálogo: su
sistema (`socio-bot`) contesta el 850 con un 997 de rechazo que señala ese renglón.

La 4007 (lección 26) es grande: 150 renglones, unos 20 KB tal como la guarda el router (26 KB en este archivo).
Sirve para ver que el rastro guarda el cuerpo completo, sin recortarlo, por grande que sea.

La 4008 (lección 27) trae los datos personales de una clienta: nombre, correo, teléfono y dirección. Son inventados
(el correo es de example.com, el dominio reservado para ejemplos), y sirven para buscarlos en el rastro y en los logs.
