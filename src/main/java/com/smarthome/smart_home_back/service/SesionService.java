package com.smarthome.smart_home_back.service;

import com.smarthome.smart_home_back.model.SesionUsuario;
import com.smarthome.smart_home_back.model.Usuario;
import com.smarthome.smart_home_back.repository.SesionUsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class SesionService {
    public static final long EXPIRATION_SECONDS = 1800;
    private static final Duration LIFETIME = Duration.ofSeconds(EXPIRATION_SECONDS);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SesionUsuarioRepository repository;

    public SesionService(SesionUsuarioRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public String crear(Usuario usuario) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();

        SesionUsuario session = new SesionUsuario();
        session.setUsuario(usuario);
        session.setTokenHash(hash(token));
        session.setCreadaEn(now);
        session.setUltimaActividad(now);
        session.setVenceEn(now.plus(LIFETIME));
        repository.save(session);
        return token;
    }

    @Transactional
    public Optional<Usuario> validarYRenovar(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        Optional<SesionUsuario> found = repository.findByTokenHash(hash(token));
        if (found.isEmpty()) return Optional.empty();

        SesionUsuario session = found.get();
        Instant now = Instant.now();
        if (session.getRevocadaEn() != null || !session.getVenceEn().isAfter(now)) {
            if (session.getRevocadaEn() == null) {
                session.setRevocadaEn(now);
                repository.save(session);
            }
            return Optional.empty();
        }

        session.setUltimaActividad(now);
        session.setVenceEn(now.plus(LIFETIME));
        repository.save(session);
        Usuario usuario = session.getUsuario();
        // Initialize the lazy relation before returning beyond this transaction.
        usuario.getNombre();
        if (Boolean.FALSE.equals(usuario.getActivo())) {
            session.setRevocadaEn(now);
            repository.save(session);
            return Optional.empty();
        }
        return Optional.of(usuario);
    }

    @Transactional
    public void revocar(String token) {
        if (token == null || token.isBlank()) return;
        repository.findByTokenHash(hash(token)).ifPresent(session -> {
            if (session.getRevocadaEn() == null) {
                session.setRevocadaEn(Instant.now());
                repository.save(session);
            }
        });
    }

    @Transactional
    public void revocarDeUsuario(Long usuarioId) {
        List<SesionUsuario> sessions = repository.findAllByUsuario_Id(usuarioId);
        Instant now = Instant.now();
        sessions.stream().filter(s -> s.getRevocadaEn() == null)
                .forEach(s -> s.setRevocadaEn(now));
        repository.saveAll(sessions);
    }

    public String extraerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return null;
        String token = authorization.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo procesar el token de sesión", e);
        }
    }
}
