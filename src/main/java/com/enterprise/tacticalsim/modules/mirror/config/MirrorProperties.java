// Guardar en: src/main/java/com/enterprise/tacticalsim/modules/mirror/config/MirrorProperties.java
package com.enterprise.tacticalsim.modules.mirror.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propiedades {@code mirror.*} de la duplicacion de pantalla. Los valores
 * de respaldo salen de application.properties (con variable de entorno);
 * los valores por defecto de aqui solo aplican si la propiedad no existe.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "mirror")
public class MirrorProperties {

    /** Vida maxima de una sala, en minutos. */
    private int roomTtlMinutes = 240;

    /** Visores simultaneos permitidos por sala. */
    private int maxViewersPerRoom = 10;

    /** Tamano maximo de un frame del emisor, en bytes (UTF-8). */
    private int maxFrameBytes = 16384;

    /** Frames por segundo que se aceptan del emisor; el exceso se descarta. */
    private int maxFramesPerSecond = 40;

    /** Salas activas simultaneas en todo el servidor (anti-abuso). */
    private int maxRooms = 50;
}
