// Guardar en: src/main/java/com/enterprise/tacticalsim/modules/mirror/websocket/MirrorHandshakeInterceptor.java
package com.enterprise.tacticalsim.modules.mirror.websocket;

import com.enterprise.tacticalsim.modules.mirror.service.MirrorRoom;
import com.enterprise.tacticalsim.modules.mirror.service.MirrorRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * Autoriza el handshake de /ws/mirror?room=CODE[&key=EMITTERKEY].
 *
 * <ul>
 *   <li>Sala inexistente o vencida: 404.</li>
 *   <li>Con parametro key: se trata de un EMISOR; key invalida: 403.</li>
 *   <li>Sin key: VISOR de solo lectura (el codigo de sala es el secreto).
 *       Sala con el maximo de visores: 429.</li>
 * </ul>
 *
 * El origen (cabecera Origin) lo valida el interceptor estandar de Spring
 * contra cors.allowed-origins (ver MirrorWebSocketConfig); los clientes sin
 * Origin (pruebas, herramientas) se permiten.
 *
 * Nunca se registra la URL ni el emitterKey.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MirrorHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_ROOM = "mirror.room";
    public static final String ATTR_ROLE = "mirror.role";
    public static final String ROLE_EMITTER = "emitter";
    public static final String ROLE_VIEWER = "viewer";

    private final MirrorRoomService roomService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return reject(response, HttpStatus.BAD_REQUEST, "Solicitud WebSocket no válida.");
        }
        String roomCode = servletRequest.getServletRequest().getParameter("room");
        String key = servletRequest.getServletRequest().getParameter("key");

        Optional<MirrorRoom> found = roomService.findActiveRoom(roomCode);
        if (found.isEmpty()) {
            return reject(response, HttpStatus.NOT_FOUND, "La sala no existe o ya venció.");
        }
        MirrorRoom room = found.get();

        if (key != null) {
            if (!room.keyMatches(key)) {
                log.warn("Intento de conexión de emisor con clave inválida");
                return reject(response, HttpStatus.FORBIDDEN, "La clave del emisor no es válida.");
            }
            attributes.put(ATTR_ROLE, ROLE_EMITTER);
        } else {
            if (!roomService.hasViewerCapacity(room)) {
                return reject(response, HttpStatus.TOO_MANY_REQUESTS,
                        "La sala ya tiene el máximo de visores conectados.");
            }
            attributes.put(ATTR_ROLE, ROLE_VIEWER);
        }
        attributes.put(ATTR_ROOM, room);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // Sin trabajo posterior al handshake.
    }

    /** Rechaza con codigo HTTP y un cuerpo JSON corto en espanol. */
    private boolean reject(ServerHttpResponse response, HttpStatus status, String message) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            String body = "{\"success\":false,\"message\":\"" + message + "\"}";
            response.getBody().write(body.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            log.debug("No se pudo escribir el cuerpo del rechazo: {}", ex.getMessage());
        }
        return false;
    }
}
