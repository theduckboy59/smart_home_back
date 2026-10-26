
package com.smarthome.smart_home_back.controller;

import com.smarthome.smart_home_back.model.Dispositivo;
import com.smarthome.smart_home_back.service.DispositivoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dispositivos")
@RequiredArgsConstructor
public class DispositivoController {

    private final DispositivoService dispositivoService;

    @GetMapping
    public List<Dispositivo> listar() {
        return dispositivoService.listar();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Dispositivo> buscarPorId(@PathVariable Long id) {
        Dispositivo dispositivo = dispositivoService.buscarPorId(id);

        if (dispositivo == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(dispositivo);
    }

    @PostMapping
    public ResponseEntity<Dispositivo> crear(
            @RequestBody Dispositivo dispositivo) {

        Dispositivo nuevo = dispositivoService.crear(dispositivo);

        return ResponseEntity.status(HttpStatus.CREATED).body(nuevo);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Dispositivo> actualizar(
            @PathVariable Long id,
            @RequestBody Dispositivo datos) {

        Dispositivo actualizado =
                dispositivoService.actualizar(id, datos);

        if (actualizado == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(actualizado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        boolean eliminado = dispositivoService.eliminar(id);

        if (!eliminado) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.noContent().build();
    }
}
