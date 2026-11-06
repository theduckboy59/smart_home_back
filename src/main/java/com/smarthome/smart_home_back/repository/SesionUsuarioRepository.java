package com.smarthome.smart_home_back.repository;

import com.smarthome.smart_home_back.model.SesionUsuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface SesionUsuarioRepository extends JpaRepository<SesionUsuario, Long> {
    Optional<SesionUsuario> findByTokenHash(String tokenHash);

    List<SesionUsuario> findAllByUsuario_Id(Long usuarioId);
}
