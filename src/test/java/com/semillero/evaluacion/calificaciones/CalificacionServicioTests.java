package com.semillero.evaluacion.calificaciones;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.semillero.evaluacion.asignaciones.Asignacion;
import com.semillero.evaluacion.asignaciones.AsignacionRepositorio;
import com.semillero.evaluacion.asignaciones.EstadoAsignacion;
import com.semillero.evaluacion.auditoria.AuditoriaServicio;
import com.semillero.evaluacion.calificaciones.dto.ActualizarCalificacionPeticion;
import com.semillero.evaluacion.calificaciones.dto.CalificacionRespuesta;
import com.semillero.evaluacion.calificaciones.dto.CrearCalificacionPeticion;
import com.semillero.evaluacion.comun.PaginaRespuesta;
import com.semillero.evaluacion.exception.ConflictoEstadoException;
import com.semillero.evaluacion.exception.RecursoNoEncontradoException;
import com.semillero.evaluacion.exception.ReglaNegocioException;
import com.semillero.evaluacion.matriculas.EstadoMatricula;
import com.semillero.evaluacion.matriculas.Matricula;
import com.semillero.evaluacion.matriculas.MatriculaRepositorio;
import com.semillero.evaluacion.periodos.EstadoPeriodo;
import com.semillero.evaluacion.periodos.Periodo;
import com.semillero.evaluacion.periodos.PeriodoRepositorio;
import com.semillero.evaluacion.profesores.Profesor;
import com.semillero.evaluacion.profesores.ProfesorRepositorio;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas unitarias de las reglas de negocio de calificaciones, sin base de
 * datos (repositorios y auditoria simulados).
 */
class CalificacionServicioTests {

    private CalificacionRepositorio repositorio;
    private MatriculaRepositorio matriculas;
    private AsignacionRepositorio asignaciones;
    private PeriodoRepositorio periodos;
    private ProfesorRepositorio profesores;
    private AuditoriaServicio auditoria;
    private CalificacionServicio servicio;

    @BeforeEach
    void preparar() {
        repositorio = Mockito.mock(CalificacionRepositorio.class);
        matriculas = Mockito.mock(MatriculaRepositorio.class);
        asignaciones = Mockito.mock(AsignacionRepositorio.class);
        periodos = Mockito.mock(PeriodoRepositorio.class);
        profesores = Mockito.mock(ProfesorRepositorio.class);
        auditoria = Mockito.mock(AuditoriaServicio.class);
        doAnswer(invocacion -> null).when(auditoria)
                .registrar(anyString(), anyString(), anyString(), any(), any(), anyString());
        servicio = new CalificacionServicio(repositorio, matriculas, asignaciones,
                periodos, profesores, auditoria);
    }

    private Profesor profesor(Long id) {
        Profesor p = new Profesor();
        ReflectionTestUtils.setField(p, "id", id);
        p.setUsuarioId(30L + id);
        p.setDocumento("80100" + id);
        p.setNombres("Docente");
        p.setApellidos("Prueba");
        p.setEstado(com.semillero.evaluacion.profesores.EstadoProfesor.ACTIVO);
        return p;
    }

    /** Asignacion canonica de las pruebas: titular 3, materia 2, grupo 6, anio 5, periodo 10. */
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

    private Matricula matricula(Long id, Long estudianteId, Long grupoId, Long anioId,
            EstadoMatricula estado) {
        Matricula m = new Matricula();
        ReflectionTestUtils.setField(m, "id", id);
        m.setEstudianteId(estudianteId);
        m.setGrupoId(grupoId);
        m.setAnioId(anioId);
        m.setEstado(estado);
        return m;
    }

    private Calificacion calificacion(Long id) {
        Calificacion c = new Calificacion();
        ReflectionTestUtils.setField(c, "id", id);
        c.setMatriculaId(70L);
        c.setEstudianteId(40L);
        c.setAsignacionId(500L);
        c.setMateriaId(2L);
        c.setGrupoId(6L);
        c.setAnioId(5L);
        c.setPeriodoId(10L);
        c.setNumeroEvaluacion(3);
        c.setNota(new BigDecimal("4.50"));
        c.setComentario("Bien");
        c.setRegistradoPor(31L);
        return c;
    }

    private CrearCalificacionPeticion peticion() {
        return new CrearCalificacionPeticion(70L, 500L, 1,
                new BigDecimal("4.5"), "Regular");
    }

    /** Escenario valido: profesor 31 (usuario 31) titular de la asignacion 500 ACTIVA. */
    private void prepararValidos() {
        when(profesores.findByUsuarioId(31L)).thenReturn(Optional.of(profesor(3L)));
        when(asignaciones.findById(500L)).thenReturn(Optional.of(
                asignacion(500L, 3L, EstadoAsignacion.ACTIVA)));
        when(periodos.findById(10L)).thenReturn(Optional.of(periodo(10L, 5L,
                EstadoPeriodo.ABIERTO)));
        when(matriculas.findById(70L)).thenReturn(Optional.of(
                matricula(70L, 40L, 6L, 5L, EstadoMatricula.ACTIVA)));
        when(repositorio.findByEstudianteIdAndMateriaIdAndPeriodoIdAndNumeroEvaluacion(
                eq(40L), eq(2L), eq(10L), eq(1))).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("crear: nota con ids derivados de matricula y asignacion, auditada como CREAR")
    void crearCalificacion() {
        prepararValidos();
        when(repositorio.save(any())).thenAnswer(inv -> {
            Calificacion c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 900L);
            return c;
        });

        CalificacionRespuesta respuesta = servicio.crear(peticion(), 31L, "127.0.0.1");

        assertEquals(900L, respuesta.id());
        assertEquals(70L, respuesta.matriculaId());
        assertEquals(40L, respuesta.estudianteId());
        assertEquals(500L, respuesta.asignacionId());
        assertEquals(2L, respuesta.materiaId());
        assertEquals(6L, respuesta.grupoId());
        assertEquals(5L, respuesta.anioId());
        assertEquals(10L, respuesta.periodoId());
        assertEquals(1, respuesta.numeroEvaluacion());
        assertEquals(new BigDecimal("4.50"), respuesta.nota());
        assertEquals(31L, respuesta.registradoPor());
        verify(auditoria).registrar(eq("CREAR"), eq("CALIFICACIONES"), anyString(),
                eq(31L), any(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("crear: cuenta sin perfil docente -> 404; no titular -> 403")
    void crearSinPerfilOComoNoTitular() {
        prepararValidos();
        when(profesores.findByUsuarioId(99L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.crear(peticion(), 99L, "127.0.0.1"));

        when(asignaciones.findById(501L)).thenReturn(Optional.of(
                asignacion(501L, 4L, EstadoAsignacion.ACTIVA)));
        assertThrows(AccessDeniedException.class, () -> servicio.crear(
                new CrearCalificacionPeticion(70L, 501L, 1,
                        new BigDecimal("4.5"), null), 31L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("crear: asignacion INACTIVA -> 400; periodo no ABIERTO -> 400")
    void crearConCicloInvalido() {
        prepararValidos();
        when(asignaciones.findById(502L)).thenReturn(Optional.of(
                asignacion(502L, 3L, EstadoAsignacion.INACTIVA)));
        assertThrows(ReglaNegocioException.class, () -> servicio.crear(
                new CrearCalificacionPeticion(70L, 502L, 1,
                        new BigDecimal("4.5"), null), 31L, "127.0.0.1"));

        when(periodos.findById(10L)).thenReturn(Optional.of(periodo(10L, 5L,
                EstadoPeriodo.CERRADO)));
        assertThrows(ReglaNegocioException.class, () -> servicio.crear(peticion(), 31L,
                "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("crear: matricula RETIRADA -> 400; de otro grupo/anio -> 400")
    void crearConMatriculaInvalida() {
        prepararValidos();
        when(matriculas.findById(71L)).thenReturn(Optional.of(
                matricula(71L, 41L, 6L, 5L, EstadoMatricula.RETIRADA)));
        assertThrows(ReglaNegocioException.class, () -> servicio.crear(
                new CrearCalificacionPeticion(71L, 500L, 1,
                        new BigDecimal("4.5"), null), 31L, "127.0.0.1"));

        when(matriculas.findById(72L)).thenReturn(Optional.of(
                matricula(72L, 42L, 7L, 5L, EstadoMatricula.ACTIVA)));
        assertThrows(ReglaNegocioException.class, () -> servicio.crear(
                new CrearCalificacionPeticion(72L, 500L, 1,
                        new BigDecimal("4.5"), null), 31L, "127.0.0.1"));

        when(matriculas.findById(73L)).thenReturn(Optional.of(
                matricula(73L, 43L, 6L, 9L, EstadoMatricula.ACTIVA)));
        assertThrows(ReglaNegocioException.class, () -> servicio.crear(
                new CrearCalificacionPeticion(73L, 500L, 1,
                        new BigDecimal("4.5"), null), 31L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("crear: espacio de evaluacion ocupado -> 400 (UNIQUE, co-docentes comparten)")
    void crearSobreEspacioOcupado() {
        prepararValidos();
        Calificacion ocupada = calificacion(900L);
        when(repositorio.findByEstudianteIdAndMateriaIdAndPeriodoIdAndNumeroEvaluacion(
                eq(40L), eq(2L), eq(10L), eq(1))).thenReturn(Optional.of(ocupada));

        assertThrows(ReglaNegocioException.class, () -> servicio.crear(peticion(), 31L,
                "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("crear: asignacion, periodo o matricula inexistentes -> 404")
    void crearConInexistentes() {
        prepararValidos();
        when(asignaciones.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.crear(
                new CrearCalificacionPeticion(70L, 99L, 1,
                        new BigDecimal("4.5"), null), 31L, "127.0.0.1"));

        when(periodos.findById(10L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.crear(peticion(),
                31L, "127.0.0.1"));

        when(periodos.findById(10L)).thenReturn(Optional.of(periodo(10L, 5L,
                EstadoPeriodo.ABIERTO)));
        when(matriculas.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.crear(
                new CrearCalificacionPeticion(99L, 500L, 1,
                        new BigDecimal("4.5"), null), 31L, "127.0.0.1"));
    }

    @Test
    @DisplayName("editar: cambia nota y comentario, fija actualizadoPor, auditado como ACTUALIZAR")
    void editarCalificacion() {
        prepararValidos();
        when(repositorio.findById(900L)).thenReturn(Optional.of(calificacion(900L)));
        when(matriculas.findById(70L)).thenReturn(Optional.of(
                matricula(70L, 40L, 6L, 5L, EstadoMatricula.ACTIVA)));
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CalificacionRespuesta respuesta = servicio.editar(900L,
                new ActualizarCalificacionPeticion(new BigDecimal("4.80"), "Mejorado"),
                31L, "127.0.0.1");

        assertEquals(new BigDecimal("4.80"), respuesta.nota());
        assertEquals("Mejorado", respuesta.comentario());
        assertEquals(31L, respuesta.actualizadoPor());
        assertEquals(3, respuesta.numeroEvaluacion());
        assertEquals(500L, respuesta.asignacionId());
        verify(auditoria).registrar(eq("ACTUALIZAR"), eq("CALIFICACIONES"), anyString(),
                eq(31L), any(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("editar: no titular -> 403; inexistente -> 404")
    void editarSinPermisoOInexistente() {
        prepararValidos();
        when(repositorio.findById(900L)).thenReturn(Optional.of(calificacion(900L)));

        when(asignaciones.findById(500L)).thenReturn(Optional.of(
                asignacion(500L, 4L, EstadoAsignacion.ACTIVA)));
        assertThrows(AccessDeniedException.class, () -> servicio.editar(900L,
                new ActualizarCalificacionPeticion(new BigDecimal("4.80"), null),
                31L, "127.0.0.1"));

        when(asignaciones.findById(500L)).thenReturn(Optional.of(
                asignacion(500L, 3L, EstadoAsignacion.ACTIVA)));
        when(repositorio.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.editar(99L,
                new ActualizarCalificacionPeticion(new BigDecimal("4.80"), null),
                31L, "127.0.0.1"));
    }

    @Test
    @DisplayName("editar: fila inconsistente con su asignacion -> IllegalArgumentException; periodo CERRADO o matricula RETIRADA -> 400")
    void editarConEstadoInvalido() {
        prepararValidos();
        // Fila cuyas columnas copiadas no corresponden a su asignacion 500
        // (materia distinta): el validator de inmutabilidad lo rechaza.
        Calificacion inconsistente = calificacion(900L);
        inconsistente.setMateriaId(3L);
        when(repositorio.findById(900L)).thenReturn(Optional.of(inconsistente));

        when(asignaciones.findById(500L)).thenReturn(Optional.of(
                asignacion(500L, 3L, EstadoAsignacion.ACTIVA)));
        when(periodos.findById(10L)).thenReturn(Optional.of(periodo(10L, 5L,
                EstadoPeriodo.ABIERTO)));
        when(matriculas.findById(70L)).thenReturn(Optional.of(
                matricula(70L, 40L, 6L, 5L, EstadoMatricula.ACTIVA)));
        assertThrows(IllegalArgumentException.class, () -> servicio.editar(900L,
                new ActualizarCalificacionPeticion(new BigDecimal("4.80"), null),
                31L, "127.0.0.1"));

        // Fila coherente, pero periodo CERRADO y luego matricula FINALIZADA.
        Calificacion coherente = calificacion(900L);
        when(repositorio.findById(900L)).thenReturn(Optional.of(coherente));
        when(periodos.findById(10L)).thenReturn(Optional.of(periodo(10L, 5L,
                EstadoPeriodo.CERRADO)));
        assertThrows(ReglaNegocioException.class, () -> servicio.editar(900L,
                new ActualizarCalificacionPeticion(new BigDecimal("4.80"), null),
                31L, "127.0.0.1"));

        when(periodos.findById(10L)).thenReturn(Optional.of(periodo(10L, 5L,
                EstadoPeriodo.ABIERTO)));
        when(matriculas.findById(70L)).thenReturn(Optional.of(
                matricula(70L, 40L, 6L, 5L, EstadoMatricula.FINALIZADA)));
        assertThrows(ReglaNegocioException.class, () -> servicio.editar(900L,
                new ActualizarCalificacionPeticion(new BigDecimal("4.80"), null),
                31L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("listar: delega en el metodo derivado paginado correcto segun los filtros")
    void listarConFiltros() {
        PageRequest pagina = PageRequest.of(0, 20);
        Page<Calificacion> fila = new PageImpl<>(List.of(calificacion(900L)), pagina, 1);
        when(repositorio.findByEstudianteIdAndPeriodoIdOrderByMateriaIdAscNumeroEvaluacionAsc(
                40L, 10L, pagina)).thenReturn(fila);
        assertEquals(1, servicio.listar(40L, null, null, null, null, 10L, pagina)
                .totalElementos());

        when(repositorio.findByAsignacionIdOrderByNumeroEvaluacionAsc(500L, pagina))
                .thenReturn(fila);
        assertEquals(1, servicio.listar(null, 500L, null, null, null, null, pagina)
                .totalElementos());

        when(repositorio.findByMateriaIdAndPeriodoIdOrderByEstudianteIdAscNumeroEvaluacionAsc(
                2L, 10L, pagina)).thenReturn(fila);
        assertEquals(1, servicio.listar(null, null, 2L, null, null, 10L, pagina)
                .totalElementos());

        when(repositorio.findByGrupoIdOrderByMateriaIdAscEstudianteIdAscNumeroEvaluacionAsc(
                6L, pagina)).thenReturn(fila);
        assertEquals(1, servicio.listar(null, null, null, 6L, null, null, pagina)
                .totalElementos());

        when(repositorio.findByAnioIdOrderByGrupoIdAscMateriaIdAscEstudianteIdAsc(5L, pagina))
                .thenReturn(fila);
        assertEquals(1, servicio.listar(null, null, null, null, 5L, null, pagina)
                .totalElementos());

        when(repositorio.findAll(any(Pageable.class))).thenReturn(fila);
        PaginaRespuesta<CalificacionRespuesta> todas = servicio
                .listar(null, null, null, null, null, null, pagina);
        assertEquals(1, todas.totalElementos());
        assertEquals(1, todas.contenido().size());
        assertEquals(900L, todas.contenido().get(0).id());
    }

    @Test
    @DisplayName("listar: pagina fuera de rango devuelve contenido vacio con metadata correcta")
    void listarPaginaVacia() {
        PageRequest pagina = PageRequest.of(3, 20);
        when(repositorio.findAll(any(Pageable.class)))
                .thenReturn(Page.empty(pagina));
        PaginaRespuesta<CalificacionRespuesta> respuesta = servicio
                .listar(null, null, null, null, null, null, pagina);
        assertTrue(respuesta.contenido().isEmpty());
        assertEquals(3, respuesta.pagina());
        assertEquals(20, respuesta.tamano());
        assertEquals(0, respuesta.totalElementos());
    }

    @Test
    @DisplayName("pendientes: numeros 1..10 sin nota; ocupados se excluyen (UNA sola consulta)")
    void pendientesDelEstudiante() {
        prepararValidos();
        when(repositorio.findByEstudianteIdAndMateriaIdAndPeriodoId(40L, 2L, 10L))
                .thenReturn(List.of(calificacion(900L))); // numeroEvaluacion 3

        List<Integer> libres = servicio.pendientes(40L, 500L);

        assertEquals(9, libres.size());
        assertEquals(List.of(1, 2, 4, 5, 6, 7, 8, 9, 10), libres);
        verify(repositorio, Mockito.times(1))
                .findByEstudianteIdAndMateriaIdAndPeriodoId(40L, 2L, 10L);
        verify(repositorio, never())
                .findByEstudianteIdAndMateriaIdAndPeriodoIdAndNumeroEvaluacion(
                        anyLong(), anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("pendientes: asignacion inexistente -> 404")
    void pendientesConAsignacionInexistente() {
        when(asignaciones.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.pendientes(40L, 99L));
    }
}
