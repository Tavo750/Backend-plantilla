package com.plantilla.backend.modules.auth.service;

import com.plantilla.backend.infrastructure.security.JwtTokenProvider;
import com.plantilla.backend.modules.auth.dto.LoginRequest;
import com.plantilla.backend.modules.auth.dto.LoginResponse;
import com.plantilla.backend.modules.auth.dto.RegisterRequest;
import com.plantilla.backend.modules.auth.entity.Usuario;
import com.plantilla.backend.modules.auth.repository.UsuarioRepository;
import com.plantilla.backend.shared.errors.BusinessException;
import com.plantilla.backend.shared.helpers.StringHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación del servicio de autenticación.
 * Principio SOLID (S): Solo responsable de lógica de autenticación y registro.
 * Principio SOLID (L): Sustituible por cualquier implementación de AuthService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Override
    public LoginResponse login(LoginRequest request) {
        log.info("Intento de login para: {}", request.getCorreo());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getCorreo(), request.getContrasena()));

        String token = jwtTokenProvider.generateToken(authentication);

        Usuario usuario = usuarioRepository.findByCorreo(request.getCorreo())
                .orElseThrow(() -> new BusinessException("Usuario no encontrado"));

        return buildLoginResponse(token, usuario);
    }

    @Override
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        log.info("Registro de nuevo usuario: {}", request.getCorreo());

        if (usuarioRepository.existsByCorreo(request.getCorreo())) {
            throw new BusinessException("DUPLICATE_EMAIL", "El correo ya está registrado");
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(request.getNombre());
        usuario.setApellidoPaterno(request.getApellidoPaterno());
        usuario.setApellidoMaterno(request.getApellidoMaterno());
        usuario.setCorreo(request.getCorreo());
        usuario.setContrasena(passwordEncoder.encode(request.getContrasena()));
        usuario.setPuesto(request.getPuesto());
        usuario.setEstado(true);

        Usuario saved = usuarioRepository.save(usuario);
        String token = jwtTokenProvider.generateToken(saved.getCorreo());

        return buildLoginResponse(token, saved);
    }

    /**
     * Construye la respuesta de login con token y datos del usuario.
     */
    private LoginResponse buildLoginResponse(String token, Usuario usuario) {
        return LoginResponse.builder()
                .token(token)
                .tipo("Bearer")
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .apellidoPaterno(usuario.getApellidoPaterno())
                .apellidoMaterno(usuario.getApellidoMaterno())
                .nombreCompleto(StringHelper.buildNombreCompleto(
                        usuario.getNombre(), usuario.getApellidoPaterno(), usuario.getApellidoMaterno()))
                .correo(usuario.getCorreo())
                .puesto(usuario.getPuesto())
                .fotoUrl(usuario.getFotoUrl())
                .estado(usuario.getEstado())
                .build();
    }
}
