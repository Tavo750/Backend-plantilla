package com.plantilla.backend.modules.auth.service;

import com.plantilla.backend.modules.auth.dto.LoginRequest;
import com.plantilla.backend.modules.auth.dto.LoginResponse;
import com.plantilla.backend.modules.auth.dto.RegisterRequest;

/**
 * Interfaz del servicio de autenticación.
 * Principio SOLID (D): Inversión de dependencias — los controllers dependen de esta abstracción.
 * Principio SOLID (I): Interfaz segregada solo para operaciones de autenticación.
 */
public interface AuthService {

    /**
     * Autentica un usuario y retorna un token JWT.
     */
    LoginResponse login(LoginRequest request);

    /**
     * Registra un nuevo usuario en el sistema.
     */
    LoginResponse register(RegisterRequest request);
}
