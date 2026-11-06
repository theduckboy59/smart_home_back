package com.smarthome.smart_home_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "eventos_seguridad", indexes = {
        @Index(name = "idx_evento_fecha", columnList = "fecha")
})
@Getter
@Setter
@NoArgsConstructor
public class EventoSeguridad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant fecha;

    @Column(nullable = false, length = 50)
    private String tipo;

    @Column(nullable = false, length = 30)
    private String resultado;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "distancia_cm")
    private Integer distanciaCm;

    @Column(name = "request_id", length = 36)
    private String requestId;

    @Column(length = 255)
    private String detalle;
}
