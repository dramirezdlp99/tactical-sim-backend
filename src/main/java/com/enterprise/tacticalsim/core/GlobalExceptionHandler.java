package com.enterprise.tacticalsim.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * NOTA: si ya tienes un @RestControllerAdvice en el proyecto, mándamelo y
 * no reemplaces este archivo -- podrían chocar (Spring no permite dos
 * manejadores para la misma excepción). Este archivo existe porque, sin
 * uno, una IllegalArgumentException (ej. "email ya registrado", "código
 * 2FA incorrecto") sale como un error 500 genérico de Spring Boot en vez
 * del JSON { success, message, data } que AuthScreen/RegisterScreen ya
 * esperan leer en json.message.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Correo o contraseña incorrectos."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Datos inválidos en la solicitud.");
        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Error no controlado", ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Error interno del servidor. Intenta nuevamente."));
    }
}