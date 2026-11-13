package mx.habil.practica.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Pruebas de {@code /admin/modo} y de cómo cambia crear un pedido en cada modo. */
@QuarkusTest
class ModoResourceTest {

    private static String pedido() {
        return "M-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String cuerpo(String pedido) {
        return "{\"pedido\":\"" + pedido + "\",\"tienda\":\"T-501\","
                + "\"partidas\":[{\"sku\":\"SKU-0001\",\"cantidad\":1,\"precio\":10.00}]}";
    }

    private static void modo(String json) {
        given().contentType(ContentType.JSON).body(json).when().post("/admin/modo").then().statusCode(200);
    }

    @AfterEach
    void volverANormal() {
        modo("{\"modo\":\"normal\"}");
    }

    @Test
    void elModoDesconocidoEsUn400() {
        given().contentType(ContentType.JSON).body("{\"modo\":\"raro\"}").when().post("/admin/modo")
                .then().statusCode(400);
    }

    @Test
    void enModoRechazaCrearContesta422YNoRegistra() {
        modo("{\"modo\":\"rechaza\"}");
        String p = pedido();
        given().contentType(ContentType.JSON).header("Idempotency-Key", p).body(cuerpo(p))
                .when().post("/pedidos").then().statusCode(422)
                .body("type", equalTo(PedidosResource.RECHAZADO_POR_NEGOCIO));
        given().when().get("/pedidos/" + p).then().statusCode(404);
    }

    @Test
    void enModoLentoTardaYDespuesRegistra() {
        modo("{\"modo\":\"lento\",\"espera_ms\":800}");
        String p = pedido();
        long t0 = System.currentTimeMillis();
        given().contentType(ContentType.JSON).header("Idempotency-Key", p).body(cuerpo(p))
                .when().post("/pedidos").then().statusCode(201);
        org.junit.jupiter.api.Assertions.assertTrue(System.currentTimeMillis() - t0 >= 800, "debió tardar");
        given().when().get("/pedidos/" + p).then().statusCode(200).body("estatus", equalTo("CREADO"));
    }

    @Test
    void enModoSaturaContesta429ConRetryAfterYDespuesRegistra() {
        modo("{\"modo\":\"satura\",\"veces\":2,\"retry_after_s\":4}");
        String p = pedido();
        for (int i = 0; i < 2; i++) {
            given().contentType(ContentType.JSON).header("Idempotency-Key", p).body(cuerpo(p))
                    .when().post("/pedidos").then().statusCode(429).header("Retry-After", "4")
                    .body("type", equalTo(PedidosResource.SATURADA));
        }
        given().when().get("/pedidos/" + p).then().statusCode(404);
        given().contentType(ContentType.JSON).header("Idempotency-Key", p).body(cuerpo(p))
                .when().post("/pedidos").then().statusCode(201);
        given().when().get("/admin/modo").then().statusCode(200).body("quedan_429", equalTo(0));
    }

    @Test
    void elGetDiceElModoVigente() {
        modo("{\"modo\":\"lento\",\"espera_ms\":5}");
        given().when().get("/admin/modo").then().statusCode(200).body("modo", equalTo("lento"))
                .body("espera_ms", greaterThanOrEqualTo(5)).body("espera_ms", lessThan(6));
    }
}
