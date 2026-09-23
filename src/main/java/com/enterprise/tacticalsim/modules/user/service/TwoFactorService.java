package com.enterprise.tacticalsim.modules.user.service;

import com.enterprise.tacticalsim.modules.user.dto.TwoFactorChallengeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servicio de 2FA en memoria (suficiente para una sola instancia, como
 * este proyecto académico con H2 embebido). Si el backend se reinicia,
 * cualquier código pendiente se pierde -- el usuario simplemente vuelve
 * a iniciar sesión y se emite uno nuevo.
 *
 * IMPORTANTE - lo que falta para producción real: aquí es donde se
 * conectaría un proveedor de SMS/email (Twilio, AWS SNS, SendGrid, etc.)
 * para ENVIAR el código al usuario. Por ahora, sin esa integración, el
 * código se imprime en el log del backend (consola de IntelliJ) para que
 * puedas probar el flujo completo end-to-end en desarrollo.
 *
 * SEGURIDAD - limite de intentos: un código de 6 dígitos tiene solo
 * 1.000.000 de combinaciones. Sin límite de intentos, alguien con el
 * tempToken (por ejemplo interceptado o adivinado) podría probar fuerza
 * bruta contra /verify-2fa dentro de la ventana de 5 minutos. Por eso
 * cada challenge ahora cuenta sus intentos fallidos y se invalida
 * después de MAX_ATTEMPTS, obligando a reiniciar el login.
 */
@Slf4j
@Service
public class TwoFactorService {

    private static final long CODE_TTL_SECONDS = 300; // 5 minutos
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, PendingChallenge> pendingChallenges = new ConcurrentHashMap<>();

    private record PendingChallenge(String email, String code, Instant expiresAt, AtomicInteger attempts) {}

    public TwoFactorChallengeResponse issueChallenge(String email) {
        cleanupExpired();

        String tempToken = UUID.randomUUID().toString();
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        Instant expiresAt = Instant.now().plusSeconds(CODE_TTL_SECONDS);

        pendingChallenges.put(tempToken, new PendingChallenge(email, code, expiresAt, new AtomicInteger(0)));

        log.info("[2FA] Codigo generado para {}: {} (expira en {}s). " +
                        "En produccion esto se enviaria por SMS/email, no por log.",
                email, code, CODE_TTL_SECONDS);

        return TwoFactorChallengeResponse.builder()
                .tempToken(tempToken)
                .expiresInSeconds(CODE_TTL_SECONDS)
                .build();
    }

    /**
     * Valida el código y, si es correcto, lo consume (un solo uso).
     * Lanza IllegalArgumentException con un mensaje apto para mostrar
     * al usuario -- GlobalExceptionHandler lo convierte en un 400 con
     * el JSON { success:false, message } que el frontend ya sabe leer.
     */
    public String validateAndConsume(String tempToken, String code) {
        PendingChallenge challenge = pendingChallenges.get(tempToken);

        if (challenge == null) {
            throw new IllegalArgumentException("Código inválido o ya utilizado. Vuelve a iniciar sesión.");
        }
        if (Instant.now().isAfter(challenge.expiresAt())) {
            pendingChallenges.remove(tempToken);
            throw new IllegalArgumentException("El código ha expirado. Vuelve a iniciar sesión.");
        }

        int attemptsSoFar = challenge.attempts().incrementAndGet();
        if (attemptsSoFar > MAX_ATTEMPTS) {
            pendingChallenges.remove(tempToken);
            log.warn("[2FA] Demasiados intentos fallidos para {}, challenge invalidado.", challenge.email());
            throw new IllegalArgumentException("Demasiados intentos fallidos. Vuelve a iniciar sesión.");
        }

        if (!challenge.code().equals(code)) {
            int remaining = MAX_ATTEMPTS - attemptsSoFar;
            throw new IllegalArgumentException("Código incorrecto. Intentos restantes: " + remaining);
        }

        pendingChallenges.remove(tempToken);
        return challenge.email();
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        pendingChallenges.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    }
}