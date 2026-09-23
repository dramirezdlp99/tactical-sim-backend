package com.enterprise.tacticalsim.modules.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envío real del código de 2FA por correo (Brevo/SMTP).
 *
 * Diseño deliberado: si NO hay credenciales SMTP configuradas
 * (app.mail.enabled=false, que es el valor de respaldo), este servicio
 * cae automáticamente al comportamiento anterior -- imprime el código
 * en el log del backend. Esto es intencional para que el proyecto siga
 * corriendo "de una" al clonarlo sin necesitar cuenta de Brevo, pero en
 * cuanto configures las variables de entorno reales, empieza a enviar
 * correos de verdad sin tocar código.
 *
 * SEGURIDAD: a diferencia del JWT secret (que rotamos y sí puede vivir
 * en el repo porque es un valor propio generado), las credenciales SMTP
 * son la contraseña de una cuenta real de terceros (Brevo) -- estas
 * NUNCA deben tener un valor de respaldo real en application.properties,
 * solo quedan vacías por defecto. Se configuran exclusivamente como
 * variables de entorno (locales o en el panel de la plataforma de
 * despliegue), nunca commiteadas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:no-reply@tacticalsim.local}")
    private String fromAddress;

    public void sendTwoFactorCode(String toEmail, String code, long expiresInSeconds) {
        if (!mailEnabled) {
            log.info("[2FA] (modo consola -- MAIL_ENABLED no esta activo) Codigo para {}: {} (expira en {}s)",
                    toEmail, code, expiresInSeconds);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject("Tu código de verificación - TacticAI Sports Simulator");
            message.setText(
                    "Tu código de acceso es: " + code + "\n\n" +
                            "Expira en " + (expiresInSeconds / 60) + " minutos.\n\n" +
                            "Si no intentaste iniciar sesión en TacticAI, ignora este correo."
            );
            mailSender.send(message);
            log.info("[2FA] Codigo enviado por correo a {}", toEmail);
        } catch (Exception e) {
            // Si Brevo falla (credenciales mal puestas, limite alcanzado, etc.)
            // no tumbamos el login -- lo dejamos visible en el log como respaldo
            // para que puedas seguir depurando sin quedar bloqueado.
            log.error("[2FA] Fallo el envio de correo a {}. Codigo (solo para depuracion): {}",
                    toEmail, code, e);
        }
    }
}