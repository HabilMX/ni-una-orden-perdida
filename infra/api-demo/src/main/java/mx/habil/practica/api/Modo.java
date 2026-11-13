package mx.habil.practica.api;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Cómo se comporta la API al crear un pedido (desde la 1.4.0, para las lecciones 19 y 20).
 *
 * <ul>
 *   <li><b>normal</b>: contesta al momento.</li>
 *   <li><b>lento</b>: espera (10 s por omisión) y DESPUÉS registra el pedido y contesta. Un cliente con un tiempo
 *       límite menor corta la llamada sin saber si el pedido quedó: sí quedó. Es el caso «no se sabe».</li>
 *   <li><b>rechaza</b>: contesta 422 {@code rechazado-por-negocio} y no registra nada. Es un «no» del destino:
 *       repetir la llamada da la misma respuesta.</li>
 * </ul>
 *
 * <p>Se cambia con {@code POST /admin/modo} (ver {@link AdminResource}). Vive en memoria: al reiniciar, vuelve a
 * normal.
 */
@ApplicationScoped
public class Modo {

    static final String NORMAL = "normal";
    static final String LENTO = "lento";
    static final String RECHAZA = "rechaza";
    static final long ESPERA_LENTO_MS = 10_000;

    private volatile String actual = NORMAL;
    private volatile long esperaMs = ESPERA_LENTO_MS;

    String actual() {
        return actual;
    }

    long esperaMs() {
        return esperaMs;
    }

    void cambiar(String modo, long espera) {
        this.esperaMs = espera;
        this.actual = modo;
    }

    static boolean valido(String modo) {
        return NORMAL.equals(modo) || LENTO.equals(modo) || RECHAZA.equals(modo);
    }
}
