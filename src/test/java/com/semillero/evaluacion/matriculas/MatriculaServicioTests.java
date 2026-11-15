package com.semillero.evaluacion.matriculas;

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
import com.semillero.evaluacion.estudiantes.EstadoEstudiante;
import com.semillero.evaluacion.estudiantes.Estudiante;
import com.semillero.evaluacion.estudiantes.EstudianteRepositorio;
import com.semillero.evaluacion.exception.RecursoNoEncontradoException;
import com.semillero.evaluacion.exception.ReglaNegocioException;
import com.semillero.evaluacion.grupos.EstadoGrupo;
import com.semillero.evaluacion.grupos.Grupo;
import com.semillero.evaluacion.grupos.GrupoRepositorio;
import com.semillero.evaluacion.matriculas.dto.CrearMatriculaPeticion;
import com.semillero.evaluacion.matriculas.dto.MatriculaRespuesta;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Pruebas unitarias de las reglas de negocio de matriculas, sin base de
 * datos (repositorios y auditoria simulados).
 */
class MatriculaServicioTests {

    private MatriculaRepositorio repositorio;
    private EstudianteRepositorio estudiantes;
    private GrupoRepositorio grupos;
    private AnioRepositorio anios;
    private AuditoriaServicio auditoria;
    private MatriculaServicio servicio;

    @BeforeEach
    void preparar() {
        repositorio = Mockito.mock(MatriculaRepositorio.class);
        estudiantes = Mockito.mock(EstudianteRepositorio.class);
        grupos = Mockito.mock(GrupoRepositorio.class);
        anios = Mockito.mock(AnioRepositorio.class);
        auditoria = Mockito.mock(AuditoriaServicio.class);
        doAnswer(invocacion -> null).when(auditoria)
                .registrar(anyString(), anyString(), anyString(), any(), any(), anyString());
        servicio = new MatriculaServicio(repositorio, estudiantes, grupos, anios, auditoria);
    }

    private Estudiante estudiante(Long id, Long usuarioId, EstadoEstudiante estado) {
        Estudiante e = new Estudiante();
        ReflectionTestUtils.setField(e, "id", id);
        e.setUsuarioId(usuarioId);
        e.setDocumento("1010" + id);
        e.setNombres("Nombres");
        e.setApellidos("Apellidos");
        e.setEstado(estado);
        return e;
    }

    private Grupo grupo(Long id, Long anioId, String nombre, EstadoGrupo estado) {
        Grupo g = new Grupo();
        ReflectionTestUtils.setField(g, "id", id);
        g.setAnioId(anioId);
        g.setNombre(nombre);
        g.setEstado(estado);
        return g;
    }

    private AnioAcademico anio(Long id, Integer numero, EstadoAnio estado) {
        AnioAcademico a = new AnioAcademico();
        ReflectionTestUtils.setField(a, "id", id);
        a.setAnio(numero);
        a.setEstado(estado);
        return a;
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

    private void prepararDatosValidos() {
        when(estudiantes.findById(10L)).thenReturn(Optional.of(
                estudiante(10L, 20L, EstadoEstudiante.ACTIVO)));
        when(grupos.findById(6L)).thenReturn(Optional.of(grupo(6L, 5L, "10-A", EstadoGrupo.ACTIVO)));
        when(anios.findById(5L)).thenReturn(Optional.of(anio(5L, 2026, EstadoAnio.ACTIVO)));
    }

    @Test
    @DisplayName("crear: matricula ACTIVA con anio derivado del grupo, auditada como CREAR")
    void crearMatricula() {
        prepararDatosValidos();
        when(repositorio.findByEstudianteIdAndGrupoId(10L, 6L)).thenReturn(Optional.empty());
        when(repositorio.findByEstudianteIdAndAnioIdAndEstado(10L, 5L, EstadoMatricula.ACTIVA))
                .thenReturn(Optional.empty());
        when(repositorio.save(any())).thenAnswer(inv -> {
            Matricula m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 100L);
            return m;
        });

        MatriculaRespuesta respuesta = servicio.crear(
                new CrearMatriculaPeticion(10L, 6L), 1L, "127.0.0.1");

        assertEquals(100L, respuesta.id());
        assertEquals(5L, respuesta.anioId());
        assertEquals(2026, respuesta.anio());
        assertEquals("ACTIVA", respuesta.estado());
        verify(auditoria).registrar(eq("CREAR"), eq("MATRICULAS"), anyString(), eq(1L),
                any(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("crear: matricula duplicada en el mismo grupo -> 400")
    void crearDuplicada() {
        prepararDatosValidos();
        when(repositorio.findByEstudianteIdAndGrupoId(10L, 6L)).thenReturn(Optional.of(
                matricula(100L, 10L, 6L, 5L, EstadoMatricula.ACTIVA)));

        assertThrows(ReglaNegocioException.class, () -> servicio.crear(
                new CrearMatriculaPeticion(10L, 6L), 1L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("crear: reingreso al mismo grupo reactiva la fila RETIRADA (regla del DDL)")
    void reactivarRetirada() {
        prepararDatosValidos();
        Matricula retirada = matricula(100L, 10L, 6L, 5L, EstadoMatricula.RETIRADA);
        when(repositorio.findByEstudianteIdAndGrupoId(10L, 6L)).thenReturn(Optional.of(retirada));
        when(repositorio.findByEstudianteIdAndAnioIdAndEstado(10L, 5L, EstadoMatricula.ACTIVA))
                .thenReturn(Optional.empty());
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MatriculaRespuesta respuesta = servicio.crear(
                new CrearMatriculaPeticion(10L, 6L), 1L, "127.0.0.1");

        assertEquals("ACTIVA", respuesta.estado());
        assertEquals(100L, respuesta.id());
        verify(auditoria).registrar(eq("ACTIVAR"), eq("MATRICULAS"), anyString(), eq(1L),
                any(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("crear: reingreso tras FINALIZADA -> 400")
    void crearTrasFinalizada() {
        prepararDatosValidos();
        when(repositorio.findByEstudianteIdAndGrupoId(10L, 6L)).thenReturn(Optional.of(
                matricula(100L, 10L, 6L, 5L, EstadoMatricula.FINALIZADA)));

        assertThrows(ReglaNegocioException.class, () -> servicio.crear(
                new CrearMatriculaPeticion(10L, 6L), 1L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("crear: ya hay una ACTIVA en otro grupo del mismo anio -> 400 (UX_MATRICULA_ACTIVA)")
    void crearConOtraActivaMismoAnio() {
        prepararDatosValidos();
        when(repositorio.findByEstudianteIdAndGrupoId(10L, 6L)).thenReturn(Optional.empty());
        when(repositorio.findByEstudianteIdAndAnioIdAndEstado(10L, 5L, EstadoMatricula.ACTIVA))
                .thenReturn(Optional.of(matricula(101L, 10L, 7L, 5L, EstadoMatricula.ACTIVA)));

        assertThrows(ReglaNegocioException.class, () -> servicio.crear(
                new CrearMatriculaPeticion(10L, 6L), 1L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("crear: estudiante inactivo, grupo inactivo o anio cerrado -> 400")
    void crearConDatosInvalidos() {
        prepararDatosValidos();
        when(estudiantes.findById(11L)).thenReturn(Optional.of(
                estudiante(11L, 21L, EstadoEstudiante.INACTIVO)));
        assertThrows(ReglaNegocioException.class, () -> servicio.crear(
                new CrearMatriculaPeticion(11L, 6L), 1L, "127.0.0.1"));

        when(grupos.findById(7L)).thenReturn(Optional.of(grupo(7L, 5L, "10-B", EstadoGrupo.INACTIVO)));
        assertThrows(ReglaNegocioException.class, () -> servicio.crear(
                new CrearMatriculaPeticion(10L, 7L), 1L, "127.0.0.1"));

        when(anios.findById(5L)).thenReturn(Optional.of(anio(5L, 2025, EstadoAnio.CERRADO)));
        assertThrows(ReglaNegocioException.class, () -> servicio.crear(
                new CrearMatriculaPeticion(10L, 6L), 1L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("crear: estudiante o grupo inexistente -> 404")
    void crearConInexistentes() {
        when(estudiantes.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.crear(
                new CrearMatriculaPeticion(99L, 6L), 1L, "127.0.0.1"));

        when(grupos.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.crear(
                new CrearMatriculaPeticion(10L, 99L), 1L, "127.0.0.1"));
    }

    @Test
    @DisplayName("retirar: ACTIVA -> RETIRADA, auditado; no ACTIVA -> 400 (sin nueva escritura)")
    void retirarMatricula() {
        when(repositorio.findById(100L)).thenReturn(Optional.of(
                matricula(100L, 10L, 6L, 5L, EstadoMatricula.ACTIVA)));
        when(anios.findById(5L)).thenReturn(Optional.of(anio(5L, 2026, EstadoAnio.ACTIVO)));
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MatriculaRespuesta respuesta = servicio.retirar(100L, 1L, "127.0.0.1");

        assertEquals("RETIRADA", respuesta.estado());
        verify(auditoria).registrar(eq("DESACTIVAR"), eq("MATRICULAS"), anyString(), eq(1L),
                any(), eq("127.0.0.1"));
        verify(repositorio).save(any());

        // La matricula ya retirada/finalizada se rechaza y NO vuelve a guardar.
        when(repositorio.findById(101L)).thenReturn(Optional.of(
                matricula(101L, 10L, 6L, 5L, EstadoMatricula.FINALIZADA)));
        assertThrows(ReglaNegocioException.class, () -> servicio.retirar(101L, 1L, "127.0.0.1"));
        verify(repositorio, Mockito.times(1)).save(any());
    }
}
