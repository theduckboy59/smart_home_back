package com.smarthome.smart_home_back.service;

import com.smarthome.smart_home_back.dto.ActualizarUsuarioRequest;
import com.smarthome.smart_home_back.dto.CrearUsuarioRequest;
import com.smarthome.smart_home_back.dto.UsuarioResponse;
import com.smarthome.smart_home_back.model.Usuario;
import com.smarthome.smart_home_back.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class UsuarioService {
    private static final String PIN_PATTERN = "^[1-9A-D]{4}$";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final SesionService sesiones;

    public UsuarioService(UsuarioRepository usuarios,
                          PasswordEncoder encoder,
                          SesionService sesiones) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.sesiones = sesiones;
    }

    @Transactional
    public synchronized UsuarioResponse crear(CrearUsuarioRequest request, Usuario actor) {
        requireAdmin(actor);
        String nombre = request.nombre().trim();
        if (usuarios.existsByNombre(nombre)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El nombre ya está registrado");
        }
        asegurarPinPuertaUnico(request.passwordpuerta(), null);

        Usuario usuario = new Usuario();
        usuario.setNombre(nombre);
        usuario.setContrasena(encoder.encode(request.contrasena()));
        usuario.setPasswordapp(encoder.encode(request.passwordapp()));
        usuario.setPasswordpuerta(encoder.encode(request.passwordpuerta()));
        usuario.setAdministrador(false);
        usuario.setActivo(true);
        return response(usuarios.save(usuario));
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar(Usuario actor) {
        requireAdmin(actor);
        return usuarios.findAllByOrderByIdAsc().stream().map(UsuarioService::response).toList();
    }

    @Transactional(readOnly = true)
    public UsuarioResponse buscar(Long id, Usuario actor) {
        requireOwnerOrAdmin(id, actor);
        return response(find(id));
    }

    @Transactional
    public synchronized UsuarioResponse actualizar(Long id, ActualizarUsuarioRequest request, Usuario actor) {
        requireOwnerOrAdmin(id, actor);
        Usuario usuario = find(id);
        boolean changed = false;

        if (request.nombre() != null) {
            String nombre = request.nombre().trim();
            if (nombre.isBlank()) throw badRequest("El nombre no puede estar vacío");
            if (!nombre.equals(usuario.getNombre()) && usuarios.existsByNombre(nombre)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "El nombre ya está registrado");
            }
            usuario.setNombre(nombre);
            changed = true;
        }
        if (request.contrasena() != null) {
            if (request.contrasena().isBlank()) throw badRequest("La contraseña no puede estar vacía");
            usuario.setContrasena(encoder.encode(request.contrasena()));
            sesiones.revocarDeUsuario(id);
            changed = true;
        }
        if (request.passwordapp() != null) {
            validatePin(request.passwordapp());
            usuario.setPasswordapp(encoder.encode(request.passwordapp()));
            changed = true;
        }
        if (request.passwordpuerta() != null) {
            validatePin(request.passwordpuerta());
            asegurarPinPuertaUnico(request.passwordpuerta(), id);
            usuario.setPasswordpuerta(encoder.encode(request.passwordpuerta()));
            changed = true;
        }
        if (!changed) throw badRequest("No se recibieron campos para actualizar");
        return response(usuarios.save(usuario));
    }

    @Transactional
    public void eliminar(Long id, Usuario actor) {
        requireAdmin(actor);
        Usuario target = find(id);
        sesiones.revocarDeUsuario(id);
        usuarios.delete(target);
    }

    @Transactional(readOnly = true)
    public UsuarioResponse miCuenta(Usuario usuario) {
        return response(find(usuario.getId()));
    }

    private Usuario find(Long id) {
        return usuarios.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    private void requireOwnerOrAdmin(Long id, Usuario actor) {
        if (!isAdmin(actor) && !actor.getId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes consultar o actualizar tu propia cuenta");
        }
    }

    private static void requireAdmin(Usuario actor) {
        if (!isAdmin(actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere una cuenta administradora");
        }
    }

    private static boolean isAdmin(Usuario actor) {
        return Boolean.TRUE.equals(actor.getAdministrador());
    }

    private void asegurarPinPuertaUnico(String pin, Long excludingId) {
        validatePin(pin);
        boolean duplicate = usuarios.findAll().stream()
                .filter(user -> user.getPasswordpuerta() != null)
                .filter(user -> excludingId == null || !user.getId().equals(excludingId))
                .anyMatch(user -> encoder.matches(pin, user.getPasswordpuerta()));
        if (duplicate) throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese PIN de puerta ya está asignado");
    }

    private static void validatePin(String pin) {
        if (pin == null || !pin.matches(PIN_PATTERN)) {
            throw badRequest("La contraseña debe tener cuatro caracteres A-D o 1-9");
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static UsuarioResponse response(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombre(),
                usuario.getPasswordapp() != null, usuario.getPasswordpuerta() != null);
    }
}
