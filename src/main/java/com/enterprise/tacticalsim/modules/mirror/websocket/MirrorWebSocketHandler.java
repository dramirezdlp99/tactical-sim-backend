// Guardar en: src/main/java/com/enterprise/tacticalsim/modules/mirror/websocket/MirrorWebSocketHandler.java
package com.enterprise.tacticalsim.modules.mirror.websocket;

import com.enterprise.tacticalsim.modules.mirror.service.FixedWindowCounter;
import com.enterprise.tacticalsim.modules.mirror.service.MirrorRoom;
import com.enterprise.tacticalsim.modules.mirror.service.MirrorRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;

import static com.enterprise.tacticalsim.modules.mirror.websocket.MirrorHandshakeInterceptor.ATTR_ROLE;
import static com.enterprise.tacticalsim.modules.mirror.websocket.MirrorHandshakeInterceptor.ATTR_ROOM;
import static com.enterprise.tacticalsim.modules.mirror.websocket.MirrorHandshakeInterceptor.ROLE_EMITTER;

/**
 * Manejador del canal /ws/mirror. El emisor (autorizado en el handshake)
 * manda frames que se reenvian tal cual a los visores; los visores son de
 * solo lectura. La logica de la sala vive en {@link MirrorRoomService}.
 *
 * Las sesiones se envuelven en {@link ConcurrentWebSocketSessionDecorator}
 * porque se les escribe desde varios hilos (frames, heartbeat, avisos) y un
 * visor lento no debe bloquear al resto: si no consume en 5 s o acumula mas
 * de 512 KB pendientes, se cierra.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MirrorWebSocketHandler extends TextWebSocketHandler {

    private static final String ATTR_GUARD = "mirror.spamGuard";
    private static final int SEND_TIME_LIMIT_MILLIS = 5_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 512 * 1024;

    private final MirrorRoomService roomService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Map<String, Object> attributes = session.getAttributes();
        Object room = attributes.get(ATTR_ROOM);
        Object role = attributes.get(ATTR_ROLE);
        if (!(room instanceof MirrorRoom mirrorRoom) || !(role instanceof String roleName)) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Conexión no válida"));
            return;
        }
        WebSocketSession safeSession =
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MILLIS, SEND_BUFFER_LIMIT_BYTES);

        boolean attached;
        if (ROLE_EMITTER.equals(roleName)) {
            attached = roomService.attachEmitter(mirrorRoom, safeSession);
        } else {
            attributes.put(ATTR_GUARD, roomService.newViewerSpamGuard());
            attached = roomService.attachViewer(mirrorRoom, safeSession);
        }
        if (!attached) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("La sala está llena o ya no está disponible"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Map<String, Object> attributes = session.getAttributes();
        Object room = attributes.get(ATTR_ROOM);
        Object role = attributes.get(ATTR_ROLE);
        if (!(room instanceof MirrorRoom mirrorRoom) || !(role instanceof String roleName)) {
            return;
        }
        try {
            if (ROLE_EMITTER.equals(roleName)) {
                roomService.handleEmitterMessage(
                        mirrorRoom, session.getId(), message.getPayload(), message.getPayloadLength());
            } else {
                Object guard = attributes.get(ATTR_GUARD);
                if (guard instanceof FixedWindowCounter counter
                        && roomService.isViewerSpam(message.getPayload(), counter)) {
                    closeQuietly(session, CloseStatus.POLICY_VIOLATION.withReason("Los visores son de solo lectura"));
                }
            }
        } catch (RuntimeException ex) {
            // Un mensaje defectuoso no debe tumbar la conexion.
            log.warn("Error al procesar un mensaje de duplicación: {}", ex.getMessage());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Error de transporte en duplicación ({}): {}", session.getId(), exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Map<String, Object> attributes = session.getAttributes();
        Object room = attributes.get(ATTR_ROOM);
        Object role = attributes.get(ATTR_ROLE);
        if (!(room instanceof MirrorRoom mirrorRoom) || !(role instanceof String roleName)) {
            return;
        }
        if (ROLE_EMITTER.equals(roleName)) {
            roomService.detachEmitter(mirrorRoom, session.getId());
        } else {
            roomService.detachViewer(mirrorRoom, session.getId());
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException ex) {
            log.debug("Error al cerrar la sesión {}: {}", session.getId(), ex.getMessage());
        }
    }
}
