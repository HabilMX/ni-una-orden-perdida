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
import java.util.LinkedHashMap;
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
 * <p>Desde la versión 1.3.0 el pedido tiene una vida: {@code CREADO → EMBARCADO → ENTREGADO}. La bodega y la
 * paquetería la avanzan con {@code POST /pedidos/{pedido}/eventos} (ver {@link #registrarEvento}), y el {@code GET}
 * devuelve el pedido con su {@code estatus} y su {@code guia}. Los eventos también exigen {@code Idempotency-Key}.
 *
 * <p>Desde la 1.4.0, {@code POST /admin/modo} hace que crear un pedido sea lento o se rechace por negocio (ver
 * {@link Modo}): así las lecciones 19 y 20 tienen un destino que tarda y uno que dice que no. Desde la 1.5.0 también
 * puede contestar {@code 429 Too Many Requests} con {@code Retry-After} (modo satura): un destino que dice «ahora no,
 * prueba después», que no es lo mismo que un «no».
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
    static final String EVENTO_INVALIDO = "tag:habil.mx,2026:ni-una-orden-perdida/evento-invalido";
    static final String FUERA_DE_ORDEN = "tag:habil.mx,2026:ni-una-orden-perdida/fuera-de-orden";
    static final String GUIA_DISTINTA = "tag:habil.mx,2026:ni-una-orden-perdida/guia-distinta";
    static final String RECHAZADO_POR_NEGOCIO = "tag:habil.mx,2026:ni-una-orden-perdida/rechazado-por-negocio";
    static final String SATURADA = "tag:habil.mx,2026:ni-una-orden-perdida/saturada";

    /** Los estatus por los que pasa un pedido, en este orden. */
    static final String CREADO = "CREADO";
    static final String EMBARCADO = "EMBARCADO";
    static final String ENTREGADO = "ENTREGADO";

    /** Lo recibido por llave de idempotencia: el cuerpo original y el pedido que generó. */
    private record Recibido(Map<String, Object> cuerpo, String pedido, String recibido) { }

    /** Dónde va el pedido: su estatus y la guía con la que salió (vacía hasta que se embarca). */
    private record Avance(String estatus, String guia, String paqueteria) { }

    /** Un evento recibido por llave: a qué pedido iba y con qué cuerpo, para reconocer un reenvío. */
    private record Evento(String pedido, Map<String, Object> cuerpo, String recibido) { }

    private final Map<String, Recibido> porLlave = new ConcurrentHashMap<>();
    private final Map<String, Recibido> porPedido = new ConcurrentHashMap<>();
    private final Map<String, Avance> avancePorPedido = new ConcurrentHashMap<>();
    private final Map<String, Evento> eventosPorLlave = new ConcurrentHashMap<>();

    /** El modo de la API (normal, lento, rechaza o satura): lo cambia {@code POST /admin/modo}. Solo afecta a crear. */
    @jakarta.inject.Inject
    Modo modo;

    @POST
    public Response crear(@HeaderParam("Idempotency-Key") String llave,
                          @HeaderParam("X-Correlation-Id") String correlacionRecibida,
                          Map<String, Object> cuerpo) {
        String correlacion = correlacionRecibida == null || correlacionRecibida.isBlank() ? "-" : correlacionRecibida;
        if (llave == null || llave.isBlank()) {
            LOG.infof("400 llave=- correlacion=%s motivo=falta Idempotency-Key", correlacion);
            return error(400, FALTA_LLAVE, "Falta la llave de idempotencia", "Falta el encabezado Idempotency-Key");
        }
        if (Modo.RECHAZA.equals(modo.actual())) {
            LOG.infof("422 llave=%s correlacion=%s motivo=rechazado por negocio (modo rechaza)", llave, correlacion);
            return error(422, RECHAZADO_POR_NEGOCIO, "El pedido se rechazó por una regla de negocio",
                    "La API está en modo rechaza: este pedido no se registra, y repetirlo da la misma respuesta");
        }
        if (modo.tocaUn429()) {
            int retryAfter = modo.retryAfterS();
            LOG.infof("429 llave=%s correlacion=%s motivo=saturada (modo satura, quedan %d) retry_after=%d",
                    llave, correlacion, modo.quedan429(), retryAfter);
            // Retry-After en segundos (RFC 9110, sección 10.2.3): cuánto pide el destino que se espere antes de repetir
            return Response.fromResponse(error(429, SATURADA, "Demasiadas peticiones",
                    "La API está en modo satura: este pedido no se registra; repítelo en " + retryAfter + " s"))
                    .header("Retry-After", String.valueOf(retryAfter)).build();
        }
        if (Modo.LENTO.equals(modo.actual())) {
            esperar(modo.esperaMs());
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
        avancePorPedido.put(pedido, new Avance(CREADO, null, null));
        LOG.infof("201 llave=%s pedido=%s correlacion=%s", llave, pedido, correlacion);
        return Response.status(201).entity(acuse(nuevo, "creado")).build();
    }

    /** El modo lento: espera ANTES de registrar, así que quien corte la llamada no sabe que sí quedó. */
    private static void esperar(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

    /**
     * El pedido como llegó, más dónde va: {@code estatus} (CREADO, EMBARCADO o ENTREGADO), {@code guia} y
     * {@code paqueteria} (vacías hasta que se embarca).
     */
    @GET
    @Path("/{pedido}")
    public Response consultar(@PathParam("pedido") String pedido) {
        Recibido r = porPedido.get(pedido);
        if (r == null) {
            return error(404, NO_EXISTE, "No existe el pedido", "No existe el pedido " + pedido);
        }
        Avance a = avancePorPedido.get(pedido);
        Map<String, Object> vista = new LinkedHashMap<>(r.cuerpo());
        vista.put("estatus", a.estatus());
        vista.put("guia", a.guia());
        vista.put("paqueteria", a.paqueteria());
        return Response.ok(vista).build();
    }

    /**
     * Un evento de la vida del pedido. Dos tipos:
     * <ul>
     *   <li>{@code {"tipo":"embarcado","guia":"…","paqueteria":"…","fecha":"…"}}: la bodega lo entregó a la
     *       paquetería. Solo un pedido CREADO puede embarcarse.</li>
     *   <li>{@code {"tipo":"entregado","guia":"…","fecha":"…"}}: la paquetería lo entregó. Solo un pedido
     *       EMBARCADO, y con la MISMA guía con la que salió.</li>
     * </ul>
     * Contesta 201 la primera vez, 200 el reenvío (misma llave y mismo contenido) y 422 si la llave se reusó con
     * otro contenido. Si no existe el pedido, 404. Si el evento no toca todavía (una entrega sin embarque, o un
     * embarque repetido con otra llave), 422 {@link #FUERA_DE_ORDEN}; si la guía no coincide, 422
     * {@link #GUIA_DISTINTA}. Ninguno de los rechazos guarda la llave: el mismo evento puede volver a mandarse
     * cuando ya toque.
     */
    @POST
    @Path("/{pedido}/eventos")
    public Response registrarEvento(@PathParam("pedido") String pedido,
                                    @HeaderParam("Idempotency-Key") String llave,
                                    @HeaderParam("X-Correlation-Id") String correlacionRecibida,
                                    Map<String, Object> cuerpo) {
        String correlacion = correlacionRecibida == null || correlacionRecibida.isBlank() ? "-" : correlacionRecibida;
        if (llave == null || llave.isBlank()) {
            LOG.infof("400 evento llave=- pedido=%s correlacion=%s motivo=falta Idempotency-Key", pedido, correlacion);
            return error(400, FALTA_LLAVE, "Falta la llave de idempotencia", "Falta el encabezado Idempotency-Key");
        }
        Evento previo = eventosPorLlave.get(llave);
        if (previo != null) {
            return eventoYaVisto(previo, llave, pedido, correlacion, cuerpo);
        }
        if (!porPedido.containsKey(pedido)) {
            LOG.infof("404 evento llave=%s pedido=%s correlacion=%s motivo=no existe", llave, pedido, correlacion);
            return error(404, NO_EXISTE, "No existe el pedido", "No existe el pedido " + pedido);
        }
        String problema = validarEvento(cuerpo);
        if (problema != null) {
            LOG.infof("422 evento llave=%s pedido=%s correlacion=%s motivo=%s", llave, pedido, correlacion, problema);
            return error(422, EVENTO_INVALIDO, "El evento no es válido", problema);
        }
        String tipo = (String) cuerpo.get("tipo");
        String guia = (String) cuerpo.get("guia");
        // Avanzar el estatus y guardar la llave va junto: dos eventos del mismo pedido no se cruzan
        synchronized (this) {
            Avance actual = avancePorPedido.get(pedido);
            String rechazo = rechazoPorAvance(tipo, guia, actual);
            if (rechazo != null) {
                String clase = rechazo.startsWith("guia") ? GUIA_DISTINTA : FUERA_DE_ORDEN;
                LOG.infof("422 evento llave=%s pedido=%s tipo=%s correlacion=%s motivo=%s",
                        llave, pedido, tipo, correlacion, rechazo);
                return error(422, clase, clase == GUIA_DISTINTA ? "La guía no coincide" : "El evento no toca todavía",
                        detalleDe(rechazo, pedido, actual));
            }
            Avance nuevo = "embarcado".equals(tipo)
                    ? new Avance(EMBARCADO, guia, (String) cuerpo.get("paqueteria"))
                    : new Avance(ENTREGADO, actual.guia(), actual.paqueteria());
            avancePorPedido.put(pedido, nuevo);
            eventosPorLlave.put(llave, new Evento(pedido, cuerpo, OffsetDateTime.now().toString()));
            LOG.infof("201 evento llave=%s pedido=%s tipo=%s guia=%s estatus=%s correlacion=%s",
                    llave, pedido, tipo, guia, nuevo.estatus(), correlacion);
            return Response.status(201).entity(acuseEvento(pedido, tipo, nuevo.estatus(), "registrado")).build();
        }
    }

    /** La llave del evento ya llegó: mismo pedido y mismo contenido es un reenvío (200); si no, llave reusada (422). */
    private Response eventoYaVisto(Evento previo, String llave, String pedido, String correlacion,
                                   Map<String, Object> cuerpo) {
        if (previo.pedido().equals(pedido) && Objects.equals(previo.cuerpo(), cuerpo)) {
            LOG.infof("200 evento llave=%s pedido=%s correlacion=%s (reenvío, no se duplica)", llave, pedido, correlacion);
            return Response.ok(acuseEvento(pedido, String.valueOf(cuerpo.get("tipo")),
                    avancePorPedido.get(pedido).estatus(), "ya_existia")).build();
        }
        LOG.infof("422 evento llave=%s pedido=%s correlacion=%s motivo=llave reusada con otro contenido",
                llave, pedido, correlacion);
        return error(422, LLAVE_REUSADA, "La llave ya se usó con otro contenido",
                "La llave " + llave + " ya se usó con otro contenido; no se adivina cuál de los dos vale");
    }

    /** Devuelve el motivo si el cuerpo del evento no trae lo que su tipo exige, o {@code null} si está completo. */
    private static String validarEvento(Map<String, Object> cuerpo) {
        if (cuerpo == null) {
            return "El cuerpo está vacío";
        }
        Object tipo = cuerpo.get("tipo");
        if (!"embarcado".equals(tipo) && !"entregado".equals(tipo)) {
            return "El tipo debe ser embarcado o entregado";
        }
        if (!(cuerpo.get("guia") instanceof String g) || g.isBlank()) {
            return "Falta la guía";
        }
        if (!(cuerpo.get("fecha") instanceof String f) || f.isBlank()) {
            return "Falta la fecha";
        }
        if ("embarcado".equals(tipo) && (!(cuerpo.get("paqueteria") instanceof String p) || p.isBlank())) {
            return "Falta la paquetería";
        }
        return null;
    }

    /** Si el evento no toca con el estatus actual, una clave del motivo; si toca, {@code null}. */
    private static String rechazoPorAvance(String tipo, String guia, Avance actual) {
        if ("embarcado".equals(tipo)) {
            return CREADO.equals(actual.estatus()) ? null : "ya-embarcado";
        }
        if (CREADO.equals(actual.estatus())) {
            return "sin-embarque";
        }
        if (ENTREGADO.equals(actual.estatus())) {
            return "ya-entregado";
        }
        return actual.guia().equals(guia) ? null : "guia";
    }

    private static String detalleDe(String rechazo, String pedido, Avance actual) {
        return switch (rechazo) {
            case "sin-embarque" -> "El pedido " + pedido + " no se ha embarcado: no puede entregarse todavía";
            case "ya-embarcado" -> "El pedido " + pedido + " ya está " + actual.estatus() + ": no se embarca otra vez";
            case "ya-entregado" -> "El pedido " + pedido + " ya se entregó";
            default -> "El pedido " + pedido + " salió con la guía " + actual.guia() + ", no con otra";
        };
    }

    private static Map<String, Object> acuseEvento(String pedido, String tipo, String estatus, String resultado) {
        return Map.of("pedido", pedido, "evento", tipo, "estatus", estatus, "resultado", resultado);
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
