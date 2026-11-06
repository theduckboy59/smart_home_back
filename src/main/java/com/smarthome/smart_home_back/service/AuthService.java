package com.smarthome.smart_home_back.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.smarthome.smart_home_back.dto.LoginResponse;
import com.smarthome.smart_home_back.model.Usuario;
import com.smarthome.smart_home_back.repository.UsuarioRepository;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final SesionService sesionService;

    public AuthService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            SesionService sesionService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.sesionService = sesionService;
    }

    public LoginResponse autenticar(String nombre, String contrasena) {
        Usuario usuario = usuarioRepository.findByNombre(nombre)
                .orElse(null);

        if (usuario == null || Boolean.FALSE.equals(usuario.getActivo()) ||
                !passwordEncoder.matches(
                        contrasena,
                        usuario.getContrasena())) {
            return new LoginResponse(
                    false,
                    "Credenciales incorrectas",
                    null,
                    null,
                    null,
                    null
            );
        }

        String token = sesionService.crear(usuario);

        return new LoginResponse(
                true,
                "Autenticacion correcta",
                usuario.getId(),
                usuario.getNombre(),
                token,
                SesionService.EXPIRATION_SECONDS
        );
    }
}
