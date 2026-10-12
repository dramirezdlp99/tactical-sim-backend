// Guardar en: src/main/java/com/enterprise/tacticalsim/modules/mirror/service/MirrorRoomLimitException.java
package com.enterprise.tacticalsim.modules.mirror.service;

/**
 * Se lanza cuando ya hay el maximo de salas activas permitido
 * ({@code mirror.max-rooms}). El controlador la traduce a HTTP 429.
 */
public class MirrorRoomLimitException extends RuntimeException {

    public MirrorRoomLimitException(String message) {
        super(message);
    }
}
