package com.semillero.evaluacion.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.semillero.evaluacion.seguridad.JwtService;
import com.semillero.evaluacion.soporte.ContextoPruebas;
import com.semillero.evaluacion.usuarios.Rol;
import com.semillero.evaluacion.usuarios.Usuario;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de endpoints de autenticacion y de proteccion por rol, SIN Oracle
 * (perfil test: los repositorios JPA se sustituyen con mocks).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTests extends ContextoPruebas {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private Usuario usuario(String nombre, Rol rol, boolean activo, boolean pendiente) {
        Usuario u = new Usuario();
        u.setUsuario(nombre);
        u.setContrasenaHash(encoder.encode("ClaveActual123"));
        u.setRol(rol);
        u.setActivo(activo);
        u.setCambioContrasenaPendiente(pendiente);
        ReflectionTestUtils.setField(u, "id", 1L);
        return u;
    }

    @Test
    @DisplayName("POST /auth/login con credenciales correctas: 200, token y bandera de cambio")
    void loginCorrecto() throws Exception {
        when(usuarios.findByUsuario("profesor1"))
                .thenReturn(Optional.of(usuario("profesor1", Rol.PROFESOR, true, true)));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"profesor1\",\"contrasena\":\"ClaveActual123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.usuario").value("profesor1"))
                .andExpect(jsonPath("$.rol").value("PROFESOR"))
                .andExpect(jsonPath("$.cambioContrasenaPendiente").value(true));
    }

    @Test
    @DisplayName("POST /auth/login con contrasena incorrecta: 401 ApiError")
    void loginIncorrecto() throws Exception {
        when(usuarios.findByUsuario("profesor1"))
                .thenReturn(Optional.of(usuario("profesor1", Rol.PROFESOR, true, false)));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"profesor1\",\"contrasena\":\"Incorrecta999\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.estado").value(401))
                .andExpect(jsonPath("$.ruta").value("/api/v1/auth/login"));
    }

    @Test
    @DisplayName("GET /auth/me sin token: 401 ApiError del punto de entrada de seguridad")
    void meSinToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.estado").value(401))
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    @DisplayName("GET /auth/me con token valido: 200 con los datos de la cuenta")
    void meConToken() throws Exception {
        Usuario cuenta = usuario("profesor1", Rol.PROFESOR, true, false);
        when(usuarios.findByUsuario("profesor1")).thenReturn(Optional.of(cuenta));
        String token = jwtService.generarToken(cuenta);

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.usuario").value("profesor1"))
                .andExpect(jsonPath("$.rol").value("PROFESOR"))
                .andExpect(jsonPath("$.cambioContrasenaPendiente").value(false));
    }

    @Test
    @DisplayName("Usuario con contrasena temporal: bloqueado en /me con 403")
    void cambioObligatorioBloqueaOtrasRutas() throws Exception {
        Usuario cuenta = usuario("profesor1", Rol.PROFESOR, true, true);
        when(usuarios.findByUsuario("profesor1")).thenReturn(Optional.of(cuenta));
        String token = jwtService.generarToken(cuenta);

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.estado").value(403))
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    @DisplayName("Cambio de contrasena con token pendiente: 200 y bandera apagada")
    void cambioDeContrasenaFlujoCompleto() throws Exception {
        Usuario cuenta = usuario("profesor1", Rol.PROFESOR, true, true);
        when(usuarios.findByUsuario("profesor1")).thenReturn(Optional.of(cuenta));
        when(usuarios.findById(1L)).thenReturn(Optional.of(cuenta));
        String token = jwtService.generarToken(cuenta);

        mockMvc.perform(post("/api/v1/auth/cambiar-contrasena")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contrasenaActual\":\"ClaveActual123\","
                                + "\"contrasenaNueva\":\"NuevaClave456\","
                                + "\"confirmacion\":\"NuevaClave456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.cambioContrasenaPendiente").value(false));
    }

    @Test
    @DisplayName("Actuator sin token: 401; con token de ESTUDIANTE: 403; con ADMIN: 200")
    void actuatorProtegidoPorRol() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isUnauthorized());

        Usuario estudiante = usuario("est1", Rol.ESTUDIANTE, true, false);
        when(usuarios.findByUsuario("est1")).thenReturn(Optional.of(estudiante));
        mockMvc.perform(get("/actuator/health")
                        .header("Authorization", "Bearer " + jwtService.generarToken(estudiante)))
                .andExpect(status().isForbidden());

        Usuario admin = usuario("admin", Rol.ADMINISTRADOR, true, false);
        when(usuarios.findByUsuario("admin")).thenReturn(Optional.of(admin));
        mockMvc.perform(get("/actuator/health")
                        .header("Authorization", "Bearer " + jwtService.generarToken(admin)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Correccion A-1: ruta desconocida SIN autenticacion -> 401 ApiError (antes era abierta)")
    void rutaDesconocidaSinAutenticacion() throws Exception {
        mockMvc.perform(get("/ruta-inexistente"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.estado").value(401))
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.mensaje").exists())
                .andExpect(jsonPath("$.ruta").value("/ruta-inexistente"));
    }

    @Test
    @DisplayName("Swagger / OpenAPI siguen publicos tras cerrar el catch-all")
    void swaggerSiguePublico() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk());
    }
}
