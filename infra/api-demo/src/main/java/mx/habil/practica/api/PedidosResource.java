package mx.habil.practica.api;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

/**
 * API demo de pedidos: el destino REST que emula la práctica.
 *
 * <p>No es permisiva a propósito, para que el alumno vea lo que un destino real contesta:
 * <ul>
 *   <li><b>400</b> si falta el encabezado {@code Idempotency-Key}.</li>
 *   <li><b>422</b> si el pedido no trae número, tienda o al menos un renglón válido, o si trae un total que no
 *       cuadra al centavo con sus partidas (o con más de dos decimales).</li>
 *   <li><b>201</b> la primera vez que llega un pedido con una llave.</li>
 *   <li><b>200</b> si vuelve a llegar la MISMA llave con el MISMO contenido: es un reenvío y no se duplica.</li>
 *   <li><b>409</b> si llega la misma llave con contenido DISTINTO: es un conflicto y no se adivina cuál vale.</li>
 * </ul>
 *
 * <p>Si la llamada trae {@code X-Correlation-Id}, la API lo anota en su bitácora junto a cada respuesta
 * ({@code correlacion=...}; un guion si no vino). Así se puede seguir una orden hasta aquí solo con ese id.
 *
 * <p>La lógica vive aquí, en el recurso, porque es un emulador de una sola pieza: no hay dominio que separar.
 * Los pedidos se guardan en memoria; al reiniciar el contenedor se pierden, y eso es intencional para un
 * destino de práctica (cada corrida del curso empieza limpia).
 */
@Path("/pedidos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PedidosResource {

    private static final Logger LOG = Logger.getLogger(PedidosResource.class);

    /** Lo recibido por llave de idempotencia: el cuerpo original y el pedido que generó. */
    private record Recibido(Map<String, Object> cuerpo, String pedido, String recibido) { }

    private final Map<String, Recibido> porLlave = new ConcurrentHashMap<>();
    private final Map<String, Recibido> porPedido = new ConcurrentHashMap<>();

    @POST
    public Response crear(@HeaderParam("Idempotency-Key") String llave,
                          @HeaderParam("X-Correlation-Id") String correlacionRecibida,
                          Map<String, Object> cuerpo) {
        String correlacion = correlacionRecibida == null || correlacionRecibida.isBlank() ? "-" : correlacionRecibida;
        if (llave == null || llave.isBlank()) {
            LOG.infof("400 llave=- correlacion=%s motivo=falta Idempotency-Key", correlacion);
            return error(400, "Falta el encabezado Idempotency-Key");
        }
        String problema = validar(cuerpo);
        if (problema != null) {
            LOG.infof("422 llave=%s correlacion=%s motivo=%s", llave, correlacion, problema);
            return error(422, problema);
        }
        String pedido = String.valueOf(cuerpo.get("pedido"));
        Recibido nuevo = new Recibido(cuerpo, pedido, OffsetDateTime.now().toString());
        Recibido previo = porLlave.putIfAbsent(llave, nuevo);
        if (previo == null) {
            porPedido.put(pedido, nuevo);
            LOG.infof("201 llave=%s pedido=%s correlacion=%s", llave, pedido, correlacion);
            return Response.status(201).entity(acuse(nuevo, "creado")).build();
        }
        if (Objects.equals(previo.cuerpo(), cuerpo)) {
            LOG.infof("200 llave=%s pedido=%s correlacion=%s (reenvío, no se duplica)", llave, pedido, correlacion);
            return Response.ok(acuse(previo, "ya_existia")).build();
        }
        LOG.infof("409 llave=%s pedido=%s correlacion=%s (misma llave, contenido distinto)", llave, pedido, correlacion);
        return error(409, "La llave " + llave + " ya se usó con otro contenido");
    }

    @GET
    @Path("/{pedido}")
    public Response consultar(@PathParam("pedido") String pedido) {
        Recibido r = porPedido.get(pedido);
        return r == null ? error(404, "No existe el pedido " + pedido) : Response.ok(r.cuerpo()).build();
    }

    /** Devuelve el motivo del rechazo, o {@code null} si el pedido es válido. */
    private static String validar(Map<String, Object> cuerpo) {
        if (cuerpo == null) {
            return "El cuerpo está vacío";
        }
        if (!(cuerpo.get("pedido") instanceof String p) || p.isBlank()) {
            return "Falta el número de pedido";
        }
        if (!(cuerpo.get("tienda") instanceof String t) || t.isBlank()) {
            return "Falta la tienda";
        }
        if (!(cuerpo.get("partidas") instanceof List<?> partidas) || partidas.isEmpty()) {
            return "El pedido no trae partidas";
        }
        for (Object o : partidas) {
            if (!(o instanceof Map<?, ?> m) || !(m.get("cantidad") instanceof Number c) || c.intValue() <= 0) {
                return "Hay una partida sin cantidad positiva";
            }
        }
        // Si el pedido trae total (desde la versión 2 del mapeo), tiene que cuadrar al centavo con sus partidas.
        if (cuerpo.containsKey("total")) {
            if (!(cuerpo.get("total") instanceof Number total)) {
                return "El total no es un número";
            }
            java.math.BigDecimal suma = java.math.BigDecimal.ZERO;
            for (Object o : partidas) {
                Map<?, ?> m = (Map<?, ?>) o;
                if (!(m.get("precio") instanceof Number precio)) {
                    return "Hay una partida sin precio";
                }
                suma = suma.add(new java.math.BigDecimal(precio.toString())
                        .multiply(new java.math.BigDecimal(((Number) m.get("cantidad")).toString())));
            }
            java.math.BigDecimal declarado = new java.math.BigDecimal(total.toString());
            if (declarado.scale() > 2 || declarado.compareTo(suma) != 0) {
                return "El total " + declarado.toPlainString() + " no cuadra con las partidas, que suman " + suma.toPlainString();
            }
        }
        return null;
    }

    private static Map<String, Object> acuse(Recibido r, String resultado) {
        return Map.of("pedido", r.pedido(), "resultado", resultado, "recibido", r.recibido());
    }

    private static Response error(int estatus, String mensaje) {
        return Response.status(estatus).entity(Map.of("error", mensaje)).build();
    }
}
