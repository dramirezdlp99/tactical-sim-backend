// Guardar en: src/main/java/com/enterprise/tacticalsim/modules/mirror/dto/MirrorRoomResponse.java
package com.enterprise.tacticalsim.modules.mirror.dto;

/**
 * Datos que recibe el EMISOR al crear una sala de duplicacion de pantalla.
 * Viaja dentro de {@code ApiResponse.data}.
 *
 * @param roomCode         codigo de 6 caracteres que se comparte con el proyector (visor).
 * @param emitterKey       secreto aleatorio de la sala; solo lo conoce quien la creo.
 *                         Se entrega UNA sola vez y nunca se escribe en los logs.
 * @param expiresInSeconds segundos de vida que le quedan a la sala desde su creacion.
 */
public record MirrorRoomResponse(String roomCode, String emitterKey, long expiresInSeconds) {
}
