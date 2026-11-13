package mx.habil.practica.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.jboss.logging.Logger;

/**
 * {@code /admin/modo}: el control que usan las lecciones para que la API se porte como un destino con problemas.
 *
 * <p>{@code POST /admin/modo} con {@code {"modo":"normal"|"lento"|"rechaza"|"satura"}} y, opcionales,
 * {@code "espera_ms"} para el modo lento (10000 por omisión), y {@code "veces"} (2) y {@code "retry_after_s"} (3) para
 * el modo satura. {@code GET /admin/modo} dice el modo vigente y cuántos 429 le quedan. Un modo desconocido es un
 * 400.
 */
@Path("/admin/modo")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminResource {

    private static final Logger LOG = Logger.getLogger(AdminResource.class);

    @Inject
    Modo modo;

    @GET
    public Map<String, Object> leer() {
        return Map.of("modo", modo.actual(), "espera_ms", modo.esperaMs(), "quedan_429", modo.quedan429(),
                "retry_after_s", modo.retryAfterS());
    }

    @POST
    public Response cambiar(Map<String, Object> cuerpo) {
        Object pedido = cuerpo == null ? null : cuerpo.get("modo");
        String nuevo = pedido == null ? null : String.valueOf(pedido);
        if (!Modo.valido(nuevo)) {
            return Response.status(400)
                    .entity(Map.of("error", "modo desconocido: " + nuevo + " (normal, lento, rechaza o satura)")).build();
        }
        long espera = Modo.ESPERA_LENTO_MS;
        if (cuerpo.get("espera_ms") instanceof Number n) {
            espera = n.longValue();
        }
        int veces = Modo.VECES_SATURA;
        if (cuerpo.get("veces") instanceof Number n) {
            veces = n.intValue();
        }
        int retryAfter = Modo.RETRY_AFTER_S;
        if (cuerpo.get("retry_after_s") instanceof Number n) {
            retryAfter = n.intValue();
        }
        modo.cambiar(nuevo, espera, veces, retryAfter);
        LOG.infof("modo=%s espera_ms=%d veces=%d retry_after_s=%d", nuevo, espera, veces, retryAfter);
        return Response.ok(leer()).build();
    }
}
