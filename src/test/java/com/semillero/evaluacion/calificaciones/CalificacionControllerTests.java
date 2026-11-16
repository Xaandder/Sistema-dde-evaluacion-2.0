package com.semillero.evaluacion.calificaciones;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.semillero.evaluacion.asignaciones.Asignacion;
import com.semillero.evaluacion.asignaciones.EstadoAsignacion;
import com.semillero.evaluacion.matriculas.EstadoMatricula;
import com.semillero.evaluacion.matriculas.Matricula;
import com.semillero.evaluacion.periodos.EstadoPeriodo;
import com.semillero.evaluacion.periodos.Periodo;
import com.semillero.evaluacion.profesores.Profesor;
import com.semillero.evaluacion.seguridad.JwtService;
import com.semillero.evaluacion.soporte.ContextoPruebas;
import com.semillero.evaluacion.usuarios.Rol;
import com.semillero.evaluacion.usuarios.Usuario;
import java.math.BigDecimal;
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

/**
 * Pruebas de endpoints de calificaciones, SIN Oracle (perfil test).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CalificacionControllerTests extends ContextoPruebas {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private Usuario usuario(String nombre, Rol rol) {
        Usuario u = new Usuario();
        u.setUsuario(nombre);
        u.setContrasenaHash(encoder.encode("ClaveActual123"));
        u.setRol(rol);
        u.setActivo(true);
        u.setCambioContrasenaPendiente(false);
        ReflectionTestUtils.setField(u, "id", 1L);
        return u;
    }

    private String token(Rol rol) {
        String nombre = rol.name().toLowerCase();
        Usuario cuenta = usuario(nombre, rol);
        when(usuarios.findByUsuario(nombre)).thenReturn(Optional.of(cuenta));
        return jwtService.generarToken(cuenta);
    }

    private Profesor profesor(Long id) {
        Profesor p = new Profesor();
        ReflectionTestUtils.setField(p, "id", id);
        p.setUsuarioId(1L);
        p.setDocumento("80100" + id);
        p.setNombres("Docente");
        p.setApellidos("Prueba");
        p.setEstado(com.semillero.evaluacion.profesores.EstadoProfesor.ACTIVO);
        return p;
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

    private Periodo periodo(Long id, Long anioId, EstadoPeriodo estado) {
        Periodo p = new Periodo();
        ReflectionTestUtils.setField(p, "id", id);
        p.setAnioId(anioId);
        p.setNumero(1);
        p.setNombre("Primer periodo");
        p.setEstado(estado);
        return p;
    }

    private Matricula matricula(Long id) {
        Matricula m = new Matricula();
        ReflectionTestUtils.setField(m, "id", id);
        m.setEstudianteId(40L);
        m.setGrupoId(6L);
        m.setAnioId(5L);
        m.setEstado(EstadoMatricula.ACTIVA);
        return m;
    }

    private String cuerpoValido() {
        return "{\"matriculaId\":70,\"asignacionId\":500,\"numeroEvaluacion\":1,"
                + "\"nota\":4.5,\"comentario\":\"Regular\"}";
    }

    @Test
    @DisplayName("POST /calificaciones como PROFESOR titular: 201 con ids derivados")
    void crearComoProfesor() throws Exception {
        when(profesores.findByUsuarioId(1L)).thenReturn(Optional.of(profesor(3L)));
        when(asignaciones.findById(500L)).thenReturn(Optional.of(
                asignacion(500L, 3L, EstadoAsignacion.ACTIVA)));
        when(periodos.findById(10L)).thenReturn(Optional.of(periodo(10L, 5L,
                EstadoPeriodo.ABIERTO)));
        when(matriculas.findById(70L)).thenReturn(Optional.of(matricula(70L)));
        when(calificaciones.findByEstudianteIdAndMateriaIdAndPeriodoIdAndNumeroEvaluacion(
                eq(40L), eq(2L), eq(10L), eq(1))).thenReturn(Optional.empty());
        when(calificaciones.save(any())).thenAnswer(invocacion -> {
            Calificacion c = invocacion.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 900L);
            return c;
        });

        mockMvc.perform(post("/api/v1/calificaciones")
                        .header("Authorization", "Bearer " + token(Rol.PROFESOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(900))
                .andExpect(jsonPath("$.estudianteId").value(40))
                .andExpect(jsonPath("$.materiaId").value(2))
                .andExpect(jsonPath("$.periodoId").value(10))
                .andExpect(jsonPath("$.nota").value(4.5));
    }

    @Test
    @DisplayName("POST /calificaciones como ESTUDIANTE: 403 (solo PROFESOR); DTO invalido: 400")
    void crearProhibidoOInvalido() throws Exception {
        mockMvc.perform(post("/api/v1/calificaciones")
                        .header("Authorization", "Bearer " + token(Rol.ESTUDIANTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/calificaciones")
                        .header("Authorization", "Bearer " + token(Rol.PROFESOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matriculaId\":70,\"asignacionId\":500,"
                                + "\"numeroEvaluacion\":1,\"nota\":6.0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /calificaciones/{id} como PROFESOR titular: 200; no titular: 403")
    void editarComoProfesor() throws Exception {
        when(profesores.findByUsuarioId(1L)).thenReturn(Optional.of(profesor(3L)));
        Calificacion existente = new Calificacion();
        ReflectionTestUtils.setField(existente, "id", 900L);
        existente.setMatriculaId(70L);
        existente.setEstudianteId(40L);
        existente.setAsignacionId(500L);
        existente.setMateriaId(2L);
        existente.setGrupoId(6L);
        existente.setAnioId(5L);
        existente.setPeriodoId(10L);
        existente.setNumeroEvaluacion(1);
        existente.setNota(new BigDecimal("4.50"));
        existente.setRegistradoPor(31L);
        when(calificaciones.findById(900L)).thenReturn(Optional.of(existente));

        when(asignaciones.findById(500L)).thenReturn(Optional.of(
                asignacion(500L, 3L, EstadoAsignacion.ACTIVA)));
        when(periodos.findById(10L)).thenReturn(Optional.of(periodo(10L, 5L,
                EstadoPeriodo.ABIERTO)));
        when(matriculas.findById(70L)).thenReturn(Optional.of(matricula(70L)));
        when(calificaciones.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        mockMvc.perform(put("/api/v1/calificaciones/900")
                        .header("Authorization", "Bearer " + token(Rol.PROFESOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nota\":4.8,\"comentario\":\"Mejorado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nota").value(4.8))
                .andExpect(jsonPath("$.comentario").value("Mejorado"));

        // Co-docente de OTRA asignacion: 403 con cuerpo valido (despues de la validacion).
        when(asignaciones.findById(500L)).thenReturn(Optional.of(
                asignacion(500L, 4L, EstadoAsignacion.ACTIVA)));
        mockMvc.perform(put("/api/v1/calificaciones/900")
                        .header("Authorization", "Bearer " + token(Rol.PROFESOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nota\":4.8,\"comentario\":\"Mejorado\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /calificaciones?estudianteId=40 lista (cualquier rol); GET /{id} inexistente: 404")
    void consultas() throws Exception {
        Calificacion fila = new Calificacion();
        ReflectionTestUtils.setField(fila, "id", 900L);
        fila.setMatriculaId(70L);
        fila.setEstudianteId(40L);
        fila.setAsignacionId(500L);
        fila.setMateriaId(2L);
        fila.setGrupoId(6L);
        fila.setAnioId(5L);
        fila.setPeriodoId(10L);
        fila.setNumeroEvaluacion(1);
        fila.setNota(new BigDecimal("4.50"));
        fila.setRegistradoPor(31L);
        org.springframework.data.domain.PageRequest pagina =
                org.springframework.data.domain.PageRequest.of(0, 20);
        when(calificaciones.findByEstudianteIdOrderByAnioIdAscMateriaIdAscNumeroEvaluacionAsc(
                eq(40L), org.mockito.ArgumentMatchers.any())).thenReturn(
                new org.springframework.data.domain.PageImpl<>(List.of(fila), pagina, 1));

        String t = token(Rol.ESTUDIANTE);
        mockMvc.perform(get("/api/v1/calificaciones")
                        .queryParam("estudianteId", "40")
                        .header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamano").value(20))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].estudianteId").value(40));

        when(calificaciones.findById(99L)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/v1/calificaciones/99")
                        .header("Authorization", "Bearer " + t))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /calificaciones con espacio de evaluacion ocupado: 409 CONFLICT")
    void crearSobreEspacioOcupado409() throws Exception {
        when(profesores.findByUsuarioId(1L)).thenReturn(Optional.of(profesor(3L)));
        when(asignaciones.findById(500L)).thenReturn(Optional.of(
                asignacion(500L, 3L, EstadoAsignacion.ACTIVA)));
        when(periodos.findById(10L)).thenReturn(Optional.of(periodo(10L, 5L,
                EstadoPeriodo.ABIERTO)));
        when(matriculas.findById(70L)).thenReturn(Optional.of(matricula(70L)));
        Calificacion ocupada = new Calificacion();
        ReflectionTestUtils.setField(ocupada, "id", 899L);
        when(calificaciones.findByEstudianteIdAndMateriaIdAndPeriodoIdAndNumeroEvaluacion(
                eq(40L), eq(2L), eq(10L), eq(1))).thenReturn(Optional.of(ocupada));

        mockMvc.perform(post("/api/v1/calificaciones")
                        .header("Authorization", "Bearer " + token(Rol.PROFESOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.estado").value(409))
                .andExpect(jsonPath("$.mensaje").value(
                        "La evaluacion 1 del estudiante en esta materia y periodo ya esta "
                                + "registrada (calificacion id 899)"));
    }

    @Test
    @DisplayName("PUT /calificaciones/{id} con fila inconsistente con su asignacion: 400 (antes 500)")
    void editarConFilaInconsistente400() throws Exception {
        when(profesores.findByUsuarioId(1L)).thenReturn(Optional.of(profesor(3L)));
        Calificacion inconsistente = new Calificacion();
        ReflectionTestUtils.setField(inconsistente, "id", 900L);
        inconsistente.setMatriculaId(70L);
        inconsistente.setEstudianteId(40L);
        inconsistente.setAsignacionId(500L);
        inconsistente.setMateriaId(3L);
        inconsistente.setGrupoId(6L);
        inconsistente.setAnioId(5L);
        inconsistente.setPeriodoId(10L);
        inconsistente.setNumeroEvaluacion(1);
        inconsistente.setNota(new BigDecimal("4.50"));
        when(calificaciones.findById(900L)).thenReturn(Optional.of(inconsistente));
        when(asignaciones.findById(500L)).thenReturn(Optional.of(
                asignacion(500L, 3L, EstadoAsignacion.ACTIVA)));

        mockMvc.perform(put("/api/v1/calificaciones/900")
                        .header("Authorization", "Bearer " + token(Rol.PROFESOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nota\":4.8,\"comentario\":\"Mejorado\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400))
                .andExpect(jsonPath("$.mensaje").value(
                        "No se puede cambiar la asignación original de una calificación."));
    }

    @Test
    @DisplayName("GET /calificaciones/pendientes: espacios 1..10 libres (cualquier rol)")
    void pendientes() throws Exception {
        when(asignaciones.findById(500L)).thenReturn(Optional.of(
                asignacion(500L, 3L, EstadoAsignacion.ACTIVA)));
        Calificacion ocupada = new Calificacion();
        ocupada.setNumeroEvaluacion(4);
        when(calificaciones.findByEstudianteIdAndMateriaIdAndPeriodoId(40L, 2L, 10L))
                .thenReturn(List.of(ocupada));

        mockMvc.perform(get("/api/v1/calificaciones/pendientes")
                        .queryParam("estudianteId", "40")
                        .queryParam("asignacionId", "500")
                        .header("Authorization", "Bearer " + token(Rol.ACUDIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9))
                .andExpect(jsonPath("$[0]").value(1))
                .andExpect(jsonPath("$[8]").value(10));
    }
}
