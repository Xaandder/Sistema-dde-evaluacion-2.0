package com.semillero.evaluacion.matriculas;

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
 * Pruebas de endpoints de matriculas, SIN Oracle (perfil test).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MatriculaControllerTests extends ContextoPruebas {

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

    private Matricula matricula(Long id, EstadoMatricula estado) {
        Matricula m = new Matricula();
        ReflectionTestUtils.setField(m, "id", id);
        m.setEstudianteId(10L);
        m.setGrupoId(6L);
        m.setAnioId(5L);
        m.setEstado(estado);
        return m;
    }

    @Test
    @DisplayName("GET /matriculas?estudianteId=10 filtra las matriculas del estudiante")
    void listarPorEstudiante() throws Exception {
        when(anios.findById(5L)).thenReturn(Optional.empty());
        when(matriculas.findByEstudianteIdOrderByFechaMatriculaAsc(10L)).thenReturn(List.of(
                matricula(100L, EstadoMatricula.ACTIVA)));

        mockMvc.perform(get("/api/v1/matriculas")
                        .queryParam("estudianteId", "10")
                        .header("Authorization", "Bearer " + token(Rol.PROFESOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].estudianteId").value(10))
                .andExpect(jsonPath("$[0].estado").value("ACTIVA"));
    }

    @Test
    @DisplayName("GET /matriculas/{id} inexistente: 404")
    void consultaInexistente() throws Exception {
        when(matriculas.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/matriculas/99")
                        .header("Authorization", "Bearer " + token(Rol.ESTUDIANTE)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /matriculas como ADMINISTRADOR: la regla del servicio decide (201 o 400)")
    void crearComoAdministrador() throws Exception {
        when(estudiantes.findById(10L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/matriculas")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estudianteId\":10,\"grupoId\":6}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /matriculas como ESTUDIANTE: 403")
    void crearComoEstudianteProhibido() throws Exception {
        mockMvc.perform(post("/api/v1/matriculas")
                        .header("Authorization", "Bearer " + token(Rol.ESTUDIANTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estudianteId\":10,\"grupoId\":6}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /matriculas/{id}/retirar como ADMINISTRADOR: delega en el servicio")
    void retirarComoAdministrador() throws Exception {
        when(matriculas.findById(100L)).thenReturn(Optional.of(
                matricula(100L, EstadoMatricula.RETIRADA)));

        mockMvc.perform(post("/api/v1/matriculas/100/retirar")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400));
    }
}
