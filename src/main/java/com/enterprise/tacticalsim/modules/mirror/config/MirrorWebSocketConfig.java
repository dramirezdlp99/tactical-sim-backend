// Guardar en: src/main/java/com/enterprise/tacticalsim/modules/mirror/config/MirrorWebSocketConfig.java
package com.enterprise.tacticalsim.modules.mirror.config;

import com.enterprise.tacticalsim.modules.mirror.websocket.MirrorHandshakeInterceptor;
import com.enterprise.tacticalsim.modules.mirror.websocket.MirrorWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import java.util.Arrays;

/**
 * Registra el canal /ws/mirror. Los origenes permitidos son los mismos de
 * la propiedad existente {@code cors.allowed-origins} (lista separada por
 * comas). Un cliente SIN cabecera Origin (pruebas, curl) se permite; uno
 * con un Origin no listado recibe 403.
 *
 * {@code @EnableScheduling} activa el heartbeat y la limpieza de salas.
 */
@Configuration
@EnableWebSocket
@EnableScheduling
@RequiredArgsConstructor
public class MirrorWebSocketConfig implements WebSocketConfigurer {

    public static final String MIRROR_PATH = "/ws/mirror";

    private final MirrorWebSocketHandler handler;
    private final MirrorHandshakeInterceptor handshakeInterceptor;

    @Value("${cors.allowed-origins:}")
    private String corsAllowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
        registry.addHandler(handler, MIRROR_PATH)
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins(origins);
    }

    /**
     * Tomcat cierra la conexion (codigo 1009) si un mensaje supera su buffer
     * de texto (8 KB por defecto). Se sube a 2x el maximo de frame para que
     * los frames grandes lleguen al servicio y se descarten con elegancia,
     * sin tumbar la conexion del emisor.
     */
    @Bean
    public ServletServerContainerFactoryBean mirrorWebSocketContainer(MirrorProperties properties) {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(Math.max(16 * 1024, properties.getMaxFrameBytes() * 2));
        return container;
    }
}
