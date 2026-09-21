package com.enterprise.tacticalsim.modules.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lo que devuelve POST /api/v1/auth/login cuando las credenciales son
 * correctas: todavía NO es el JWT final, es el "boleto" para el segundo
 * paso (POST /verify-2fa). Antes login() devolvía el JWT directamente,
 * o sea el 2FA de la UI era pura decoración.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TwoFactorChallengeResponse {
    private String tempToken;
    private long expiresInSeconds;
}