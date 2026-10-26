package com.smarthome.smart_home_back.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.smarthome.smart_home_back.model.Usuario;
import com.smarthome.smart_home_back.repository.UsuarioRepository;

@Component
public class UsuarioInicializador implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.initial-user-name:}")
    private String nombreInicial;

    @Value("${app.initial-user-password:}")
    private String contrasenaInicial;

    public UsuarioInicializador(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (nombreInicial == null || nombreInicial.isBlank()
                || contrasenaInicial == null
                || contrasenaInicial.isBlank()) {
            return;
        }

        if (usuarioRepository.existsByNombre(nombreInicial)) {
            return;
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(nombreInicial);
        usuario.setContrasena(
                passwordEncoder.encode(contrasenaInicial)
        );

        usuarioRepository.save(usuario);

        System.out.println("Usuario inicial creado correctamente.");
    }
}