package com.smarthome.smart_home_back.repository;

import com.smarthome.smart_home_back.model.EventoSeguridad;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoSeguridadRepository extends JpaRepository<EventoSeguridad, Long> {
}
