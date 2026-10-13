package com.enterprise.tacticalsim.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Cliente HTTP hacia el motor de IA.
 *
 * Capa de IA "detras" del backend: si la variable de entorno AI_INTERNAL_KEY
 * esta definida, cada llamada lleva la cabecera X-Internal-Key con ese secreto
 * compartido, y el motor de IA rechaza (401) lo que no la traiga. Asi el
 * navegador no puede llamar a la IA directamente aunque conozca su URL.
 * Si la variable no existe, no se envia nada (comportamiento anterior).
 */
@Configuration
public class WebClientConfig {

    public static final String INTERNAL_KEY_HEADER = "X-Internal-Key";

    @Value("${ai.engine.url:http://localhost:8000}")
    private String aiEngineUrl;

    @Value("${AI_INTERNAL_KEY:}")
    private String aiInternalKey;

    @Bean
    public WebClient aiEngineWebClient() {
        WebClient.Builder builder = WebClient.builder().baseUrl(aiEngineUrl);
        if (aiInternalKey != null && !aiInternalKey.isBlank()) {
            builder.defaultHeader(INTERNAL_KEY_HEADER, aiInternalKey.trim());
        }
        return builder.build();
    }
}
