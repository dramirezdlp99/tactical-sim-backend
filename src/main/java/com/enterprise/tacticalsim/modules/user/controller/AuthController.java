package com.enterprise.tacticalsim.modules.user.controller;

import com.enterprise.tacticalsim.core.ApiResponse;
import com.enterprise.tacticalsim.modules.user.dto.AuthResponse;
import com.enterprise.tacticalsim.modules.user.dto.LoginRequest;
import com.enterprise.tacticalsim.modules.user.dto.RegisterRequest;
import com.enterprise.tacticalsim.modules.user.dto.TwoFactorChallengeResponse;
import com.enterprise.tacticalsim.modules.user.dto.VerifyTwoFactorRequest;
import com.enterprise.tacticalsim.modules.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "User registered successfully"));
    }

    // CAMBIO: ya no devuelve el JWT. Devuelve un "boleto" de 2FA
    // (tempToken + expiresInSeconds) que el frontend usa en el segundo
    // paso, /verify-2fa, para obtener el JWT real.
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TwoFactorChallengeResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        TwoFactorChallengeResponse response = authService.initiateLogin(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Credenciales válidas. Código de verificación generado."));
    }

    // NUEVO endpoint: segundo paso del login. Valida el código de 6
    // dígitos contra el tempToken emitido por /login y, si es correcto,
    // entrega el JWT real (AuthResponse), igual que antes devolvía /login.
    @PostMapping("/verify-2fa")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyTwoFactor(
            @Valid @RequestBody VerifyTwoFactorRequest request
    ) {
        AuthResponse response = authService.verifyTwoFactor(request.getTempToken(), request.getCode());
        return ResponseEntity.ok(ApiResponse.success(response, "Autenticación completada"));
    }
}