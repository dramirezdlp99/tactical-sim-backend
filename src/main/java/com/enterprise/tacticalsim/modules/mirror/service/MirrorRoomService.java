// Guardar en: src/main/java/com/enterprise/tacticalsim/modules/mirror/service/MirrorRoomService.java
package com.enterprise.tacticalsim.modules.mirror.service;

import com.enterprise.tacticalsim.modules.mirror.config.MirrorProperties;
import com.enterprise.tacticalsim.modules.mirror.dto.MirrorRoomResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Registro en memoria y logica del relevo de duplicacion de pantalla.
 *
 * LIMITACION CONOCIDA: las salas viven solo en la memoria de ESTA instancia
 * (ConcurrentHashMap). Sirve para el despliegue de una sola instancia en
 * Render gratis; si el backend se reinicia o se duerme, las salas se
 * pierden, y con varias instancias un visor podria caer en una que no
 * conoce la sala. Para escalar haria falta un bus compartido (Redis, etc.).
 *
 * El servidor NO interpreta los frames: solo valida que sean un objeto JSON
 * con "type":"frame", limita tamano y tasa, y reenvia el MISMO texto.
 *
 * SEGURIDAD: ni el emitterKey ni el codigo de sala se escriben en los logs
 * (el codigo de sala es el secreto del visor).
 */
@Slf4j
@Service
public class MirrorRoomService {

    /** Alfabeto sin caracteres ambiguos: sin 0/O, 1/I ni L. */
    static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int CODE_LENGTH = 6;
    /** 32 bytes = 256 bits de entropia para el emitterKey. */
    private static final int KEY_BYTES = 32;
    private static final int MAX_CODE_ATTEMPTS = 20;

    /** Un visor que mande mas de este numero de mensajes ajenos en la ventana se cierra. */
    static final int VIEWER_SPAM_LIMIT = 20;
    static final long VIEWER_SPAM_WINDOW_MILLIS = 10_000L;

    private static final String PING_MESSAGE = "{\"type\":\"ping\"}";

    private final MirrorProperties props;
    private final ObjectMapper objectMapper;
    private final ObjectReader strictReader;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentMap<String, MirrorRoom> rooms = new ConcurrentHashMap<>();
    private final Object createLock = new Object();

    @Autowired
    public MirrorRoomService(MirrorProperties props, ObjectMapper objectMapper, ObjectProvider<Clock> clockProvider) {
        this(props, objectMapper, clockProvider.getIfUnique(Clock::systemUTC));
    }

    /** Constructor con reloj explicito (pruebas). */
    public MirrorRoomService(MirrorProperties props, ObjectMapper objectMapper, Clock clock) {
        this.props = props;
        this.objectMapper = objectMapper;
        // FAIL_ON_TRAILING_TOKENS: rechaza '{"type":"frame"} basura', porque el
        // texto original se reenvia tal cual a los visores y debe ser JSON valido.
        this.strictReader = objectMapper.readerFor(JsonNode.class)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Ciclo de vida de las salas
    // ------------------------------------------------------------------

    /**
     * Crea una sala nueva.
     *
     * @throws MirrorRoomLimitException si ya hay {@code mirror.max-rooms} salas activas.
     */
    public MirrorRoomResponse createRoom() {
        cleanupExpiredRooms();
        String emitterKey = randomKey();
        long ttlSeconds = props.getRoomTtlMinutes() * 60L;
        synchronized (createLock) {
            if (rooms.size() >= props.getMaxRooms()) {
                throw new MirrorRoomLimitException(
                        "Se alcanzó el límite de salas de duplicación activas. Intenta de nuevo en unos minutos.");
            }
            for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
                String code = randomCode();
                MirrorRoom room = new MirrorRoom(
                        code,
                        emitterKey,
                        clock.instant().plusSeconds(ttlSeconds),
                        new FixedWindowCounter(props.getMaxFramesPerSecond(), 1000L));
                if (rooms.putIfAbsent(code, room) == null) {
                    log.info("Sala de duplicación creada. Salas activas: {}", rooms.size());
                    return new MirrorRoomResponse(code, emitterKey, ttlSeconds);
                }
            }
        }
        throw new IllegalStateException("No se pudo generar un código de sala único.");
    }

    /** Busca una sala vigente; si ya vencio, la cierra (limpieza perezosa) y devuelve vacio. */
    public Optional<MirrorRoom> findActiveRoom(String code) {
        if (code == null) {
            return Optional.empty();
        }
        MirrorRoom room = rooms.get(code.trim().toUpperCase(Locale.ROOT));
        if (room == null) {
            return Optional.empty();
        }
        if (isExpired(room)) {
            closeRoom(room, "La sala ha vencido");
            return Optional.empty();
        }
        return Optional.of(room);
    }

    public boolean hasViewerCapacity(MirrorRoom room) {
        return room.viewerCount() < props.getMaxViewersPerRoom();
    }

    public int activeRooms() {
        return rooms.size();
    }

    /** Barrido programado de salas vencidas (ademas de la limpieza perezosa). */
    @Scheduled(fixedDelayString = "${mirror.cleanup-millis:30000}")
    public void cleanupExpiredRooms() {
        for (MirrorRoom room : new ArrayList<>(rooms.values())) {
            if (isExpired(room)) {
                closeRoom(room, "La sala ha vencido");
            }
        }
    }

    /**
     * Heartbeat: el proxy de Render corta conexiones WebSocket inactivas, asi
     * que se manda {"type":"ping"} a todos periodicamente. El cliente puede
     * responder {"type":"pong"} (se ignora).
     */
    @Scheduled(fixedDelayString = "${mirror.heartbeat-millis:25000}")
    public void sendHeartbeat() {
        for (MirrorRoom room : rooms.values()) {
            if (room.isClosed()) {
                continue;
            }
            for (WebSocketSession session : room.allSessions()) {
                send(session, PING_MESSAGE);
            }
        }
    }

    /** Al apagar el servidor se avisa a todos para que el cliente sepa por que se cerro. */
    @PreDestroy
    public void closeAllRooms() {
        for (MirrorRoom room : new ArrayList<>(rooms.values())) {
            closeRoom(room, "El servidor se está reiniciando");
        }
    }

    private void closeRoom(MirrorRoom room, String reason) {
        if (!room.markClosed()) {
            return;
        }
        rooms.remove(room.getCode(), room);
        String message = objectMapper.createObjectNode().put("type", "closed").put("reason", reason).toString();
        for (WebSocketSession session : room.allSessions()) {
            send(session, message);
            closeQuietly(session, CloseStatus.NORMAL.withReason(reason));
        }
        room.getViewers().clear();
        room.setEmitter(null);
        room.setLastFrame(null);
        log.info("Sala de duplicación cerrada ({}). Salas activas: {}", reason, rooms.size());
    }

    private boolean isExpired(MirrorRoom room) {
        return !clock.instant().isBefore(room.getExpiresAt());
    }

    // ------------------------------------------------------------------
    // Conexiones
    // ------------------------------------------------------------------

    /**
     * Registra al emisor. Si ya habia uno, el nuevo lo reemplaza y el viejo se
     * cierra con un motivo claro (permite reconectar).
     *
     * @return false si la sala ya no esta disponible.
     */
    public boolean attachEmitter(MirrorRoom room, WebSocketSession session) {
        WebSocketSession previous;
        synchronized (room.getLock()) {
            if (room.isClosed()) {
                return false;
            }
            previous = room.getEmitter();
            room.setEmitter(session);
        }
        if (previous != null) {
            closeQuietly(previous, new CloseStatus(4001, "Emisor reemplazado por una conexión nueva"));
        }
        send(session, helloMessage("emitter", room.getCode(), room.viewerCount()));
        broadcastToViewers(room, emitterMessage(true), null);
        return true;
    }

    /**
     * Registra a un visor y le envia: hello, estado del emisor y el ultimo frame.
     *
     * @return false si la sala esta llena, cerrada o el socket ya no responde.
     */
    public boolean attachViewer(MirrorRoom room, WebSocketSession session) {
        synchronized (room.getLock()) {
            if (room.isClosed() || room.viewerCount() >= props.getMaxViewersPerRoom()) {
                return false;
            }
            boolean ok = send(session, helloMessage("viewer", room.getCode(), room.viewerCount() + 1))
                    && send(session, emitterMessage(room.getEmitter() != null));
            String last = room.getLastFrame();
            if (ok && last != null) {
                ok = send(session, last);
            }
            if (!ok) {
                return false;
            }
            room.getViewers().put(session.getId(), session);
        }
        broadcastViewerCount(room, session.getId());
        return true;
    }

    public void detachViewer(MirrorRoom room, String sessionId) {
        if (room.getViewers().remove(sessionId) != null && !room.isClosed()) {
            broadcastViewerCount(room, null);
        }
    }

    public void detachEmitter(MirrorRoom room, String sessionId) {
        boolean wasCurrent = false;
        synchronized (room.getLock()) {
            WebSocketSession current = room.getEmitter();
            if (current != null && current.getId().equals(sessionId)) {
                room.setEmitter(null);
                wasCurrent = true;
            }
        }
        // Si fue reemplazado por otra conexion, el emisor sigue en linea.
        if (wasCurrent && !room.isClosed()) {
            broadcastToViewers(room, emitterMessage(false), null);
        }
    }

    // ------------------------------------------------------------------
    // Mensajes
    // ------------------------------------------------------------------

    /**
     * Procesa un mensaje de texto del emisor. Solo reenvia frames validos:
     * objeto JSON con type == "frame", dentro del limite de bytes y de tasa.
     * Todo lo demas (pong, basura, excedentes) se ignora en silencio.
     *
     * @param payloadBytes tamano real del mensaje en bytes (UTF-8)
     */
    public void handleEmitterMessage(MirrorRoom room, String sessionId, String payload, int payloadBytes) {
        WebSocketSession current = room.getEmitter();
        if (current == null || !current.getId().equals(sessionId) || room.isClosed()) {
            return; // emisor reemplazado: sus mensajes ya no cuentan
        }
        if (payloadBytes > props.getMaxFrameBytes()) {
            log.debug("Frame descartado por exceder {} bytes", props.getMaxFrameBytes());
            return;
        }
        JsonNode node;
        try {
            node = strictReader.readTree(payload);
        } catch (JsonProcessingException ex) {
            log.debug("Mensaje del emisor descartado: no es JSON válido");
            return;
        }
        if (node == null || !node.isObject()) {
            return;
        }
        JsonNode type = node.get("type");
        if (type == null || !type.isTextual() || !"frame".equals(type.asText())) {
            return; // "pong" u otros tipos: se ignoran
        }
        if (!room.getFrameLimiter().tryAcquire(clock.millis())) {
            return; // tasa excedida: descarte silencioso
        }
        List<WebSocketSession> targets;
        synchronized (room.getLock()) {
            room.setLastFrame(payload);
            targets = new ArrayList<>(room.getViewers().values());
        }
        for (WebSocketSession viewer : targets) {
            send(viewer, payload);
        }
    }

    /** Contador por visor para detectar spam (se guarda en los atributos de la sesion). */
    public FixedWindowCounter newViewerSpamGuard() {
        return new FixedWindowCounter(VIEWER_SPAM_LIMIT, VIEWER_SPAM_WINDOW_MILLIS);
    }

    /**
     * Un visor es de solo lectura: todo lo que envie se ignora, salvo que sea
     * spam repetido.
     *
     * @return true si el visor debe cerrarse por abuso.
     */
    public boolean isViewerSpam(String payload, FixedWindowCounter guard) {
        if (isPong(payload)) {
            return false;
        }
        return !guard.tryAcquire(clock.millis());
    }

    private boolean isPong(String payload) {
        if (payload == null || payload.length() > 64) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            return node != null && node.isObject() && "pong".equals(node.path("type").asText());
        } catch (JsonProcessingException ex) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Auxiliares de envio
    // ------------------------------------------------------------------

    private void broadcastViewerCount(MirrorRoom room, String exceptSessionId) {
        String message = objectMapper.createObjectNode()
                .put("type", "viewers").put("count", room.viewerCount()).toString();
        WebSocketSession emitter = room.getEmitter();
        if (emitter != null) {
            send(emitter, message);
        }
        broadcastToViewers(room, message, exceptSessionId);
    }

    private void broadcastToViewers(MirrorRoom room, String message, String exceptSessionId) {
        for (WebSocketSession viewer : new ArrayList<>(room.getViewers().values())) {
            if (exceptSessionId == null || !exceptSessionId.equals(viewer.getId())) {
                send(viewer, message);
            }
        }
    }

    private String helloMessage(String role, String roomCode, int viewers) {
        return objectMapper.createObjectNode()
                .put("type", "hello").put("role", role)
                .put("roomCode", roomCode).put("viewers", viewers).toString();
    }

    private String emitterMessage(boolean online) {
        return objectMapper.createObjectNode().put("type", "emitter").put("online", online).toString();
    }

    /** Envia un texto; si el socket ya no responde lo cierra sin propagar la excepcion. */
    private boolean send(WebSocketSession session, String text) {
        try {
            session.sendMessage(new TextMessage(text));
            return true;
        } catch (Exception ex) {
            log.debug("No se pudo enviar a la sesión {}: {}", session.getId(), ex.getMessage());
            closeQuietly(session, CloseStatus.SESSION_NOT_RELIABLE);
            return false;
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception ex) {
            log.debug("Error al cerrar la sesión {}: {}", session.getId(), ex.getMessage());
        }
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    private String randomKey() {
        byte[] bytes = new byte[KEY_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
