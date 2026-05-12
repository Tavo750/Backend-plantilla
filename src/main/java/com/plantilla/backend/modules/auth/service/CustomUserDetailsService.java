package com.plantilla.backend.modules.auth.service;

import com.plantilla.backend.modules.auth.entity.Usuario;
import com.plantilla.backend.modules.auth.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Implementación de UserDetailsService para Spring Security.
 * Principio SOLID (S): Solo responsable de cargar datos del usuario para autenticación.
 * Principio SOLID (D): Spring Security depende de la abstracción UserDetailsService.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String correo) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario no encontrado con correo: " + correo));

        return new User(
                usuario.getCorreo(),
                usuario.getContrasena(),
                usuario.getEstado(),    // enabled
                true,                   // accountNonExpired
                true,                   // credentialsNonExpired
                true,                   // accountNonLocked
                Collections.emptyList() // authorities
        );
    }
}
