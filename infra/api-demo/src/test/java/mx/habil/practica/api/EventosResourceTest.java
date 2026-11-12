package mx.habil.practica.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de {@code POST /pedidos/{pedido}/eventos} y del {@code GET} con estatus y guía.
 *
 * <p>Cada prueba usa su propio número de pedido, así no dependen del orden en que corren: la API guarda en memoria
 * durante toda la corrida de pruebas.
 */
@QuarkusTest
class EventosResourceTest {

    private static String nuevoPedido() {
        String pedido = "P-" + UUID.randomUUID().toString().substring(0, 8);
        given().contentType(ContentType.JSON).header("Idempotency-Key", pedido)
                .body("{\"pedido\":\"" + pedido + "\",\"tienda\":\"T-501\","
                        + "\"partidas\":[{\"sku\":\"SKU-0001\",\"cantidad\":1,\"precio\":10.00}]}")
                .when().post("/pedidos").then().statusCode(201);
        return pedido;
    }

    private static String embarque(String guia) {
        return "{\"tipo\":\"embarcado\",\"guia\":\"" + guia + "\",\"paqueteria\":\"PAQX\","
                + "\"fecha\":\"2026-11-13T09:30:00-06:00\"}";
    }

    private static String entrega(String guia) {
        return "{\"tipo\":\"entregado\",\"guia\":\"" + guia + "\",\"fecha\":\"2026-11-14T17:05:00-06:00\"}";
    }

    private static io.restassured.response.ValidatableResponse evento(String pedido, String llave, String cuerpo) {
        return given().contentType(ContentType.JSON).header("Idempotency-Key", llave).body(cuerpo)
                .when().post("/pedidos/" + pedido + "/eventos").then();
    }

    @Test
    void unPedidoRecienCreadoEstaCreadoYSinGuia() {
        String pedido = nuevoPedido();
        given().when().get("/pedidos/" + pedido).then().statusCode(200)
                .body("estatus", equalTo("CREADO")).body("guia", nullValue()).body("pedido", equalTo(pedido));
    }

    @Test
    void elEmbarqueDejaEmbarcadoConSuGuia() {
        String pedido = nuevoPedido();
        evento(pedido, pedido + "-embarcada", embarque("GUIA-1")).statusCode(201).body("estatus", equalTo("EMBARCADO"));
        given().when().get("/pedidos/" + pedido).then().statusCode(200)
                .body("estatus", equalTo("EMBARCADO")).body("guia", equalTo("GUIA-1"))
                .body("paqueteria", equalTo("PAQX"));
    }

    @Test
    void laEntregaConLaMismaGuiaDejaEntregado() {
        String pedido = nuevoPedido();
        evento(pedido, pedido + "-embarcada", embarque("GUIA-2")).statusCode(201);
        evento(pedido, pedido + "-entregada", entrega("GUIA-2")).statusCode(201).body("estatus", equalTo("ENTREGADO"));
        given().when().get("/pedidos/" + pedido).then().body("estatus", equalTo("ENTREGADO")).body("guia", equalTo("GUIA-2"));
    }

    @Test
    void elReenvioConLaMismaLlaveYElMismoContenidoContesta200() {
        String pedido = nuevoPedido();
        evento(pedido, pedido + "-embarcada", embarque("GUIA-3")).statusCode(201);
        evento(pedido, pedido + "-embarcada", embarque("GUIA-3")).statusCode(200).body("resultado", equalTo("ya_existia"));
    }

    @Test
    void laMismaLlaveConOtroContenidoEsLlaveReusada() {
        String pedido = nuevoPedido();
        evento(pedido, pedido + "-embarcada", embarque("GUIA-4")).statusCode(201);
        evento(pedido, pedido + "-embarcada", embarque("GUIA-OTRA")).statusCode(422)
                .body("type", equalTo(PedidosResource.LLAVE_REUSADA));
    }

    @Test
    void sinLlaveContesta400() {
        String pedido = nuevoPedido();
        given().contentType(ContentType.JSON).body(embarque("GUIA-5"))
                .when().post("/pedidos/" + pedido + "/eventos").then().statusCode(400)
                .body("type", equalTo(PedidosResource.FALTA_LLAVE));
    }

    @Test
    void unPedidoQueNoExisteContesta404() {
        evento("NO-EXISTE-77", "NO-EXISTE-77-embarcada", embarque("GUIA-6")).statusCode(404)
                .body("type", equalTo(PedidosResource.NO_EXISTE));
    }

    @Test
    void laEntregaAntesDelEmbarqueEstaFueraDeOrdenYNoGuardaLaLlave() {
        String pedido = nuevoPedido();
        evento(pedido, pedido + "-entregada", entrega("GUIA-7")).statusCode(422)
                .body("type", equalTo(PedidosResource.FUERA_DE_ORDEN));
        given().when().get("/pedidos/" + pedido).then().body("estatus", equalTo("CREADO"));
        // Cuando ya toca, la MISMA llave se acepta: el rechazo no la guardó
        evento(pedido, pedido + "-embarcada", embarque("GUIA-7")).statusCode(201);
        evento(pedido, pedido + "-entregada", entrega("GUIA-7")).statusCode(201).body("estatus", equalTo("ENTREGADO"));
    }

    @Test
    void laEntregaConOtraGuiaContestaGuiaDistinta() {
        String pedido = nuevoPedido();
        evento(pedido, pedido + "-embarcada", embarque("GUIA-8")).statusCode(201);
        evento(pedido, pedido + "-entregada", entrega("GUIA-NO-ES")).statusCode(422)
                .body("type", equalTo(PedidosResource.GUIA_DISTINTA));
        given().when().get("/pedidos/" + pedido).then().body("estatus", equalTo("EMBARCADO"));
    }

    @Test
    void unSegundoEmbarqueConOtraLlaveEstaFueraDeOrden() {
        String pedido = nuevoPedido();
        evento(pedido, pedido + "-embarcada", embarque("GUIA-9")).statusCode(201);
        evento(pedido, pedido + "-embarcada-otra-vez", embarque("GUIA-9")).statusCode(422)
                .body("type", equalTo(PedidosResource.FUERA_DE_ORDEN));
    }

    @Test
    void unEventoSinPaqueteriaOSinTipoValidoEsInvalido() {
        String pedido = nuevoPedido();
        evento(pedido, pedido + "-x1", "{\"tipo\":\"embarcado\",\"guia\":\"G\",\"fecha\":\"2026-11-13\"}")
                .statusCode(422).body("type", equalTo(PedidosResource.EVENTO_INVALIDO));
        evento(pedido, pedido + "-x2", "{\"tipo\":\"perdido\",\"guia\":\"G\",\"fecha\":\"2026-11-13\"}")
                .statusCode(422).body("type", equalTo(PedidosResource.EVENTO_INVALIDO));
    }
}
