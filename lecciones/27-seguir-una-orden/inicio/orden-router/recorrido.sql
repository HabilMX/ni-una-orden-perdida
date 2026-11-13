-- El recorrido de una orden (lección 27). Lo declara el router al arrancar (recorrido.camel.yaml), y se lee:
--   SELECT * FROM recorrido WHERE orden = '4006'
--
-- Esta versión es un tablero de HOY: una fila por operación, con su último intento. No alcanza para seguir una orden:
-- no cuenta lo que pasó antes (el primer intento que no supo, el que no se envió, el rerun) ni muestra qué salió.
--
--   TODO 1. Una fila por CADA intento de la operación, no solo el último (con LEFT JOIN, para que una operación sin
--           intentos también salga).
--   TODO 2. Lo que salió en cada intento, completo: el cuerpo está en payload, y se une por la huella (sha256).
--   TODO 3. Lo que vino después: los acuses del socio de esa operación (acuse.codigo, en orden), los avisos de
--           alerta de ese canal y momento, y cuándo quedó completo el momento (completitud).
--
-- Deja las columnas con estos nombres: el GET /ordenes/{orden}/recorrido las ordena por momento, canal y n.
DROP VIEW IF EXISTS recorrido;
CREATE VIEW recorrido AS
SELECT p.orden, p.momento, p.canal, p.estado AS estado_canal, p.numero_control, p.correlacion,
       u.n, u.tipo, u.pedido_por, u.motivo, u.version_mapeo, u.desenlace,
       CAST(u.respuesta ->> 'codigo' AS int) AS codigo, u.inicio, u.fin,
       u.tamano, u.sha256, CAST(NULL AS text) AS cuerpo,
       CAST(NULL AS text) AS acuses, CAST(NULL AS text) AS alertas, CAST(NULL AS timestamptz) AS completa
FROM operacion p
LEFT JOIN LATERAL (SELECT * FROM intento i WHERE i.operacion = p.id ORDER BY i.n DESC LIMIT 1) u ON true;
