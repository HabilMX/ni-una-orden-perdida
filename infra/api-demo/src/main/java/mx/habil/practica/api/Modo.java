package mx.habil.practica.api;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cómo se comporta la API al crear un pedido (desde la 1.4.0, para las lecciones 19 y 20; {@code satura}, desde la
 * 1.5.0).
 *
 * <ul>
 *   <li><b>normal</b>: contesta al momento.</li>
 *   <li><b>lento</b>: espera (10 s por omisión) y DESPUÉS registra el pedido y contesta. Un cliente con un tiempo
 *       límite menor corta la llamada sin saber si el pedido quedó: sí quedó. Es el caso «no se sabe».</li>
 *   <li><b>rechaza</b>: contesta 422 {@code rechazado-por-negocio} y no registra nada. Es un «no» del destino:
 *       repetir la llamada da la misma respuesta.</li>
 *   <li><b>satura</b>: las siguientes {@code veces} llamadas (2 por omisión) contestan 429 {@code saturada} con
 *       {@code Retry-After} ({@code retry_after_s}, 3 s por omisión) y no registran nada; las que vienen después se
 *       atienden como en normal. Es un «ahora no, prueba después»: repetir la llamada, esperando, sí sirve.</li>
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
    static final String SATURA = "satura";
    static final long ESPERA_LENTO_MS = 10_000;
    static final int VECES_SATURA = 2;
    static final int RETRY_AFTER_S = 3;

    private volatile String actual = NORMAL;
    private volatile long esperaMs = ESPERA_LENTO_MS;
    private volatile int retryAfterS = RETRY_AFTER_S;
    private final AtomicInteger quedan429 = new AtomicInteger();

    String actual() {
        return actual;
    }

    long esperaMs() {
        return esperaMs;
    }

    int retryAfterS() {
        return retryAfterS;
    }

    int quedan429() {
        return quedan429.get();
    }

    /** {@code veces} y {@code retryAfter} solo cuentan en modo satura; en los otros modos no queda ningún 429. */
    void cambiar(String modo, long espera, int veces, int retryAfter) {
        this.esperaMs = espera;
        this.retryAfterS = retryAfter;
        this.quedan429.set(SATURA.equals(modo) ? veces : 0);
        this.actual = modo;
    }

    /**
     * En modo satura, si todavía le toca a esta llamada un 429, lo descuenta y dice que sí. Dos llamadas a la vez no
     * se llevan el mismo: el contador baja de uno en uno y no pasa de cero.
     */
    boolean tocaUn429() {
        return SATURA.equals(actual) && quedan429.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0;
    }

    static boolean valido(String modo) {
        return NORMAL.equals(modo) || LENTO.equals(modo) || RECHAZA.equals(modo) || SATURA.equals(modo);
    }
}
