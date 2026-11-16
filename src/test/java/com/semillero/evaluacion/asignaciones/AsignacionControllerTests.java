package com.semillero.evaluacion.asignaciones;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.semillero.evaluacion.seguridad.JwtService;
import com.semillero.evaluacion.soporte.ContextoPruebas;
import com.semillero.evaluacion.usuarios.Rol;
import com.semillero.evaluacion.usuarios.Usuario;
import java.util.List;
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

import static org.mockito.Mockito.when;

/**
 * Pruebas de endpoints de asignaciones docentes, SIN Oracle (perfil test).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AsignacionControllerTests extends ContextoPruebas {

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

    private String token(Rol rol) {
        String nombre = rol.name().toLowerCase();
        Usuario cuenta = usuario(nombre, rol, false);
        when(usuarios.findByUsuario(nombre)).thenReturn(Optional.of(cuenta));
        return jwtService.generarToken(cuenta);
    }

    private Asignacion asignacion(Long id, Long profesorId, EstadoAsignacion estado) {
        Asignacion a = new Asignacion();
        ReflectionTestUtils.setField(a, "id", id);
        a.setProfesorId(profesorId);
        a.setMateriaId(2L);
        a.setGrupoId(6L);
        a.setPeriodoId(10L);
        a.setAnioId(5L);
        a.setEstado(estado);
        return a;
    }

    @Test
    @DisplayName("GET /asignaciones?profesorId=3 filtra por docente (cualquier rol)")
    void listarPorProfesor() throws Exception {
        when(asignaciones.findByProfesorIdOrderByCreadoEnAsc(3L)).thenReturn(List.of(
                asignacion(500L, 3L, EstadoAsignacion.ACTIVA)));
        com.semillero.evaluacion.anios.AnioAcademico anio =
                new com.semillero.evaluacion.anios.AnioAcademico();
        ReflectionTestUtils.setField(anio, "id", 5L);
        anio.setAnio(2026);
        anio.setEstado(com.semillero.evaluacion.anios.EstadoAnio.ACTIVO);
        when(anios.findAllById(List.of(5L))).thenReturn(List.of(anio));

        mockMvc.perform(get("/api/v1/asignaciones")
                        .queryParam("profesorId", "3")
                        .header("Authorization", "Bearer " + token(Rol.PROFESOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].profesorId").value(3))
                .andExpect(jsonPath("$[0].anio").value(2026))
                .andExpect(jsonPath("$[0].estado").value("ACTIVA"));
    }

    @Test
    @DisplayName("GET /asignaciones/{id} inexistente: 404")
    void consultaInexistente() throws Exception {
        when(asignaciones.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/asignaciones/99")
                        .header("Authorization", "Bearer " + token(Rol.ESTUDIANTE)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /asignaciones como ACUDIENTE: 403 (solo ADMINISTRADOR)")
    void crearComoAcudienteProhibido() throws Exception {
        mockMvc.perform(post("/api/v1/asignaciones")
                        .header("Authorization", "Bearer " + token(Rol.ACUDIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profesorId\":3,\"materiaId\":2,\"grupoId\":6,\"periodoId\":10}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /asignaciones con DTO invalido (sin periodo): 400 por validacion")
    void crearSinPeriodo() throws Exception {
        mockMvc.perform(post("/api/v1/asignaciones")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profesorId\":3,\"materiaId\":2,\"grupoId\":6}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /asignaciones como ADMINISTRADOR: el servicio decide (404 si no hay profesor)")
    void crearConProfesorInexistente() throws Exception {
        when(profesores.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/asignaciones")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profesorId\":99,\"materiaId\":2,\"grupoId\":6,\"periodoId\":10}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /asignaciones/{id}/finalizar: delega en el servicio (no ACTIVA -> 400)")
    void finalizarComoAdministrador() throws Exception {
        when(asignaciones.findById(500L)).thenReturn(Optional.of(
                asignacion(500L, 3L, EstadoAsignacion.INACTIVA)));

        mockMvc.perform(post("/api/v1/asignaciones/500/finalizar")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400));
    }

    @Test
    @DisplayName("POST /asignaciones/{id}/reemplazar: delega en el servicio")
    void reemplazarComoAdministrador() throws Exception {
        when(asignaciones.findById(500L)).thenReturn(Optional.of(
                asignacion(500L, 3L, EstadoAsignacion.INACTIVA)));

        mockMvc.perform(post("/api/v1/asignaciones/500/reemplazar")
                        .queryParam("profesorId", "4")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR)))
                .andExpect(status().isBadRequest());
    }
}
