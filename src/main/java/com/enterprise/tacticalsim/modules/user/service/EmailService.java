package com.enterprise.tacticalsim.modules.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Envío real del código de 2FA por correo, usando la API HTTP de Brevo
 * (https://api.brevo.com/v3/smtp/email) en vez de SMTP tradicional.
 *
 * CAMBIO IMPORTANTE (de SMTP a API HTTP): la primera versión de este
 * servicio usaba JavaMailSender por SMTP (puerto 465/587). Eso funcionaba
 * en local, pero al desplegar en Render los correos dejaban de llegar --
 * Render (como la mayoria de hosts gratuitos) bloquea conexiones salientes
 * por SMTP para evitar que se use para enviar spam. La API HTTP de Brevo
 * en cambio es una simple peticion HTTPS (puerto 443, el mismo que usa
 * cualquier pagina web normal) -- eso nunca lo bloquea ningun host, ni
 * tampoco lo intercepta un antivirus como si pasaba con SMTP+TLS en
 * desarrollo local. Un solo mecanismo que funciona igual en los dos
 * entornos, sin necesitar la distincion STARTTLS/SSL/puerto 465 vs 587
 * que se manejaba antes.
 *
 * Diseño deliberado: si NO hay una API key configurada (app.mail.enabled
 * = false, el valor de respaldo), este servicio cae automáticamente al
 * comportamiento anterior -- imprime el código en el log del backend.
 * Esto es intencional para que el proyecto siga corriendo "de una" al
 * clonarlo sin necesitar cuenta de Brevo.
 *
 * SEGURIDAD: la API key de Brevo es la credencial real de una cuenta de
 * terceros -- nunca tiene un valor de respaldo real en
 * application.properties, solo queda vacía por defecto. Se configura
 * exclusivamente como variable de entorno (local o en el panel de la
 * plataforma de despliegue), nunca commiteada.
 */
@Slf4j
@Service
public class EmailService {

    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:no-reply@tacticalsim.local}")
    private String fromAddress;

    @Value("${brevo.api.key:}")
    private String brevoApiKey;

    public void sendTwoFactorCode(String toEmail, String code, long expiresInSeconds) {
        if (!mailEnabled) {
            log.info("[2FA] (modo consola -- MAIL_ENABLED no esta activo) Codigo para {}: {} (expira en {}s)",
                    toEmail, code, expiresInSeconds);
            return;
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("sender", Map.of("email", fromAddress, "name", "TacticAI Sports Simulator"));
            body.put("to", List.of(Map.of("email", toEmail)));
            body.put("subject", "Tu código de verificación - TacticAI Sports Simulator");
            body.put("textContent",
                    "Tu código de acceso es: " + code + "\n\n" +
                            "Expira en " + (expiresInSeconds / 60) + " minutos.\n\n" +
                            "Si no intentaste iniciar sesión en TacticAI, ignora este correo."
            );

            String jsonBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BREVO_API_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("api-key", brevoApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("[2FA] Codigo enviado por correo (API Brevo) a {}", toEmail);
            } else {
                // No tumbamos el login si Brevo responde con error (API key mal
                // puesta, limite de envios alcanzado, etc.) -- dejamos el codigo
                // visible en el log como respaldo para que puedas seguir
                // depurando sin quedar bloqueado.
                log.error("[2FA] Brevo respondio {} al enviar a {}. Cuerpo: {}. Codigo (solo depuracion): {}",
                        response.statusCode(), toEmail, response.body(), code);
            }
        } catch (Exception e) {
            log.error("[2FA] Fallo el envio de correo a {}. Codigo (solo para depuracion): {}",
                    toEmail, code, e);
        }
    }
}