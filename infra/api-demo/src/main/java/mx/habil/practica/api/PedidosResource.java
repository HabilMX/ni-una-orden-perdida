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
 *   <li><b>201</b> la primera vez que llega un pedido con una llave.</li>
 *   <li><b>200</b> si vuelve a llegar la MISMA llave con el MISMO contenido: es un reenvío y no se duplica.</li>
 *   <li><b>422</b> si llega la misma llave con contenido DISTINTO: quien envía reusó la llave, y no se adivina
 *       cuál de los dos contenidos vale. Es lo que pide el borrador del IETF del encabezado Idempotency-Key.</li>
 *   <li><b>422</b> también si el pedido no trae número, tienda o al menos un renglón válido, o si trae un total que
 *       no cuadra al centavo con sus partidas (o con más de dos decimales).</li>
 * </ul>
 *
 * <p>Los dos 422 se distinguen por el cuerpo, no por el código: todo error sale como
 * {@code application/problem+json} (RFC 9457) y su campo {@code type} dice cuál es ({@link #LLAVE_REUSADA},
 * {@link #PEDIDO_INVALIDO}, {@link #FALTA_LLAVE}, {@link #NO_EXISTE}). La llave se revisa ANTES de validar el
 * contenido: una llave reusada se reporta como tal aunque el contenido nuevo tampoco sea válido. Un pedido inválido
 * no guarda su llave, así que puede reenviarse corregido con la misma.
 *
 * <p>El 409 del borrador («la petición original sigue en proceso») no aparece aquí: esta API contesta al momento y
 * nunca tiene una petición a medias.
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

    /** Identificadores de cada tipo de error (campo {@code type} del problem+json). Son URI de tipo «tag». */
    static final String FALTA_LLAVE = "tag:habil.mx,2026:ni-una-orden-perdida/falta-llave";
    static final String PEDIDO_INVALIDO = "tag:habil.mx,2026:ni-una-orden-perdida/pedido-invalido";
    static final String LLAVE_REUSADA = "tag:habil.mx,2026:ni-una-orden-perdida/llave-reusada";
    static final String NO_EXISTE = "tag:habil.mx,2026:ni-una-orden-perdida/no-existe";

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
            return error(400, FALTA_LLAVE, "Falta la llave de idempotencia", "Falta el encabezado Idempotency-Key");
        }
        Recibido previo = porLlave.get(llave);
        if (previo != null) {
            return yaVista(previo, llave, correlacion, cuerpo);
        }
        String problema = validar(cuerpo);
        if (problema != null) {
            LOG.infof("422 llave=%s correlacion=%s motivo=%s", llave, correlacion, problema);
            return error(422, PEDIDO_INVALIDO, "El pedido no es válido", problema);
        }
        String pedido = String.valueOf(cuerpo.get("pedido"));
        Recibido nuevo = new Recibido(cuerpo, pedido, OffsetDateTime.now().toString());
        previo = porLlave.putIfAbsent(llave, nuevo);
        if (previo != null) {
            // Otra llamada con la misma llave ganó por un instante: se contesta como si hubiera llegado antes
            return yaVista(previo, llave, correlacion, cuerpo);
        }
        porPedido.put(pedido, nuevo);
        LOG.infof("201 llave=%s pedido=%s correlacion=%s", llave, pedido, correlacion);
        return Response.status(201).entity(acuse(nuevo, "creado")).build();
    }

    /** La llave ya llegó antes: mismo contenido es un reenvío (200); otro contenido es una llave reusada (422). */
    private Response yaVista(Recibido previo, String llave, String correlacion, Map<String, Object> cuerpo) {
        if (Objects.equals(previo.cuerpo(), cuerpo)) {
            LOG.infof("200 llave=%s pedido=%s correlacion=%s (reenvío, no se duplica)", llave, previo.pedido(), correlacion);
            return Response.ok(acuse(previo, "ya_existia")).build();
        }
        LOG.infof("422 llave=%s pedido=%s correlacion=%s motivo=llave reusada con otro contenido",
                llave, previo.pedido(), correlacion);
        return error(422, LLAVE_REUSADA, "La llave ya se usó con otro contenido",
                "La llave " + llave + " ya se usó con otro contenido; no se adivina cuál de los dos vale");
    }

    @GET
    @Path("/{pedido}")
    public Response consultar(@PathParam("pedido") String pedido) {
        Recibido r = porPedido.get(pedido);
        return r == null ? error(404, NO_EXISTE, "No existe el pedido", "No existe el pedido " + pedido)
                : Response.ok(r.cuerpo()).build();
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

    /** Un error con la forma de RFC 9457: qué tipo es, un título corto, el código y el detalle de este caso. */
    private static Response error(int estatus, String tipo, String titulo, String detalle) {
        return Response.status(estatus)
                .type("application/problem+json")
                .entity(Map.of("type", tipo, "title", titulo, "status", estatus, "detail", detalle))
                .build();
    }
}
