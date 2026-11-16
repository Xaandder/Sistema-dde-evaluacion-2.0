package com.semillero.evaluacion.vinculaciones;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.semillero.evaluacion.acudientes.Acudiente;
import com.semillero.evaluacion.acudientes.AcudienteRepositorio;
import com.semillero.evaluacion.acudientes.EstadoAcudiente;
import com.semillero.evaluacion.auditoria.AuditoriaServicio;
import com.semillero.evaluacion.estudiantes.EstadoEstudiante;
import com.semillero.evaluacion.estudiantes.Estudiante;
import com.semillero.evaluacion.estudiantes.EstudianteRepositorio;
import com.semillero.evaluacion.exception.ConflictoEstadoException;
import com.semillero.evaluacion.exception.RecursoNoEncontradoException;
import com.semillero.evaluacion.vinculaciones.dto.CrearVinculacionPeticion;
import com.semillero.evaluacion.vinculaciones.dto.EstudianteVinculadoRespuesta;
import com.semillero.evaluacion.vinculaciones.dto.VinculacionRespuesta;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

/** Pruebas unitarias de las reglas de negocio de vinculaciones, sin Oracle. */
class VinculacionServicioTests {

    private VinculacionRepositorio repositorio;
    private AcudienteRepositorio acudientes;
    private EstudianteRepositorio estudiantes;
    private AuditoriaServicio auditoria;
    private VinculacionServicio servicio;

    @BeforeEach
    void preparar() {
        repositorio = Mockito.mock(VinculacionRepositorio.class);
        acudientes = Mockito.mock(AcudienteRepositorio.class);
        estudiantes = Mockito.mock(EstudianteRepositorio.class);
        auditoria = Mockito.mock(AuditoriaServicio.class);
        doAnswer(inv -> null).when(auditoria)
                .registrar(anyString(), anyString(), anyString(), any(), any(), anyString());
        servicio = new VinculacionServicio(repositorio, acudientes, estudiantes, auditoria);
    }

    private Acudiente acudiente(Long id, Long usuarioId, EstadoAcudiente estado) {
        Acudiente a = new Acudiente();
        ReflectionTestUtils.setField(a, "id", id);
        a.setUsuarioId(usuarioId);
        a.setDocumento("70100" + id);
        a.setNombres("Acudiente");
        a.setApellidos("Prueba");
        a.setCorreo("acudiente" + id + "@colegio.edu");
        a.setEstado(estado);
        return a;
    }

    private Estudiante estudiante(Long id, EstadoEstudiante estado) {
        Estudiante e = new Estudiante();
        ReflectionTestUtils.setField(e, "id", id);
        e.setUsuarioId(90L + id);
        e.setDocumento("10100" + id);
        e.setNombres("Estudiante");
        e.setApellidos("Prueba");
        e.setEstado(estado);
        return e;
    }

    private EstudianteAcudiente vinculacion(Long estudianteId, Long acudienteId) {
        EstudianteAcudiente v = new EstudianteAcudiente();
        v.setEstudianteId(estudianteId);
        v.setAcudienteId(acudienteId);
        return v;
    }

    @Test
    @DisplayName("vincular: crea la junction con participantes ACTIVOS y audita")
    void vincular() {
        when(acudientes.findById(2L)).thenReturn(Optional.of(
                acudiente(2L, 50L, EstadoAcudiente.ACTIVO)));
        when(estudiantes.findById(10L)).thenReturn(Optional.of(
                estudiante(10L, EstadoEstudiante.ACTIVO)));
        when(repositorio.existsByEstudianteIdAndAcudienteId(10L, 2L)).thenReturn(false);
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        VinculacionRespuesta respuesta = servicio.vincular(
                new CrearVinculacionPeticion(10L, 2L), 1L, "ip");

        assertEquals(10L, respuesta.estudianteId());
        assertEquals(2L, respuesta.acudienteId());
        verify(auditoria).registrar(org.mockito.ArgumentMatchers.eq("CREAR"),
                org.mockito.ArgumentMatchers.eq("ESTUDIANTE_ACUDIENTE"), anyString(),
                org.mockito.ArgumentMatchers.eq(1L), any(), anyString());
    }

    @Test
    @DisplayName("vincular: participante INACTIVO -> 409; duplicada -> 409; inexistente -> 404")
    void vincularConflictos() {
        when(acudientes.findById(2L)).thenReturn(Optional.of(
                acudiente(2L, 50L, EstadoAcudiente.INACTIVO)));
        when(estudiantes.findById(10L)).thenReturn(Optional.of(
                estudiante(10L, EstadoEstudiante.ACTIVO)));
        assertThrows(ConflictoEstadoException.class,
                () -> servicio.vincular(new CrearVinculacionPeticion(10L, 2L), 1L, "ip"));

        when(acudientes.findById(2L)).thenReturn(Optional.of(
                acudiente(2L, 50L, EstadoAcudiente.ACTIVO)));
        when(estudiantes.findById(10L)).thenReturn(Optional.of(
                estudiante(10L, EstadoEstudiante.INACTIVO)));
        assertThrows(ConflictoEstadoException.class,
                () -> servicio.vincular(new CrearVinculacionPeticion(10L, 2L), 1L, "ip"));

        when(estudiantes.findById(10L)).thenReturn(Optional.of(
                estudiante(10L, EstadoEstudiante.ACTIVO)));
        when(repositorio.existsByEstudianteIdAndAcudienteId(10L, 2L)).thenReturn(true);
        assertThrows(ConflictoEstadoException.class,
                () -> servicio.vincular(new CrearVinculacionPeticion(10L, 2L), 1L, "ip"));

        when(repositorio.existsByEstudianteIdAndAcudienteId(10L, 2L)).thenReturn(false);
        when(acudientes.findById(9L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.vincular(new CrearVinculacionPeticion(10L, 9L), 1L, "ip"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("desvincular: borra la junction y audita; inexistente -> 404")
    void desvincular() {
        when(repositorio.findByEstudianteIdAndAcudienteId(10L, 2L))
                .thenReturn(Optional.of(vinculacion(10L, 2L)));

        servicio.desvincular(10L, 2L, 1L, "ip");
        verify(repositorio).delete(any(EstudianteAcudiente.class));
        verify(auditoria).registrar(org.mockito.ArgumentMatchers.eq("DESACTIVAR"),
                org.mockito.ArgumentMatchers.eq("ESTUDIANTE_ACUDIENTE"), anyString(),
                org.mockito.ArgumentMatchers.eq(1L), any(), anyString());

        when(repositorio.findByEstudianteIdAndAcudienteId(11L, 2L))
                .thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.desvincular(11L, 2L, 1L, "ip"));
    }

    @Test
    @DisplayName("estudiantesDeMiAcudiente: dueño ve sus estudiantes; ajeno -> 403")
    void estudiantesDeMiAcudiente() {
        when(acudientes.findById(2L)).thenReturn(Optional.of(
                acudiente(2L, 50L, EstadoAcudiente.ACTIVO)));
        when(repositorio.findByAcudienteIdOrderByFechaVinculacionAsc(2L))
                .thenReturn(List.of(vinculacion(10L, 2L), vinculacion(11L, 2L)));
        when(estudiantes.findAllById(List.of(10L, 11L)))
                .thenReturn(List.of(estudiante(10L, EstadoEstudiante.ACTIVO),
                        estudiante(11L, EstadoEstudiante.ACTIVO)));

        List<EstudianteVinculadoRespuesta> respuesta =
                servicio.estudiantesDeMiAcudiente(2L, 50L);
        assertEquals(2, respuesta.size());
        assertTrue(respuesta.stream().allMatch(e -> e.documento().startsWith("10100")));

        assertThrows(AccessDeniedException.class,
                () -> servicio.estudiantesDeMiAcudiente(2L, 99L));
    }

    @Test
    @DisplayName("listar: filtra por acudiente, por estudiante o lista todas")
    void listar() {
        when(repositorio.findByAcudienteIdOrderByFechaVinculacionAsc(2L))
                .thenReturn(List.of(vinculacion(10L, 2L)));
        when(repositorio.findByEstudianteIdOrderByFechaVinculacionAsc(10L))
                .thenReturn(List.of(vinculacion(10L, 2L)));
        when(repositorio.findAll())
                .thenReturn(List.of(vinculacion(10L, 2L), vinculacion(11L, 3L)));

        assertEquals(1, servicio.listar(2L, null).size());
        assertEquals(1, servicio.listar(null, 10L).size());
        assertEquals(2, servicio.listar(null, null).size());
    }
}
