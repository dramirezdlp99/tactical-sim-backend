package com.enterprise.tacticalsim.gateway.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Este archivo NO existía en el proyecto. Sin él, SecurityConfig no podía
 * inyectar "AuthenticationProvider" (constructor generado por
 * @RequiredArgsConstructor) y la aplicación debería fallar al arrancar con
 * un error de tipo "NoSuchBeanDefinitionException: AuthenticationProvider".
 *
 * Aquí se conectan las tres piezas que le daban vida a la autenticación:
 * CustomUserDetailsService (busca el usuario), PasswordEncoder (compara el
 * hash) y el AuthenticationManager que AuthService.login() ya invoca.
 */
@Configuration
@RequiredArgsConstructor
public class ApplicationConfig {

    private final UserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Debe ser el MISMO encoder usado en AuthService.register()
        // (passwordEncoder.encode(request.getPassword())), si no,
        // el login nunca podrá validar contraseñas ya guardadas.
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}