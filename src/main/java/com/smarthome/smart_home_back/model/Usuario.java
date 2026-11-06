package com.smarthome.smart_home_back.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    @Column(nullable = false, length = 255)
    private String contrasena;

    @Column(name = "passwordapp", length = 255)
    private String passwordapp;

    @Column(name = "passwordpuerta", length = 255)
    private String passwordpuerta;

    // Nullable to keep the schema update compatible with existing rows.
    @Column(name = "administrador")
    private Boolean administrador = false;

    // Existing records with NULL remain enabled during the schema migration.
    @Column(name = "activo")
    private Boolean activo = true;
}
