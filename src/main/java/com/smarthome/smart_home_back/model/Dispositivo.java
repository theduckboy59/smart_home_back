
package com.smarthome.smart_home_back.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Dispositivo {

    private Long id;
    private String nombre;
    private String estado;
}
