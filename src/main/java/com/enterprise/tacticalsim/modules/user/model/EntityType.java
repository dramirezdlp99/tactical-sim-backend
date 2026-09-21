package com.enterprise.tacticalsim.modules.user.model;

/**
 * Tipo de entidad operativa elegido en el registro (Alta Enterprise).
 * Los valores en minúscula del lado del frontend (orgType: 'franchise',
 * 'academy', 'federation') se mapean 1:1 a estos nombres en mayúscula,
 * que es como Jackson deserializa un enum por defecto.
 */
public enum EntityType {
    FRANCHISE,   // Franquicia / Club (NBA, Euroliga, ATP)
    ACADEMY,     // Academia de Rendimiento (Centros de Élite)
    FEDERATION   // Federación Nacional (Selecciones)
}