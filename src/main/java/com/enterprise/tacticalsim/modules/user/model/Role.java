package com.enterprise.tacticalsim.modules.user.model;

public enum Role {
    ROLE_USER,
    ROLE_COACH,
    // CAMBIO: faltaba por completo. AuthScreen.jsx y RegisterScreen.jsx ya
    // mandaban el string "ROLE_ANALYST" para el perfil "Analista Táctico",
    // pero el enum no lo tenía -> Jackson lanzaba error de deserialización
    // y cualquier registro/login como analista fallaba.
    ROLE_ANALYST,
    ROLE_ADMIN
}