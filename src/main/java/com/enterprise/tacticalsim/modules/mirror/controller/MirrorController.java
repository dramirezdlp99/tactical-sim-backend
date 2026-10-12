// Guardar en: src/main/java/com/enterprise/tacticalsim/modules/mirror/controller/MirrorController.java
package com.enterprise.tacticalsim.modules.mirror.controller;

import com.enterprise.tacticalsim.core.ApiResponse;
import com.enterprise.tacticalsim.modules.mirror.dto.MirrorRoomResponse;
import com.enterprise.tacticalsim.modules.mirror.service.MirrorRoomLimitException;
import com.enterprise.tacticalsim.modules.mirror.service.MirrorRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Duplicacion de pantalla: crea la sala a la que luego se conectan el
 * emisor y los visores por WebSocket (/ws/mirror). El POST exige JWT con
 * rol COACH, ANALYST o ADMIN (regla en SecurityConfig).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/mirror")
@RequiredArgsConstructor
public class MirrorController {

    private final MirrorRoomService roomService;

    @PostMapping("/rooms")
    public ResponseEntity<ApiResponse<MirrorRoomResponse>> createRoom(Authentication authentication) {
        String user = authentication != null ? authentication.getName() : "desconocido";
        try {
            MirrorRoomResponse room = roomService.createRoom();
            log.info("Sala de duplicación creada por {}", user);
            return ResponseEntity.status(HttpStatus.CREATED)
                    // La respuesta lleva el emitterKey: que ningun proxy ni cache la guarde.
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(ApiResponse.success(room, "Sala de duplicación creada"));
        } catch (MirrorRoomLimitException ex) {
            log.warn("Creación de sala rechazada por límite (usuario {})", user);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }
}
