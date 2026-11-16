package com.semillero.evaluacion.asignaciones;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.semillero.evaluacion.anios.AnioAcademico;
import com.semillero.evaluacion.anios.AnioRepositorio;
import com.semillero.evaluacion.anios.EstadoAnio;
import com.semillero.evaluacion.auditoria.AuditoriaServicio;
import com.semillero.evaluacion.exception.RecursoNoEncontradoException;
import com.semillero.evaluacion.exception.ReglaNegocioException;
import com.semillero.evaluacion.asignaciones.dto.AsignacionRespuesta;
import com.semillero.evaluacion.asignaciones.dto.CrearAsignacionPeticion;
import com.semillero.evaluacion.grupos.EstadoGrupo;
import com.semillero.evaluacion.grupos.Grupo;
import com.semillero.evaluacion.grupos.GrupoRepositorio;
import com.semillero.evaluacion.materias.EstadoMateria;
import com.semillero.evaluacion.materias.Materia;
import com.semillero.evaluacion.materias.MateriaRepositorio;
import com.semillero.evaluacion.periodos.EstadoPeriodo;
import com.semillero.evaluacion.periodos.Periodo;
import com.semillero.evaluacion.periodos.PeriodoRepositorio;
import com.semillero.evaluacion.profesores.EstadoProfesor;
import com.semillero.evaluacion.profesores.Profesor;
import com.semillero.evaluacion.profesores.ProfesorRepositorio;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Pruebas unitarias de las reglas de negocio de asignaciones docentes, sin
 * base de datos (repositorios y auditoria simulados).
 */
class AsignacionServicioTests {

    private AsignacionRepositorio repositorio;
    private ProfesorRepositorio profesores;
    private MateriaRepositorio materias;
    private GrupoRepositorio grupos;
    private PeriodoRepositorio periodos;
    private AnioRepositorio anios;
    private AuditoriaServicio auditoria;
    private AsignacionServicio servicio;

    @BeforeEach
    void preparar() {
        repositorio = Mockito.mock(AsignacionRepositorio.class);
        profesores = Mockito.mock(ProfesorRepositorio.class);
        materias = Mockito.mock(MateriaRepositorio.class);
        grupos = Mockito.mock(GrupoRepositorio.class);
        periodos = Mockito.mock(PeriodoRepositorio.class);
        anios = Mockito.mock(AnioRepositorio.class);
        auditoria = Mockito.mock(AuditoriaServicio.class);
        doAnswer(invocacion -> null).when(auditoria)
                .registrar(anyString(), anyString(), anyString(), any(), any(), anyString());
        servicio = new AsignacionServicio(repositorio, profesores, materias, grupos,
                periodos, anios, auditoria);
    }

    private Profesor profesor(Long id, EstadoProfesor estado) {
        Profesor p = new Profesor();
        ReflectionTestUtils.setField(p, "id", id);
        p.setUsuarioId(30L + id);
        p.setDocumento("80100" + id);
        p.setNombres("Docente");
        p.setApellidos("Prueba");
        p.setEstado(estado);
        return p;
    }

    private Materia materia(Long id, EstadoMateria estado) {
        Materia m = new Materia();
        ReflectionTestUtils.setField(m, "id", id);
        m.setCodigo("MAT-10" + id);
        m.setNombre("Materia " + id);
        m.setEstado(estado);
        return m;
    }

    private Grupo grupo(Long id, Long anioId, EstadoGrupo estado) {
        Grupo g = new Grupo();
        ReflectionTestUtils.setField(g, "id", id);
        g.setAnioId(anioId);
        g.setNombre("10-A");
        g.setEstado(estado);
        return g;
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

    private AnioAcademico anio(Long id, Integer numero, EstadoAnio estado) {
        AnioAcademico a = new AnioAcademico();
        ReflectionTestUtils.setField(a, "id", id);
        a.setAnio(numero);
        a.setEstado(estado);
        return a;
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

    /** Datos por defecto validos: profesor/materia/grupo ACTIVOS, periodo ABIERTO del anio 5. */
    private void prepararValidos() {
        when(profesores.findById(3L)).thenReturn(Optional.of(
                profesor(3L, EstadoProfesor.ACTIVO)));
        when(materias.findById(2L)).thenReturn(Optional.of(materia(2L, EstadoMateria.ACTIVO)));
        when(grupos.findById(6L)).thenReturn(Optional.of(grupo(6L, 5L, EstadoGrupo.ACTIVO)));
        when(periodos.findById(10L)).thenReturn(Optional.of(periodo(10L, 5L,
                EstadoPeriodo.ABIERTO)));
        when(anios.findById(5L)).thenReturn(Optional.of(anio(5L, 2026, EstadoAnio.ACTIVO)));
    }

    @Test
    @DisplayName("crear: asignacion ACTIVA con anio derivado del grupo, auditada como CREAR")
    void crearAsignacion() {
        prepararValidos();
        when(repositorio.findByProfesorIdAndMateriaIdAndGrupoIdAndPeriodoIdAndEstado(
                eq(3L), eq(2L), eq(6L), eq(10L), eq(EstadoAsignacion.ACTIVA)))
                .thenReturn(Optional.empty());
        when(repositorio.save(any())).thenAnswer(inv -> {
            Asignacion a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", 500L);
            return a;
        });

        AsignacionRespuesta respuesta = servicio.crear(
                new CrearAsignacionPeticion(3L, 2L, 6L, 10L), 1L, "127.0.0.1");

        assertEquals(500L, respuesta.id());
        assertEquals(5L, respuesta.anioId());
        assertEquals(2026, respuesta.anio());
        assertEquals("ACTIVA", respuesta.estado());
        verify(auditoria).registrar(eq("CREAR"), eq("ASIGNACIONES_DOCENTES"), anyString(),
                eq(1L), any(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("crear: profesor, materia o grupo INACTIVO -> 400")
    void crearConParticipantesInactivos() {
        prepararValidos();
        when(profesores.findById(4L)).thenReturn(Optional.of(
                profesor(4L, EstadoProfesor.INACTIVO)));
        assertThrows(ReglaNegocioException.class, () -> servicio.crear(
                new CrearAsignacionPeticion(4L, 2L, 6L, 10L), 1L, "127.0.0.1"));

        when(materias.findById(7L)).thenReturn(Optional.of(materia(7L, EstadoMateria.INACTIVO)));
        assertThrows(ReglaNegocioException.class, () -> servicio.crear(
                new CrearAsignacionPeticion(3L, 7L, 6L, 10L), 1L, "127.0.0.1"));

        when(grupos.findById(8L)).thenReturn(Optional.of(grupo(8L, 5L, EstadoGrupo.INACTIVO)));
        assertThrows(ReglaNegocioException.class, () -> servicio.crear(
                new CrearAsignacionPeticion(3L, 2L, 8L, 10L), 1L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("crear: periodo CERRADO -> 400; anio CERRADO -> 400")
    void crearConCicloInvalido() {
        prepararValidos();
        when(periodos.findById(11L)).thenReturn(Optional.of(periodo(11L, 5L,
                EstadoPeriodo.CERRADO)));
        assertThrows(ReglaNegocioException.class, () -> servicio.crear(
                new CrearAsignacionPeticion(3L, 2L, 6L, 11L), 1L, "127.0.0.1"));

        when(anios.findById(5L)).thenReturn(Optional.of(anio(5L, 2025, EstadoAnio.CERRADO)));
        assertThrows(ReglaNegocioException.class, () -> servicio.crear(
                new CrearAsignacionPeticion(3L, 2L, 6L, 10L), 1L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("crear: grupo y periodo de anios distintos -> 400 (coherencia de FKs)")
    void crearConAniosIncoherentes() {
        prepararValidos();
        when(periodos.findById(12L)).thenReturn(Optional.of(periodo(12L, 9L,
                EstadoPeriodo.ABIERTO)));

        assertThrows(ReglaNegocioException.class, () -> servicio.crear(
                new CrearAsignacionPeticion(3L, 2L, 6L, 12L), 1L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("crear: asignacion ACTIVA duplicada -> 400 (UX_ASIGNACION_ACTIVA)")
    void crearDuplicada() {
        prepararValidos();
        when(repositorio.findByProfesorIdAndMateriaIdAndGrupoIdAndPeriodoIdAndEstado(
                eq(3L), eq(2L), eq(6L), eq(10L), eq(EstadoAsignacion.ACTIVA)))
                .thenReturn(Optional.of(asignacion(500L, 3L, EstadoAsignacion.ACTIVA)));

        assertThrows(ReglaNegocioException.class, () -> servicio.crear(
                new CrearAsignacionPeticion(3L, 2L, 6L, 10L), 1L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("crear: referencias inexistentes -> 404")
    void crearConInexistentes() {
        prepararValidos();
        when(profesores.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.crear(
                new CrearAsignacionPeticion(99L, 2L, 6L, 10L), 1L, "127.0.0.1"));

        when(materias.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.crear(
                new CrearAsignacionPeticion(3L, 99L, 6L, 10L), 1L, "127.0.0.1"));

        when(grupos.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.crear(
                new CrearAsignacionPeticion(3L, 2L, 99L, 10L), 1L, "127.0.0.1"));

        when(periodos.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.crear(
                new CrearAsignacionPeticion(3L, 2L, 6L, 99L), 1L, "127.0.0.1"));
    }

    @Test
    @DisplayName("finalizar: ACTIVA -> INACTIVA auditada; no ACTIVA -> 400")
    void finalizarAsignacion() {
        when(repositorio.findById(500L)).thenReturn(Optional.of(
                asignacion(500L, 3L, EstadoAsignacion.ACTIVA)));
        when(anios.findById(5L)).thenReturn(Optional.of(anio(5L, 2026, EstadoAnio.ACTIVO)));
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AsignacionRespuesta respuesta = servicio.finalizar(500L, 1L, "127.0.0.1");

        assertEquals("INACTIVA", respuesta.estado());
        verify(auditoria).registrar(eq("DESACTIVAR"), eq("ASIGNACIONES_DOCENTES"), anyString(),
                eq(1L), any(), eq("127.0.0.1"));

        when(repositorio.findById(501L)).thenReturn(Optional.of(
                asignacion(501L, 3L, EstadoAsignacion.INACTIVA)));
        assertThrows(ReglaNegocioException.class, () -> servicio.finalizar(501L, 1L, "127.0.0.1"));
        verify(repositorio, Mockito.times(1)).save(any());
    }

    @Test
    @DisplayName("reemplazar: pasa la anterior a INACTIVA y crea una nueva con el entrante")
    void reemplazarProfesor() {
        when(repositorio.findById(500L)).thenReturn(Optional.of(
                asignacion(500L, 3L, EstadoAsignacion.ACTIVA)));
        when(profesores.findById(4L)).thenReturn(Optional.of(profesor(4L, EstadoProfesor.ACTIVO)));
        when(repositorio.findByProfesorIdAndMateriaIdAndGrupoIdAndPeriodoIdAndEstado(
                eq(4L), eq(2L), eq(6L), eq(10L), eq(EstadoAsignacion.ACTIVA)))
                .thenReturn(Optional.empty());
        when(anios.findById(5L)).thenReturn(Optional.of(anio(5L, 2026, EstadoAnio.ACTIVO)));
        when(repositorio.save(any())).thenAnswer(inv -> {
            Asignacion a = inv.getArgument(0);
            if (a.getId() == null) {
                ReflectionTestUtils.setField(a, "id", 502L);
            }
            return a;
        });

        AsignacionRespuesta respuesta = servicio.reemplazar(500L, 4L, 1L, "127.0.0.1");

        assertEquals("ACTIVA", respuesta.estado());
        assertEquals(4L, respuesta.profesorId());
        assertEquals(502L, respuesta.id());
        verify(repositorio, Mockito.times(2)).save(any());
        verify(auditoria).registrar(eq("ACTUALIZAR"), eq("ASIGNACIONES_DOCENTES"), anyString(),
                eq(1L), any(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("reemplazar: mismo profesor, asignacion no ACTIVA o entrante inactivo -> 400")
    void reemplazarInvalido() {
        prepararValidos();
        when(repositorio.findById(500L)).thenReturn(Optional.of(
                asignacion(500L, 3L, EstadoAsignacion.ACTIVA)));

        // El entrante ya es el titular.
        assertThrows(ReglaNegocioException.class, () -> servicio.reemplazar(500L, 3L, 1L,
                "127.0.0.1"));

        // El entrante esta inactivo.
        when(profesores.findById(4L)).thenReturn(Optional.of(profesor(4L, EstadoProfesor.INACTIVO)));
        assertThrows(ReglaNegocioException.class, () -> servicio.reemplazar(500L, 4L, 1L,
                "127.0.0.1"));

        // La asignacion ya esta finalizada.
        when(repositorio.findById(501L)).thenReturn(Optional.of(
                asignacion(501L, 3L, EstadoAsignacion.INACTIVA)));
        when(profesores.findById(4L)).thenReturn(Optional.of(profesor(4L, EstadoProfesor.ACTIVO)));
        assertThrows(ReglaNegocioException.class, () -> servicio.reemplazar(501L, 4L, 1L,
                "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("buscarPorId: id inexistente lanza 404")
    void buscarInexistente() {
        when(repositorio.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> servicio.buscarPorId(99L));
    }

    @Test
    @DisplayName("listar: el numero de anio aparece SIEMPRE (con o sin filtros), con una sola consulta batch")
    void listarResuelveElAnio() {
        Asignacion fila = asignacion(500L, 3L, EstadoAsignacion.ACTIVA);
        when(anios.findAllById(List.of(5L)))
                .thenReturn(List.of(anio(5L, 2026, EstadoAnio.ACTIVO)));

        // Sin filtros: findAll().
        when(repositorio.findAll()).thenReturn(List.of(fila));
        List<AsignacionRespuesta> todas = servicio.listar(null, null, null, null);
        assertEquals(1, todas.size());
        assertEquals(2026, todas.get(0).anio());
        verify(anios, Mockito.times(1)).findAllById(List.of(5L));
        verify(anios, never()).findById(5L);

        // Con filtro por profesor (sin anioId): tambien resuelve el anio.
        when(repositorio.findByProfesorIdOrderByCreadoEnAsc(3L)).thenReturn(List.of(fila));
        assertEquals(2026, servicio.listar(3L, null, null, null).get(0).anio());
    }

    @Test
    @DisplayName("listar: anio lectivo inexistente entre los resultados -> 404")
    void listarConAnioInexistente() {
        when(repositorio.findAll()).thenReturn(List.of(asignacion(500L, 3L, EstadoAsignacion.ACTIVA)));
        when(anios.findAllById(List.of(5L))).thenReturn(List.of());

        assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.listar(null, null, null, null));
    }
}
