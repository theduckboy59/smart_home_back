# Especificación técnica para actualizar el backend Smart Home

**Objetivo:** ampliar tu backend Java Spring Boot para administrar usuarios, validar las contraseñas de la puerta y la alarma, procesar el sensor ultrasónico y enviar órdenes MQTT al ESP32.

**Base de referencia:** `backend.txt`, `arduinouno_actualizado.ino` y `esp32_actualizado.ino` compartidos previamente.

---

## 1. Reglas generales del desarrollo

| Elemento | Instrucción |
|---|---|
| Lenguaje | Java 17 |
| Framework | Spring Boot |
| Base de datos | PostgreSQL, `smart_home` |
| Comunicación | MQTT mediante Eclipse Paho |
| Broker | Mosquitto del servidor Ubuntu |
| Puerto MQTT | 1883 |
| Comunicación de seguridad | Mensajes JSON |
| Persistencia | Spring Data JPA e Hibernate |
| Autenticación | Usuario, contraseña y sesión autenticada |
| Protección de contraseñas | BCrypt |
| Configuración | Conservar `application.yaml` |
| Dependencias | Conservar `pom.xml` |
| Funcionalidades existentes | Mantenerlas, sin eliminar sus API |

El procesamiento de contraseñas y las decisiones de seguridad corresponden al backend. Los dispositivos únicamente entregan mediciones, reciben órdenes y reportan resultados.

## 2. Contrato MQTT que ya utilizan los nuevos programas

**El backend debe adaptarse al formato JSON actual del ESP32**, no inventar otro protocolo.

| Tópico | Dirección | Uso |
|---|---|---|
| `smart_home/esp32/event` | ESP32 → servidor | PIN físico y distancia |
| `smart_home/esp32/status` | ESP32 → servidor | Estado general |
| `smart_home/esp32/availability` | ESP32 → servidor | Conexión o desconexión |
| `smart_home/esp32/command` | Servidor → ESP32 | Orden de ejecución |
| `smart_home/esp32/ack` | ESP32 → servidor | Resultado de la orden |

### A. PIN introducido en el teclado físico

El ESP32 envía:

```json
{
  "tipo": "pin_puerta",
  "codigo": "AB12"
}
```

El backend debe recibir `codigo`, comprobar que contiene cuatro caracteres válidos y buscar una coincidencia con `passwordpuerta`.

**Este evento abre la puerta únicamente cuando el backend lo autoriza.**

### B. Medición ultrasónica válida

```json
{
  "tipo": "distancia",
  "valida": true,
  "cm": 12
}
```

El backend debe interpretar `12` como una distancia de 12 centímetros, dentro de la zona crítica de 15 cm.

Cuando la medición no es válida, el ESP32 envía:

```json
{
  "tipo": "distancia",
  "valida": false,
  "cm": -1
}
```

En ese caso, el backend no debe interpretar `-1` como distancia real ni utilizarlo para disparar una alarma por proximidad.

### C. Estado general del dispositivo

El JSON actual del ESP32 es:

```json
{
  "tipo": "estado",
  "version": 1,
  "uno1": {
    "online": true,
    "puerta": { "estado": 0 },
    "alarma": { "estado": 0 }
  },
  "uno2": {
    "online": true,
    "audio": { "pista": 0 }
  }
}
```

| Campo | Significado |
|---|---|
| `uno1.online` | Comunicación con Arduino |
| `uno1.puerta.estado` | `0` cerrada; `1` abierta |
| `uno1.alarma.estado` | `0` apagada; `1` activa |
| `uno2.online` | Alias lógico de disponibilidad del mismo Arduino |
| `uno2.audio.pista` | Última pista registrada por ESP32 |

**No hay dos Arduino físicos.** El firmware utiliza `uno1` y `uno2` para conservar compatibilidad con la estructura del backend.

### D. Orden enviada por el servidor

```json
{
  "request_id": "550e8400-e29b-41d4-a716-446655440000",
  "id": "puerta",
  "accion": "estado",
  "valor": 1
}
```

Condiciones obligatorias:

- `request_id`: UUID de 36 caracteres, único por solicitud.
- `id`: nombre exacto del componente.
- `accion`: operación solicitada.
- `valor`: número entero JSON, no una contraseña.
- No insertar el PIN dentro del comando.

### E. Confirmación recibida del ESP32

```json
{
  "tipo": "ack",
  "request_id": "550e8400-e29b-41d4-a716-446655440000",
  "id": "puerta",
  "resultado": "aplicado",
  "detalle": "Orden procesada por Arduino"
}
```

| Resultado | Interpretación |
|---|---|
| `aplicado` | Arduino procesó la orden |
| `rechazado` | Arduino rechazó la orden |
| `incierto` | No se obtuvo confirmación concluyente |

El backend solo debe asociar una confirmación a la orden cuyo `request_id` e `id` coincidan exactamente.

**No debe considerar una operación física exitosa solo porque publicó un mensaje MQTT.**

---

## 3. Modificar la entidad `Usuario`

Archivo existente:

`src/main/java/com/smarthome/smart_home_back/model/Usuario.java`

Actualmente contiene `id`, `nombre` y `contrasena`.

| Columna PostgreSQL | Tipo Java | Condición |
|---|---|---|
| `id` | `Long` | Clave primaria autogenerada |
| `nombre` | `String` | Obligatorio y único |
| `contrasena` | `String` | Contraseña BCrypt del login |
| `passwordapp` | `String` | Hash BCrypt para desactivar la alarma |
| `passwordpuerta` | `String` | Hash BCrypt para abrir la puerta |

### Reglas de las contraseñas

`contrasena` conservará su funcionamiento actual.

`passwordapp` y `passwordpuerta` tendrán las siguientes condiciones:

- Exactamente cuatro caracteres.
- Letras `A`, `B`, `C`, `D` y números del `1` al `9`.
- No admitirán el número `0`, espacios ni caracteres especiales.
- Se almacenarán exclusivamente con BCrypt.
- No se mostrarán en consultas de usuarios.
- Se podrán actualizar individualmente, sin modificar las otras contraseñas.

La expresión regular será:

```regex
^[1-9A-D]{4}$
```

| Contraseña | Resultado |
|---|---|
| `AB12` | Válida |
| `A1B2` | Válida |
| `9999` | Válida |
| `DDDD` | Válida |
| `1234` | Válida |
| `A012` | Inválida |
| `ABCD5` | Inválida |

**Migración importante:** ya existe un usuario registrado (`id=1`, `nombre=postgres`). Al agregar las dos columnas, inicialmente deben admitir valores nulos para no impedir que la tabla existente funcione. Después se configurarán las nuevas contraseñas mediante el CRUD.

Mantener `spring.jpa.hibernate.ddl-auto=update`, que ya está configurado, sin editar el archivo YAML. Antes de iniciar la versión actualizada, realizar un respaldo de PostgreSQL.

## 4. Implementar el CRUD completo de usuarios

Crear estos componentes dentro de los paquetes existentes:

| Archivo | Función |
|---|---|
| `controller/UsuarioController.java` | Endpoints REST del CRUD |
| `service/UsuarioService.java` | Lógica de usuarios |
| `dto/CrearUsuarioRequest.java` | Datos para registrar usuarios |
| `dto/ActualizarUsuarioRequest.java` | Datos para actualizaciones |
| `dto/UsuarioResponse.java` | Respuesta segura sin contraseñas |

Modificar `UsuarioRepository.java` únicamente cuando se requieran consultas adicionales.

### Endpoints propuestos

| Método | Ruta | Función |
|---|---|---|
| POST | `/api/usuarios` | Crear usuario |
| GET | `/api/usuarios` | Listar usuarios |
| GET | `/api/usuarios/{id}` | Consultar usuario |
| PUT | `/api/usuarios/{id}` | Actualizar usuario |
| DELETE | `/api/usuarios/{id}` | Eliminar usuario |
| GET | `/api/usuarios/me` | Consultar la cuenta autenticada |

### Ejemplo: crear usuario

```json
{
  "nombre": "operador1",
  "contrasena": "ClaveLoginEjemplo2026",
  "passwordapp": "AB12",
  "passwordpuerta": "CD34"
}
```

El servicio deberá validar los datos, comprobar que `nombre` no exista, generar los tres hashes BCrypt y guardar el registro.

La respuesta no incluirá contraseñas ni hashes:

```json
{
  "id": 2,
  "nombre": "operador1",
  "passwordappConfigurada": true,
  "passwordpuertaConfigurada": true
}
```

### Ejemplo: actualizar solamente la contraseña de la puerta

```json
{
  "passwordpuerta": "B123"
}
```

No se debe modificar `nombre`, `contrasena` ni `passwordapp` cuando esos campos no estén presentes.

La consulta de usuarios debe devolver únicamente información pública o administrativa autorizada.

**Control de acceso del CRUD:** las operaciones sobre otros usuarios deben reservarse a una cuenta con permisos administrativos. Mientras no exista un mecanismo de roles, una sesión normal solo podrá consultar y actualizar su propia cuenta; no se deben habilitar altas o eliminaciones públicas.

---

## 5. Mantener el login e incorporar sesiones verificables

Archivos actuales:

- `AuthController.java`
- `AuthService.java`
- `LoginRequest.java`
- `LoginResponse.java`

El login seguirá comprobando `nombre` y `contrasena` con BCrypt.

Pero hay una modificación necesaria: **el login actual devuelve `autenticado=true`, sin proporcionar un mecanismo para comprobar posteriormente que la sesión continúa autorizada**.

Para que `passwordapp` pueda desactivar el buzzer solo después del login, el servidor debe implementar sesiones.

### Diseño recomendado

Utilizar tokens de sesión opacos, generados mediante `SecureRandom`, sin agregar dependencias externas.

El backend realizará lo siguiente:

1. Validar `nombre` y `contrasena`.
2. Generar un token aleatorio criptográficamente seguro.
3. Guardar en PostgreSQL únicamente el hash del token y su vencimiento.
4. Devolver el token al cliente autenticado.
5. Exigirlo en las operaciones protegidas.
6. Invalidarlo al cerrar sesión o cuando expire.

Crear las clases:

| Archivo | Función |
|---|---|
| `model/SesionUsuario.java` | Persistencia de sesiones |
| `repository/SesionUsuarioRepository.java` | Consultas de sesiones |
| `service/SesionService.java` | Crear, verificar, renovar y revocar sesiones |
| `config/AutorizacionConfig.java` | Protección centralizada de rutas |

Tiempo recomendado para las pruebas: **30 minutos de inactividad**, renovable mediante actividad autenticada.

### Respuesta de login

Conservar los campos existentes y agregar un token:

```json
{
  "autenticado": true,
  "mensaje": "Autenticacion correcta",
  "usuarioId": 1,
  "nombre": "postgres",
  "accessToken": "TOKEN_ALEATORIO_GENERADO",
  "expiresIn": 1800
}
```

Las peticiones protegidas deberán utilizar:

```http
Authorization: Bearer TOKEN_ALEATORIO_GENERADO
```

No aceptar `usuarioId` enviado por el cliente como prueba de identidad. El usuario se identificará a partir del token validado por el backend.

Agregar:

| Endpoint | Función |
|---|---|
| `POST /api/auth/logout` | Revocar sesión |
| `GET /api/auth/me` | Consultar usuario autenticado |

No es necesario agregar JWT ni modificar `pom.xml` para implementar esta solución.

---

## 6. Validación de la puerta desde el backend

Esta operación comienza cuando el usuario introduce cuatro caracteres y pulsa `#` en el teclado matricial físico.

### Flujo completo

```text
Teclado físico: PIN AB12 + #
       |
       v
ESP32 publica evento MQTT: tipo = pin_puerta
       |
       v
Backend valida passwordpuerta:
- formato del PIN
- comparación BCrypt / PostgreSQL
- bloqueo de alarma
- conectividad del ESP32 y Arduino
       |
       +--> PIN incorrecto o alarma activa: NO abrir
       |
       +--> PIN autorizado: publicar orden MQTT puerta/estado/1
                                       |
                                       v
                                Arduino abre servo
                                y reproduce 0002.mp3
```

### Reglas de validación

Cuando llegue:

```json
{
  "tipo": "pin_puerta",
  "codigo": "AB12"
}
```

El backend deberá:

1. Verificar que el evento proviene del tópico MQTT correcto.
2. Validar el formato del PIN con `^[1-9A-D]{4}$`.
3. Buscar una coincidencia BCrypt con `passwordpuerta`.
4. Verificar que la alarma general no está activa.
5. Verificar que el ESP32 y el Arduino están disponibles.
6. Si todo es correcto, enviar la orden de apertura.
7. Registrar el resultado del intento en PostgreSQL.

Como el JSON actual no incluye identificador de usuario, inicialmente el backend deberá comparar el PIN con los usuarios que tengan `passwordpuerta` configurada. Para evitar ambigüedades, no permitir que dos usuarios registren el mismo PIN de puerta.

La búsqueda debe realizarse con `PasswordEncoder.matches()` y nunca comparando directamente los hashes.

### Orden exacta para abrir la puerta

Tópico: `smart_home/esp32/command`

```json
{
  "request_id": "c092b459-6cd0-40b1-8c37-9d54274d4011",
  "id": "puerta",
  "accion": "estado",
  "valor": 1
}
```

Cuando el Arduino reciba la orden autorizada, el firmware actual:

- Gira el servomotor al ángulo de apertura.
- Reproduce automáticamente `/MP3/0002.mp3`, audio de bienvenida.
- Mantiene la puerta abierta durante 5 segundos.
- Vuelve a la posición de bloqueo.

**No enviar otra orden para reproducir `0002.mp3`.** El programa del Arduino ya la ejecuta al abrir la puerta. Enviarla otra vez provocaría reproducción duplicada.

### Si el PIN es incorrecto

No enviar ningún comando `puerta/estado/1`.

El servidor deberá registrar el intento rechazado y mantener la puerta bloqueada.

También deberá limitar los intentos repetidos. Para una maqueta, una política inicial razonable es bloquear nuevas validaciones durante 60 segundos después de cinco intentos fallidos consecutivos.

---

## 7. Máquina de estados de seguridad perimétrica

Crear:

`service/SeguridadPerimetralService.java`

El sensor estará configurado para considerar presencia cuando la distancia sea **menor o igual a 15 cm**.

### Estados y tiempos definitivos

```text
   0 s                  15 s                 25 s       30 s
    |--------------------|--------------------|----------|
    |    VIGILANCIA      | PRESENCIA          | ALERTA   |
    |                    | CONFIRMADA         |          |
    |                    |                    |          +--> BUZZER + BLOQUEO
    |                    |                    +--> MP3 0003
    +--> Primera lectura <= 15 cm
```

| Estado | Condición | Acción |
|---|---|---|
| `NORMAL` | No hay presencia | Sin acciones |
| `VIGILANCIA` | 0–15 segundos | Contabilizar permanencia |
| `PRESENCIA_CONFIRMADA` | 15 segundos | Registrar que continúa la presencia |
| `ADVERTENCIA` | 25 segundos | Reproducir `0003.mp3` |
| `ALARMA` | 30 segundos | Activar buzzer y bloquear puerta |
| `REARMADO` | Contraseña válida | Apagar buzzer y reiniciar vigilancia |

### Temporizadores

Se deben utilizar tareas programadas de Spring mediante `@Scheduled`, sin utilizar `Thread.sleep()`.

Frecuencia sugerida: cada 200 ms.

El temporizador debe iniciarse con la primera medición válida que indique `cm <= 15`.

```text
00 s  Distancia: 12 cm -> VIGILANCIA
05 s  Distancia: 11 cm -> VIGILANCIA
15 s  Distancia: 12 cm -> PRESENCIA_CONFIRMADA
25 s  Distancia: 10 cm -> ADVERTENCIA
30 s  Distancia: 11 cm -> ALARMA
```

### Reglas adicionales

- Si el objeto abandona el área antes de los 30 segundos, el estado vuelve a `NORMAL` y el contador se reinicia.
- Una medición inválida no debe interpretarse automáticamente como presencia o ausencia.
- Si el servidor deja de recibir mediciones válidas durante un intervalo definido —por ejemplo, 2 segundos— debe registrar `SENSOR_SIN_DATOS` y suspender la progresión del temporizador hasta recuperar mediciones suficientes.
- Cuando la alarma ya se ha activado, **debe mantenerse activa aunque el objeto se retire**. Su desactivación depende de `passwordapp`.
- El backend debe guardar cuándo entró en cada estado y evitar enviar varias veces la misma orden correspondiente a una transición.

## 8. Audio de advertencia a los 25 segundos

A los 25 segundos de presencia continua, el backend enviará:

```json
{
  "request_id": "d5b2cd7c-441f-4d99-b598-a12504d2fe32",
  "id": "audio",
  "accion": "pista",
  "valor": 3
}
```

El ESP32 traducirá esta orden para que el DFPlayer reproduzca:

**`/MP3/0003.mp3`**

Mensaje: *«Advertencia: Se encuentra en propiedad privada. Por favor retírese»*.

Esta orden debe enviarse una sola vez por ciclo de detección.

El backend debe esperar la confirmación `ack` y registrar si se procesó o si el resultado fue incierto.

## 9. Activación de alarma a los 30 segundos

Si la presencia continúa, el backend enviará:

```json
{
  "request_id": "f6a92c5d-1de4-4f72-81be-29a3b61840c0",
  "id": "alarma",
  "accion": "estado",
  "valor": 1
}
```

El firmware actual del Arduino ejecuta simultáneamente:

- Activación continua del buzzer.
- Bloqueo de la puerta mediante el servomotor.
- Registro interno de alarma activa.

**No es necesario enviar una segunda orden de bloqueo** después de activar la alarma: el comando `alarma/estado/1` ya realiza ambas operaciones.

El backend deberá registrar esta transición y marcar el estado como `ALARMA`.

Mientras esté activa:

- No aceptar nuevas aperturas de puerta.
- No permitir que un comando genérico la desactive sin autenticación.
- No detener automáticamente el buzzer cuando desaparezca la presencia.
- Mantener el bloqueo hasta el rearme autorizado.

---

## 10. Desactivación de la alarma mediante `passwordapp`

Esta operación será exclusiva del teclado virtual de la aplicación.

**El teclado matricial físico no tendrá ninguna función para apagar el buzzer.**

Crear:

`controller/SeguridadController.java`

Endpoint:

```http
POST /api/seguridad/alarma/desactivar
```

### Solicitud

Debe incluir la sesión autenticada:

```http
Authorization: Bearer TOKEN_DE_SESION
Content-Type: application/json
```

Cuerpo JSON:

```json
{
  "passwordapp": "AB12"
}
```

### Validación

El backend realizará estas verificaciones, en orden:

1. El token de sesión existe, está vigente y no está revocado.
2. Identifica un usuario existente y activo.
3. `passwordapp` tiene exactamente cuatro caracteres válidos.
4. La contraseña coincide con el hash BCrypt del usuario autenticado.
5. El sistema se encuentra en un estado que permite el rearme.
6. La comunicación MQTT está disponible.

Si se cumplen, enviará:

```json
{
  "request_id": "f48cba38-0b08-4514-85b4-41fd7520cbca",
  "id": "alarma",
  "accion": "estado",
  "valor": 0
}
```

El Arduino desactivará el buzzer y volverá a habilitar la posibilidad de apertura.

### Respuesta correcta

Después de recibir el ACK de aplicación:

```json
{
  "autorizado": true,
  "resultado": "aplicado",
  "alarma": "desactivada",
  "mensaje": "Alarma desactivada correctamente"
}
```

- Si falta la sesión, devolver HTTP `401 Unauthorized`.
- Si la contraseña es incorrecta, devolver un rechazo sin publicar ningún comando MQTT.
- Si el dispositivo no confirma la ejecución, responder con estado incierto y conservar la alarma como activa hasta reconciliarla con la telemetría.
- Tras el rearme, si el objeto continúa dentro del perímetro, comenzará un nuevo ciclo de vigilancia.
- Nunca se abrirá automáticamente la puerta por el simple hecho de desactivar la alarma.

---

## 11. Proteger el endpoint actual de comandos

Actualmente existe:

`POST /api/smart-home/commands`

Este endpoint permite enviar comandos de `puerta`, `alarma`, `audio`, `ventilador` y luces.

**Problema crítico:** aunque implementes contraseñas para la puerta y el buzzer, la seguridad sería inútil si otra ruta puede enviar directamente:

```json
{
  "id": "puerta",
  "accion": "estado",
  "valor": 1
}
```

o:

```json
{
  "id": "alarma",
  "accion": "estado",
  "valor": 0
}
```

sin pasar por la autorización correspondiente.

### Regla obligatoria

En `SmartHomeService` centralizar las restricciones:

| Operación | Autorización requerida |
|---|---|
| Abrir puerta | Validación correcta de `passwordpuerta` |
| Modificar ángulo para desbloquear | Misma autorización de puerta |
| Activar alarma automáticamente | Máquina de estados perimetral |
| Desactivar alarma | Sesión vigente + `passwordapp` |
| Cerrar puerta | Autorización de seguridad correspondiente |
| Luces y otros dispositivos | Conservar su lógica, aplicando controles de acceso apropiados |

No basta con proteger únicamente los nuevos endpoints. La validación debe aplicarse en la capa de servicio para evitar rutas alternativas de ejecución.

El servicio puede utilizar un objeto interno `ComandoAutorizado`, que incluya la acción, la identidad o proceso que autorizó la operación y el identificador de solicitud.

---

## 12. Integración de eventos MQTT con el backend existente

Archivos principales que debes revisar:

- `mqtt/PahoMqttTransport.java`
- `mqtt/MqttTransport.java`
- `service/SmartHomeService.java`

El transporte actual ya está suscrito a los tópicos necesarios.

**No hace falta crear otra conexión MQTT para recibir PIN y distancia.**

### Cómo organizar el procesamiento

`SmartHomeService.receive()` actualmente procesa estados, confirmaciones y eventos de teclas.

Debe ampliarse para reconocer:

```text
tipo = "pin_puerta"
tipo = "distancia"
tipo = "estado"
tipo = "ack"
```

Además, debe continuar tratando los mensajes `availability`.

La recomendación es separar el transporte del procesamiento:

```text
PahoMqttTransport
  |
  v
SmartHomeService: clasifica y valida el JSON
  |
  v
Cola interna de eventos (no bloquear callback MQTT)
  |
  +--> AccesoService: PIN de puerta
  |
  +--> SeguridadPerimetralService: distancia y tiempo
```

No registrar un segundo `setReceiver()` sobre el mismo `MqttTransport` sin añadir distribución de eventos: el transporte actual admite un solo receptor, y sustituirlo desconectaría lógicamente al receptor existente.

Las consultas a PostgreSQL, comparaciones BCrypt y esperas de ACK deben ejecutarse fuera del callback MQTT.

## 13. Administrador de órdenes y confirmaciones

`SmartHomeService` ya utiliza `Pending`, `request_id` y `CompletableFuture`.

Conviene conservar esa arquitectura.

### Reglas que debe cumplir

- Generar un UUID por orden.
- Publicar mediante `MqttTransport.publish()`.
- Esperar el ACK correlacionado.
- Mantener el timeout actual de 5 segundos.
- No repetir automáticamente aperturas cuyo resultado sea `incierto`.
- Consultar posteriormente `status` para conocer el estado reportado.

**Limitación actual:** el servicio permite solamente una orden pendiente al mismo tiempo.

Para la nueva seguridad, crear un despachador de órdenes que serialice las acciones. La activación de alarma debe tener prioridad frente a operaciones no críticas.

Ejemplo: a los 25 segundos se envía el audio. Si cuando se cumplen 30 segundos aún existe una orden pendiente, la activación de alarma deberá quedar priorizada y ejecutarse cuando el transporte lo permita, registrando cualquier retraso.

No publicar acciones físicas con `retained=true`, porque se podrían ejecutar de nuevo al reconectar el dispositivo.

---

## 14. Registrar los eventos en PostgreSQL

Actualmente solo se ha confirmado la tabla `usuarios`.

Agregar estas entidades dentro del backend:

| Tabla | Utilidad |
|---|---|
| `usuarios` | Credenciales de los usuarios |
| `sesiones_usuario` | Sesiones autenticadas |
| `eventos_seguridad` | Historial de accesos, advertencias y alarmas |
| `estado_seguridad` | Estado actual y recuperación tras reinicios |

### Estructura recomendada de `eventos_seguridad`

| Columna | Tipo sugerido |
|---|---|
| `id` | BIGINT |
| `fecha` | TIMESTAMP WITH TIME ZONE |
| `tipo` | VARCHAR(50) |
| `resultado` | VARCHAR(30) |
| `usuario_id` | BIGINT, nullable |
| `distancia_cm` | INTEGER, nullable |
| `request_id` | VARCHAR(36), nullable |
| `detalle` | VARCHAR(255) |

Valores sugeridos para `tipo`:

`ACCESO_AUTORIZADO`, `ACCESO_RECHAZADO`, `VIGILANCIA`, `ADVERTENCIA`, `ALARMA_ACTIVADA`, `ALARMA_DESACTIVADA`, `ERROR_SENSOR`, `ERROR_MQTT`.

Ejemplo de registro:

| Fecha | Tipo | Resultado | Detalle |
|---|---|---|---|
| 00:10:00 | VIGILANCIA | INICIADA | Distancia 12 cm |
| 00:10:25 | ADVERTENCIA | APLICADA | Audio 0003 |
| 00:10:30 | ALARMA_ACTIVADA | APLICADA | Buzzer y cerradura |
| 00:11:05 | ALARMA_DESACTIVADA | APLICADA | Usuario autenticado |

No almacenar en este historial los valores introducidos en `passwordapp` o `passwordpuerta`.

La tabla `estado_seguridad` permitirá recuperar una alarma activa cuando Spring Boot se reinicie. Al arrancar, el servidor deberá contrastar el estado persistido con la telemetría reciente, sin asumir que el buzzer está apagado.

---

## 15. API para consultar el estado de seguridad

Agregar:

```http
GET /api/seguridad/estado
```

Respuesta propuesta:

```json
{
  "estado": "ADVERTENCIA",
  "distanciaCm": 12,
  "presencia": true,
  "tiempoPresenciaSegundos": 27,
  "umbralCm": 15,
  "advertenciaSegundo": 25,
  "alarmaSegundo": 30,
  "puertaBloqueada": false,
  "buzzerActivo": false,
  "esp32Online": true,
  "arduinoOnline": true,
  "ultimaMedicionValida": "2026-11-06T06:10:27Z"
}
```

Los valores serán calculados o leídos por el servidor, no constantes de ejemplo.

| Endpoint | Finalidad |
|---|---|
| `GET /api/seguridad/estado` | Consultar seguridad actual |
| `GET /api/seguridad/eventos` | Historial paginado |
| `POST /api/seguridad/alarma/desactivar` | Validar sesión y `passwordapp` |

No necesitamos un endpoint adicional que permita abrir la puerta sin el teclado físico.

---

## 16. Compatibilidad y problemas que deben corregirse

### A. Dos clientes MQTT con el mismo identificador

`PahoMqttTransport` y `MqttLedService` utilizan por defecto `smart-home-backend` como identificador.

Si ambos se conectan al mismo broker con ese ID, pueden desconectarse entre sí.

**Instrucción:** unificar el transporte o asignar un identificador único a cada cliente, sin modificar el YAML.

### B. Mensajes de luces del servicio antiguo

`MqttLedService` envía comandos como `LED_ON`, mientras que el nuevo ESP32 espera JSON para `smart_home/esp32/command`.

El backend no debe asumir que ambos formatos son compatibles.

Conservar los endpoints existentes y revisar la adaptación de los comandos. Si una función no está soportada por el firmware actual, debe reportar que no se pudo ejecutar, en lugar de indicar falsamente que está aplicada.

### C. Audio y disponibilidad

El backend actual utiliza `uno2` para verificar disponibilidad del audio.

Mantener la compatibilidad porque el nuevo ESP32 reporta `uno2` como alias lógico.

La pista reportada por el ESP32 representa la última orden de reproducción registrada, no una verificación física del sonido.

### D. Seguridad del broker

La documentación del servidor indica que Mosquitto admite conexiones anónimas en la configuración de pruebas.

Eso significa que **la autorización del backend puede ser eludida si otro dispositivo de la red tiene permiso para publicar directamente en `smart_home/esp32/command`**.

Las validaciones propuestas son adecuadas para desarrollar la demostración, pero no deben presentarse como una cerradura segura para uso real mientras el broker carezca de autenticación y controles de publicación.

### E. Limitaciones del firmware actual

Hay dos funciones que no se pueden garantizar modificando solamente Java:

- Un pitido corto de error para PIN incorrecto: el protocolo actual no tiene una orden específica para ese sonido.
- Confirmación física del cierre del servo: el estado reportado refleja la orden y la posición lógica del programa, no un sensor mecánico independiente.

El backend deberá gestionar esas limitaciones sin inventar confirmaciones.

---

## 17. Plan de pruebas obligatorio

Antes de dar por terminada la modificación, ejecutar las siguientes pruebas.

| N.º | Prueba | Resultado esperado |
|---|---|---|
| 1 | Iniciar el backend con usuario existente | Inicia sin errores de migración |
| 2 | Crear usuario | Guarda tres hashes BCrypt |
| 3 | Consultar usuarios | No expone contraseñas |
| 4 | Actualizar `passwordpuerta` | No modifica las otras contraseñas |
| 5 | Login correcto | Devuelve sesión válida |
| 6 | Login incorrecto | HTTP 401 |
| 7 | Enviar `pin_puerta` válido por MQTT | Orden de apertura |
| 8 | Enviar `pin_puerta` incorrecto | No abre puerta |
| 9 | Activar alarma e intentar abrir | Apertura rechazada |
| 10 | Distancia de 20 cm | Estado normal |
| 11 | Distancia de 15 cm | Comienza vigilancia |
| 12 | Presencia durante 15 segundos | Presencia confirmada |
| 13 | Presencia durante 25 segundos | Orden de audio 0003 |
| 14 | Presencia durante 30 segundos | Buzzer activo y puerta bloqueada |
| 15 | Objeto se retira a los 20 segundos | Temporizador reiniciado |
| 16 | Lectura ultrasónica inválida | No activa falsa alarma |
| 17 | Desactivar alarma con `passwordapp` incorrecta | Rechazo |
| 18 | Desactivar sin sesión | HTTP 401 |
| 19 | Desactivar con sesión y contraseña válida | Orden `alarma/estado/0` |
| 20 | Reiniciar backend con alarma activa | Recupera estado sin desactivar automáticamente |
| 21 | Intentar apertura directa por API genérica | Rechazado sin autorización |
| 22 | Perder comunicación MQTT | Estado de ejecución incierto, sin éxito falso |

También deben probarse intentos repetidos de PIN y la concurrencia entre una orden de puerta y una alarma automática.

## 18. Comandos de comprobación desde Ubuntu

Desde la sesión SSH:

```bash
cd ~/proyectos/smart_home_back
```

Compilar y ejecutar las pruebas con el Maven Wrapper existente:

```bash
./mvnw clean test
```

Para observar todos los mensajes MQTT del sistema:

```bash
mosquitto_sub -h 127.0.0.1 -p 1883 \
  -t 'smart_home/esp32/#' -v
```

Consultar las tablas después de aplicar la nueva versión:

```sql
\dt
\d usuarios
\d eventos_seguridad
```

No ejecutar pruebas de publicación de órdenes de apertura o alarma sobre una instalación activa sin controlar físicamente la maqueta.

**Nota sobre la compilación:** en la exportación aparece `MqttService.java` importando Spring Integration MQTT, pero esa dependencia no figura en el `pom.xml` compartido. Si la compilación falla por ese motivo, hay que resolver esa incompatibilidad antes de considerar exitoso el despliegue. No se debe atribuir el fallo a las nuevas clases sin verificarlo.

---

## 19. Orden exacto de implementación

1. **Modelo y persistencia.** Ampliar `Usuario`, crear DTO de entrada/salida y probar la migración conservando el usuario existente.
2. **CRUD de usuarios.** Crear `UsuarioService` y `UsuarioController`, incluyendo validaciones y hash BCrypt.
3. **Autenticación y sesiones.** Conservar el login y añadir tokens de sesión, vencimiento y logout.
4. **Autorización.** Proteger las acciones críticas de puerta, alarma y administración de usuarios.
5. **Integración MQTT.** Adaptar `SmartHomeService.receive()` para procesar `pin_puerta` y `distancia`, preservando `status`, `ack` y `availability`.
6. **Control de acceso.** Implementar `AccesoService`, validación de `passwordpuerta` y publicación de la orden de apertura.
7. **Seguridad perimétrica.** Crear máquina de estados y temporizadores de 15, 25 y 30 segundos.
8. **Audio y alarma.** Emitir las órdenes JSON de pista 3 y alarma activa, esperando confirmación.
9. **Desactivación autorizada.** Crear el endpoint que valida la sesión y `passwordapp` antes de apagar el buzzer.
10. **Persistencia y recuperación.** Guardar eventos, estados de seguridad y gestionar reinicios o desconexiones.
11. **Pruebas integrales.** Ejecutar pruebas unitarias, de API y de comunicación MQTT utilizando el protocolo real.

## 20. Resultado esperado del sistema completo

Al finalizar estos cambios:

- El usuario `postgres` seguirá funcionando con su contraseña habitual de login.
- Cada usuario podrá tener `passwordpuerta` y `passwordapp` independientes.
- El teclado matricial físico enviará los cuatro caracteres al servidor para autorizar la apertura.
- La puerta se abrirá durante 5 segundos y el Arduino reproducirá `0002.mp3`.
- El ultrasónico informará al servidor sobre la distancia.
- A los 15 segundos de presencia continua quedará confirmada la vigilancia.
- A los 25 segundos se reproducirá `0003.mp3`.
- A los 30 segundos se activará el buzzer y la puerta quedará bloqueada.
- La alarma solamente podrá desactivarse mediante una sesión autenticada y `passwordapp` correcta.
- Los intentos de acceso y eventos de seguridad quedarán registrados en PostgreSQL.
- Los endpoints críticos no permitirán eludir estas validaciones.
- El backend mantendrá el formato JSON, los nombres de los tópicos y el mecanismo de confirmación del ESP32 actual.

**Criterio final de aceptación:** una operación se considerará ejecutada solamente cuando haya sido autorizada por el backend y confirmada por el ESP32 mediante el ACK correspondiente. Las órdenes sin confirmación deberán conservarse como inciertas.

---

**Alcance del documento:** estas son instrucciones de implementación. No se ha modificado todavía el código Java ni la base de datos del servidor.
