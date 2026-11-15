package com.semillero.evaluacion.anios;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Pruebas de endpoints de anios lectivos, SIN Oracle (perfil test: los
 * repositorios JPA se sustituyen con mocks). Verifica rutas, permisos por
 * rol y las respuestas de error uniformes (ApiError).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnioControllerTests extends ContextoPruebas {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private Usuario usuario(String nombre, Rol rol, boolean pendiente) {
        Usuario u = new Usuario();
        u.setUsuario(nombre);
        u.setContrasenaHash(encoder.encode("ClaveActual123"));
        u.setRol(rol);
        u.setActivo(true);
        u.setCambioContrasenaPendiente(pendiente);
        ReflectionTestUtils.setField(u, "id", 1L);
        return u;
    }

    private AnioAcademico anio(Long id, Integer numero, EstadoAnio estado) {
        AnioAcademico a = new AnioAcademico();
        ReflectionTestUtils.setField(a, "id", id);
        a.setAnio(numero);
        a.setEstado(estado);
        return a;
    }

    private String token(Rol rol) {
        String nombre = rol.name().toLowerCase();
        Usuario cuenta = usuario(nombre, rol, false);
        when(usuarios.findByUsuario(nombre)).thenReturn(Optional.of(cuenta));
        return jwtService.generarToken(cuenta);
    }

    @Test
    @DisplayName("POST /anios como ADMINISTRADOR: 201 con el anio en estado ACTIVO")
    void crearComoAdministrador() throws Exception {
        when(anios.existsByAnio(2026)).thenReturn(false);
        when(anios.save(any())).thenAnswer(inv -> {
            AnioAcademico a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", 7L);
            return a;
        });

        mockMvc.perform(post("/api/v1/anios")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anio\":2026}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.anio").value(2026))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
    }

    @Test
    @DisplayName("POST /anios como PROFESOR: 403 (solo ADMINISTRADOR)")
    void crearComoProfesorProhibido() throws Exception {
        mockMvc.perform(post("/api/v1/anios")
                        .header("Authorization", "Bearer " + token(Rol.PROFESOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anio\":2026}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /anios duplicado: 400 ApiError con mensaje de regla de negocio")
    void crearDuplicado() throws Exception {
        when(anios.existsByAnio(2026)).thenReturn(true);

        mockMvc.perform(post("/api/v1/anios")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anio\":2026}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400))
                .andExpect(jsonPath("$.mensaje").value("El anio 2026 ya esta registrado"));
    }

    @Test
    @DisplayName("POST /anios con anio fuera de rango (2101): 400 por validacion del DTO")
    void crearFueraDeRango() throws Exception {
        mockMvc.perform(post("/api/v1/anios")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anio\":2101}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /anios y GET /anios/{id} accesibles para ESTUDIANTE autenticado")
    void consultaAccesibleParaCualquierRol() throws Exception {
        when(anios.findAllByOrderByAnioAsc()).thenReturn(java.util.List.of(
                anio(1L, 2025, EstadoAnio.CERRADO), anio(2L, 2026, EstadoAnio.ACTIVO)));
        when(anios.findById(2L)).thenReturn(Optional.of(anio(2L, 2026, EstadoAnio.ACTIVO)));
        String t = token(Rol.ESTUDIANTE);

        mockMvc.perform(get("/api/v1/anios").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].anio").value(2025))
                .andExpect(jsonPath("$[1].estado").value("ACTIVO"));

        mockMvc.perform(get("/api/v1/anios/2").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anio").value(2026));
    }

    @Test
    @DisplayName("GET /anios sin token: 401")
    void consultaSinToken() throws Exception {
        mockMvc.perform(get("/api/v1/anios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /anios/{id} inexistente: 404 ApiError")
    void consultaInexistente() throws Exception {
        when(anios.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/anios/99")
                        .header("Authorization", "Bearer " + token(Rol.PROFESOR)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.estado").value(404))
                .andExpect(jsonPath("$.mensaje").value("No existe un anio lectivo con id 99"));
    }

    @Test
    @DisplayName("PUT /anios/{id} como ADMINISTRADOR: 200 con el numero actualizado")
    void actualizarComoAdministrador() throws Exception {
        when(anios.findById(5L)).thenReturn(Optional.of(anio(5L, 2026, EstadoAnio.ACTIVO)));
        when(anios.existsByAnio(2027)).thenReturn(false);
        when(anios.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/v1/anios/5")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anio\":2027}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anio").value(2027));
    }

    @Test
    @DisplayName("POST /anios/{id}/cerrar como ADMINISTRADOR: 200 CERRADO; ya cerrado: 400")
    void cerrarAnio() throws Exception {
        when(anios.findById(5L)).thenReturn(Optional.of(anio(5L, 2026, EstadoAnio.ACTIVO)));
        when(anios.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/anios/5/cerrar")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CERRADO"));

        when(anios.findById(6L)).thenReturn(Optional.of(anio(6L, 2025, EstadoAnio.CERRADO)));
        mockMvc.perform(post("/api/v1/anios/6/cerrar")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /anios/{id}/activar como ADMINISTRADOR: 200 ACTIVO; ya activo: 400")
    void activarAnio() throws Exception {
        when(anios.findById(5L)).thenReturn(Optional.of(anio(5L, 2025, EstadoAnio.CERRADO)));
        when(anios.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/anios/5/activar")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVO"));

        when(anios.findById(6L)).thenReturn(Optional.of(anio(6L, 2026, EstadoAnio.ACTIVO)));
        mockMvc.perform(post("/api/v1/anios/6/activar")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR)))
                .andExpect(status().isBadRequest());
    }
}
