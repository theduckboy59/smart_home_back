package com.smarthome.smart_home_back.repository;

import com.smarthome.smart_home_back.model.EstadoSeguridad;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstadoSeguridadRepository extends JpaRepository<EstadoSeguridad, Long> {
}
