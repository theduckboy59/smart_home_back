
package com.smarthome.smart_home_back.service;

import com.smarthome.smart_home_back.model.Dispositivo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DispositivoService {

    private final List<Dispositivo> dispositivos = new ArrayList<>();
    private Long siguienteId = 1L;

    public List<Dispositivo> listar() {
        return dispositivos;
    }

    public Dispositivo buscarPorId(Long id) {
        return dispositivos.stream()
                .filter(dispositivo -> dispositivo.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public Dispositivo crear(Dispositivo dispositivo) {
        dispositivo.setId(siguienteId++);
        dispositivos.add(dispositivo);
        return dispositivo;
    }

    public Dispositivo actualizar(Long id, Dispositivo datos) {
        Dispositivo dispositivo = buscarPorId(id);

        if (dispositivo == null) {
            return null;
        }

        dispositivo.setNombre(datos.getNombre());
        dispositivo.setEstado(datos.getEstado());

        return dispositivo;
    }

    public boolean eliminar(Long id) {
        return dispositivos.removeIf(
                dispositivo -> dispositivo.getId().equals(id)
        );
    }
}
