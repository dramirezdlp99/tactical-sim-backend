package com.enterprise.tacticalsim.modules.user.service;

import com.enterprise.tacticalsim.gateway.security.JwtService;
import com.enterprise.tacticalsim.modules.user.dto.AuthResponse;
import com.enterprise.tacticalsim.modules.user.dto.LoginRequest;
import com.enterprise.tacticalsim.modules.user.dto.RegisterRequest;
import com.enterprise.tacticalsim.modules.user.dto.TwoFactorChallengeResponse;
import com.enterprise.tacticalsim.modules.user.model.User;
import com.enterprise.tacticalsim.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final TwoFactorService twoFactorService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Este correo ya está registrado.");
        }

        User user = User.builder()
                .name(request.getName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                // CAMBIO: antes no se guardaba, el selector de "Alta
                // Enterprise" era puramente cosmético.
                .entityType(request.getEntityType())
                .active(true)
                .build();

        userRepository.save(user);

        // El registro deja al usuario autenticado de una vez (sin 2FA):
        // el 2FA protege el LOGIN posterior, no la creación de la cuenta.
        return buildAuthResponse(user);
    }

    /**
     * CAMBIO CLAVE: antes esto devolvía el JWT directamente. Ahora valida
     * las credenciales y, si son correctas, solo emite un "boleto" de 2FA
     * (TwoFactorChallengeResponse). El JWT real se entrega recién en
     * verifyTwoFactor(), después de validar el código de 6 dígitos.
     */
    public TwoFactorChallengeResponse initiateLogin(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        // Si llega aquí, las credenciales son válidas (si no, authenticate()
        // ya lanzó BadCredentialsException, capturado por GlobalExceptionHandler).
        return twoFactorService.issueChallenge(request.getEmail());
    }

    public AuthResponse verifyTwoFactor(String tempToken, String code) {
        String email = twoFactorService.validateAndConsume(tempToken, code);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        var orgUserDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .roles(user.getRole().name().replace("ROLE_", ""))
                .build();

        String jwtToken = jwtService.generateToken(orgUserDetails);

        return AuthResponse.builder()
                .accessToken(jwtToken)
                .tokenType("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .role(user.getRole().name())
                .entityType(user.getEntityType().name())
                .build();
    }
}