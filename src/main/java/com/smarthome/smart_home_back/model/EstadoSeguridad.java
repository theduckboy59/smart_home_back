package com.smarthome.smart_home_back.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "estado_seguridad")
@Getter
@Setter
@NoArgsConstructor
public class EstadoSeguridad {
    @Id
    private Long id = 1L;

    @Column(nullable = false, length = 40)
    private String estado = "NORMAL";

    @Column(name = "alarma_activa", nullable = false)
    private boolean alarmaActiva;

    @Column(name = "puerta_bloqueada", nullable = false)
    private boolean puertaBloqueada;

    @Column(name = "presencia", nullable = false)
    private boolean presencia;

    @Column(name = "distancia_cm")
    private Integer distanciaCm;

    @Column(name = "presencia_desde")
    private Instant presenciaDesde;

    @Column(name = "ultima_medicion_valida")
    private Instant ultimaMedicionValida;

    @Column(name = "estado_desde")
    private Instant estadoDesde;

    @Column(name = "tiempo_presencia_ms", nullable = false)
    private long tiempoPresenciaMs;

    @Column(name = "advertencia_enviada", nullable = false)
    private boolean advertenciaEnviada;
}
