-- El recorrido de una orden (lección 26): una fila por intento de cada operación, con lo que salió completo.
-- Lo declara el router al arrancar (recorrido.camel.yaml). Se lee: SELECT * FROM recorrido WHERE orden = '4006'.
--
--   orden · momento · canal       qué se tenía que hacer (operacion)
--   estado_canal                  cómo quedó ese canal hoy
--   n · tipo · pedido_por · motivo · version_mapeo · desenlace · codigo · inicio · fin
--                                 cada intento (intento), también los que no salieron bien y los manuales
--   tamano · sha256 · cuerpo      lo que salió en ese intento, completo y con los datos personales tapados (payload;
--                                 desde la 27). La huella es la de lo que salió de verdad; tamano, el del guardado.
--   acuses                        lo que dijo el socio de esa operación, en orden (A aceptado, R rechazado)
--   alertas · completa            si el momento se venció en ese canal, y cuándo quedó completo
--
-- Una operación que todavía no tiene intentos sale igual, con los campos del intento vacíos (LEFT JOIN).
DROP VIEW IF EXISTS recorrido;
CREATE VIEW recorrido AS
SELECT p.orden, p.momento, p.canal, p.estado AS estado_canal, p.numero_control, p.correlacion,
       i.n, i.tipo, i.pedido_por, i.motivo, i.version_mapeo, i.desenlace,
       CAST(i.respuesta ->> 'codigo' AS int) AS codigo, i.inicio, i.fin,
       i.tamano, i.sha256, d.cuerpo,
       (SELECT string_agg(a.codigo, ',' ORDER BY a.id) FROM acuse a WHERE a.operacion = p.id) AS acuses,
       (SELECT string_agg(l.tipo || ' en ' || l.estado, ',' ORDER BY l.id) FROM alerta l
         WHERE l.orden = p.orden AND l.momento = p.momento AND l.canal = p.canal) AS alertas,
       c.completa
FROM operacion p
LEFT JOIN intento i ON i.operacion = p.id
LEFT JOIN payload d ON d.sha256 = i.sha256
LEFT JOIN completitud c ON c.orden = p.orden AND c.momento = p.momento;
