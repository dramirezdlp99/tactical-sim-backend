package com.enterprise.tacticalsim.gateway.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

    // Antes esta lista estaba quemada en el codigo (solo localhost:3000).
    // Ahora viene de application.properties (cors.allowed-origins), que a
    // su vez lee la variable de entorno CORS_ALLOWED_ORIGINS. Esto permite
    // agregar el dominio real del frontend desplegado (Vercel) sin tocar
    // ni recompilar el codigo -- solo cambiando la variable de entorno
    // en el panel de Render.
    @Value("${cors.allowed-origins}")
    private String corsAllowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Habilitar la configuración global de CORS definida abajo
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 2. Deshabilitar CSRF para arquitectura REST API Stateless
                .csrf(AbstractHttpConfigurer::disable)

                // 3. Permitir renderizar la consola de H2 Database en iframe
                //    OJO: /h2-console SOLO debe estar accesible en desarrollo local.
                //    Si este backend llega a desplegarse en algo alcanzable desde
                //    internet (no solo localhost), esta ruta debe deshabilitarse
                //    (spring.h2.console.enabled=false) porque permite ejecutar SQL
                //    arbitrario contra la base de datos desde el navegador.
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
                        // ROLE_ANALYST/ROLE_ADMIN para no bloquear pruebas).
                        // hasAnyRole() antepone "ROLE_" automáticamente, así que
                        // aquí se pasa "COACH"/"ANALYST"/"ADMIN", no "ROLE_COACH".
                        .requestMatchers(HttpMethod.POST, "/api/v1/simulation/**")
                        .hasAnyRole("COACH", "ANALYST", "ADMIN")

                        // === CAMBIO 3 (auditoría de seguridad) ===
                        // Antes, GET /api/v1/simulation/history y /history/export
                        // solo caían en el anyRequest().authenticated() genérico,
                        // es decir CUALQUIER usuario autenticado podía consultarlos
                        // aunque el botón "Ver Historial" estuviera oculto en el
                        // frontend para no-Analistas. Ocultar un botón no es control
                        // de acceso real: cualquiera con un token válido podía
                        // llamarlo directo con Postman/curl. Ahora se restringe
                        // explícitamente a los mismos roles que pueden guardar
                        // jugadas, ya que cada quien solo ve SU PROPIO historial
                        // (filtrado por email en SimulationHistoryService).
                        .requestMatchers(HttpMethod.GET, "/api/v1/simulation/history/**")
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
     *
     * Nota de seguridad: los orígenes están en una lista blanca explícita
     * (localhost:3000 / 127.0.0.1:3000), NO en "*". Con allowCredentials(true),
     * Spring ni siquiera dejaría usar "*" como origen -- esta configuración
     * ya está bien hecha, solo hay que recordar agregar aquí el dominio real
     * si algún día se despliega el frontend fuera de localhost.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of(corsAllowedOrigins.split(",")));
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