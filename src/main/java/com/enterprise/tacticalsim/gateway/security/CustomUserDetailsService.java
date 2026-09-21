package com.enterprise.tacticalsim.gateway.security;

import com.enterprise.tacticalsim.modules.user.model.User;
import com.enterprise.tacticalsim.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Pieza que faltaba por completo en el proyecto: sin esta clase,
 * AuthenticationProvider no tiene forma de verificar credenciales
 * contra la base de datos, y JwtAuthenticationFilter no puede
 * reconstruir el "principal" a partir del email guardado en el JWT.
 *
 * En este sistema el "username" de Spring Security ES el email,
 * porque User.java no tiene un campo "username" separado. Por eso se
 * busca siempre por email, igual que ya hace AuthService.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No existe un usuario con el email: " + email));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .roles(user.getRole().name().replace("ROLE_", ""))
                .disabled(!user.isActive())
                .build();
    }
}