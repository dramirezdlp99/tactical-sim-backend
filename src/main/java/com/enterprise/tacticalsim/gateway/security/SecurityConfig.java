package com.enterprise.tacticalsim.gateway.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Habilitar la configuración global de CORS definida abajo
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 2. Deshabilitar CSRF para arquitectura REST API Stateless
                .csrf(AbstractHttpConfigurer::disable)

                // 3. Permitir renderizar la consola de H2 Database en iframe
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))

                // 4. Configurar reglas de autorización de rutas
                .authorizeHttpRequests(auth -> auth
                        // === CAMBIO 1 (causa real del 403) ===
                        // El navegador manda un preflight OPTIONS antes de todo POST
                        // "no simple" (con JSON + Authorization). Ese preflight NUNCA
                        // trae el header Authorization, así que si no se permite
                        // explícitamente, cae en anyRequest().authenticated() y
                        // Spring Security lo rechaza antes de que tu JS vea la
                        // respuesta real -> el navegador lo reporta como 403.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/h2-console/**",
                                "/actuator/**"
                        ).permitAll()

                        // === CAMBIO 2 ===
                        // Matcher explícito para /simulation/run con los roles reales
                        // que ya existen en tu enum Role (ROLE_COACH, y agregamos
                        // ROLE_ADMIN para no bloquear pruebas desde ese rol).
                        // hasAnyRole() antepone "ROLE_" automáticamente, así que
                        // aquí se pasa "COACH"/"ADMIN", no "ROLE_COACH".
                        .requestMatchers(HttpMethod.POST, "/api/v1/simulation/**")
                        .hasAnyRole("COACH", "ANALYST", "ADMIN")

                        .anyRequest().authenticated()
                )

                // 5. Gestión de sesión sin estado (Stateless)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 6. Proveedor de autenticación y Filtro JWT
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Define la política CORS explícita para permitir que React (puerto 3000)
     * consuma la API REST y envíe headers de autenticación JWT.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of("http://localhost:3000", "http://127.0.0.1:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}