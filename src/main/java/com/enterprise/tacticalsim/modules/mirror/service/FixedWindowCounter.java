// Guardar en: src/main/java/com/enterprise/tacticalsim/modules/mirror/service/FixedWindowCounter.java
package com.enterprise.tacticalsim.modules.mirror.service;

/**
 * Contador de ventana fija, seguro entre hilos: permite como maximo
 * {@code limit} eventos por ventana de {@code windowMillis}. Se usa para
 * limitar los frames del emisor y para detectar spam de un visor.
 * El tiempo se recibe como parametro para poder probarlo con un reloj
 * controlado.
 */
public final class FixedWindowCounter {

    private final int limit;
    private final long windowMillis;
    private boolean started = false;
    private long windowStart = 0L;
    private int count = 0;

    public FixedWindowCounter(int limit, long windowMillis) {
        this.limit = limit;
        this.windowMillis = windowMillis;
    }

    /**
     * Intenta registrar un evento.
     *
     * @return true si cabe en la ventana actual; false si ya se alcanzo el limite.
     */
    public synchronized boolean tryAcquire(long nowMillis) {
        if (!started || nowMillis < windowStart || nowMillis - windowStart >= windowMillis) {
            started = true;
            windowStart = nowMillis;
            count = 0;
        }
        if (count >= limit) {
            return false;
        }
        count++;
        return true;
    }
}
