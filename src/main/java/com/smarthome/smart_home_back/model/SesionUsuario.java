package com.smarthome.smart_home_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "sesiones_usuario", indexes = {
        @Index(name = "idx_sesion_token_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_sesion_usuario", columnList = "usuario_id")
})
@Getter
@Setter
@NoArgsConstructor
public class SesionUsuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @Column(name = "ultima_actividad", nullable = false)
    private Instant ultimaActividad;

    @Column(name = "vence_en", nullable = false)
    private Instant venceEn;

    @Column(name = "revocada_en")
    private Instant revocadaEn;
}
