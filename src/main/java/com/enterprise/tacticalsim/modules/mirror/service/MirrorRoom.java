// Guardar en: src/main/java/com/enterprise/tacticalsim/modules/mirror/service/MirrorRoom.java
package com.enterprise.tacticalsim.modules.mirror.service;

import org.springframework.web.socket.WebSocketSession;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Estado en memoria de una sala de duplicacion de pantalla: un emisor,
 * varios visores y el ultimo frame. No guarda el emitterKey en claro,
 * solo su hash SHA-256, y lo compara en tiempo constante.
 * Los cambios de membresia y del ultimo frame se hacen dentro de
 * {@link #getLock()} (lo gestiona {@link MirrorRoomService}).
 */
public final class MirrorRoom {

    private final String code;
    private final byte[] keyHash;
    private final Instant expiresAt;
    private final FixedWindowCounter frameLimiter;
    private final Object lock = new Object();
    private final Map<String, WebSocketSession> viewers = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile WebSocketSession emitter;
    private volatile String lastFrame;

    MirrorRoom(String code, String emitterKey, Instant expiresAt, FixedWindowCounter frameLimiter) {
        this.code = code;
        this.keyHash = sha256(emitterKey);
        this.expiresAt = expiresAt;
        this.frameLimiter = frameLimiter;
    }

    public String getCode() {
        return code;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    /** Compara la clave presentada con la de la sala en tiempo constante. */
    public boolean keyMatches(String candidate) {
        if (candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(keyHash, sha256(candidate));
    }

    public int viewerCount() {
        return viewers.size();
    }

    public boolean isClosed() {
        return closed.get();
    }

    Object getLock() {
        return lock;
    }

    Map<String, WebSocketSession> getViewers() {
        return viewers;
    }

    WebSocketSession getEmitter() {
        return emitter;
    }

    void setEmitter(WebSocketSession emitter) {
        this.emitter = emitter;
    }

    String getLastFrame() {
        return lastFrame;
    }

    void setLastFrame(String lastFrame) {
        this.lastFrame = lastFrame;
    }

    FixedWindowCounter getFrameLimiter() {
        return frameLimiter;
    }

    /** Marca la sala como cerrada; devuelve true solo la primera vez. */
    boolean markClosed() {
        return closed.compareAndSet(false, true);
    }

    /** Copia de todas las sesiones conectadas (emisor y visores). */
    List<WebSocketSession> allSessions() {
        List<WebSocketSession> all = new ArrayList<>(viewers.values());
        WebSocketSession current = emitter;
        if (current != null) {
            all.add(current);
        }
        return all;
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no esta disponible en esta JVM", ex);
        }
    }
}
