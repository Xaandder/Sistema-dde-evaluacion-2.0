package com.semillero.evaluacion.solicitudes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.semillero.evaluacion.solicitudes.dto.CrearSolicitudPeticion;
import com.semillero.evaluacion.solicitudes.dto.SolicitudRespuesta;
import com.semillero.evaluacion.vinculaciones.EstudianteAcudiente;
import com.semillero.evaluacion.vinculaciones.VinculacionRepositorio;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

/** Pruebas unitarias de las reglas de negocio de solicitudes, sin Oracle. */
class SolicitudServicioTests {

    private SolicitudRepositorio repositorio;
    private AcudienteRepositorio acudientes;
    private EstudianteRepositorio estudiantes;
    private VinculacionRepositorio vinculaciones;
    private AuditoriaServicio auditoria;
    private SolicitudServicio servicio;

    @BeforeEach
    void preparar() {
        repositorio = Mockito.mock(SolicitudRepositorio.class);
        acudientes = Mockito.mock(AcudienteRepositorio.class);
        estudiantes = Mockito.mock(EstudianteRepositorio.class);
        vinculaciones = Mockito.mock(VinculacionRepositorio.class);
        auditoria = Mockito.mock(AuditoriaServicio.class);
        doAnswer(inv -> null).when(auditoria)
                .registrar(anyString(), anyString(), anyString(), any(), any(), anyString());
        servicio = new SolicitudServicio(repositorio, acudientes, estudiantes,
                vinculaciones, auditoria);
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

    private SolicitudVinculacion solicitud(Long id, Long acudienteId, Long estudianteId,
            EstadoSolicitud estado) {
        SolicitudVinculacion s = new SolicitudVinculacion();
        ReflectionTestUtils.setField(s, "id", id);
        s.setAcudienteId(acudienteId);
        s.setEstudianteId(estudianteId);
        s.setEstado(estado);
        return s;
    }

    @Test
    @DisplayName("crear: solicitud PENDIENTE del acudiente de la sesion (perfil por usuarioId)")
    void crear() {
        when(acudientes.findByUsuarioId(50L)).thenReturn(Optional.of(
                acudiente(2L, 50L, EstadoAcudiente.ACTIVO)));
        when(estudiantes.findById(10L)).thenReturn(Optional.of(
                estudiante(10L, EstadoEstudiante.ACTIVO)));
        when(vinculaciones.existsByEstudianteIdAndAcudienteId(10L, 2L)).thenReturn(false);
        when(repositorio.existsByAcudienteIdAndEstudianteIdAndEstado(
                2L, 10L, EstadoSolicitud.PENDIENTE)).thenReturn(false);
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SolicitudRespuesta respuesta = servicio.crear(
                new CrearSolicitudPeticion(10L, "Soy la madre"), 50L, "ip");

        assertEquals("PENDIENTE", respuesta.estado());
        assertEquals(2L, respuesta.acudienteId());
        verify(auditoria).registrar(org.mockito.ArgumentMatchers.eq("CREAR"),
                org.mockito.ArgumentMatchers.eq("SOLICITUDES_VINCULACION"), anyString(),
                org.mockito.ArgumentMatchers.eq(50L), any(), anyString());
    }

    @Test
    @DisplayName("crear: ya vinculado o PENDIENTE duplicada -> 409; estudiante inexistente -> 404")
    void crearConflictos() {
        when(acudientes.findByUsuarioId(50L)).thenReturn(Optional.of(
                acudiente(2L, 50L, EstadoAcudiente.ACTIVO)));
        when(estudiantes.findById(10L)).thenReturn(Optional.of(
                estudiante(10L, EstadoEstudiante.ACTIVO)));

        when(vinculaciones.existsByEstudianteIdAndAcudienteId(10L, 2L)).thenReturn(true);
        assertThrows(ConflictoEstadoException.class,
                () -> servicio.crear(new CrearSolicitudPeticion(10L, null), 50L, "ip"));

        when(vinculaciones.existsByEstudianteIdAndAcudienteId(10L, 2L)).thenReturn(false);
        when(repositorio.existsByAcudienteIdAndEstudianteIdAndEstado(
                2L, 10L, EstadoSolicitud.PENDIENTE)).thenReturn(true);
        assertThrows(ConflictoEstadoException.class,
                () -> servicio.crear(new CrearSolicitudPeticion(10L, null), 50L, "ip"));

        when(repositorio.existsByAcudienteIdAndEstudianteIdAndEstado(
                2L, 10L, EstadoSolicitud.PENDIENTE)).thenReturn(false);
        when(estudiantes.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.crear(new CrearSolicitudPeticion(99L, null), 50L, "ip"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("aprobar: crea la vinculacion con trazas (revisadoPor/fechaResolucion)")
    void aprobar() {
        when(repositorio.findById(7L)).thenReturn(Optional.of(
                solicitud(7L, 2L, 10L, EstadoSolicitud.PENDIENTE)));
        when(estudiantes.findById(10L)).thenReturn(Optional.of(
                estudiante(10L, EstadoEstudiante.ACTIVO)));
        when(vinculaciones.existsByEstudianteIdAndAcudienteId(10L, 2L)).thenReturn(false);
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SolicitudRespuesta respuesta = servicio.aprobar(7L, "Autorizada", 1L, "ip");

        assertEquals("APROBADA", respuesta.estado());
        assertEquals(1L, respuesta.revisadoPor());
        verify(vinculaciones).save(any(EstudianteAcudiente.class));
        verify(auditoria).registrar(org.mockito.ArgumentMatchers.eq("ACTUALIZAR"),
                org.mockito.ArgumentMatchers.eq("SOLICITUDES_VINCULACION"), anyString(),
                org.mockito.ArgumentMatchers.eq(1L), any(), anyString());
    }

    @Test
    @DisplayName("aprobar: solicitud no PENDIENTE -> 409; vinculacion previa -> 409")
    void aprobarConflictos() {
        when(repositorio.findById(7L)).thenReturn(Optional.of(
                solicitud(7L, 2L, 10L, EstadoSolicitud.RECHAZADA)));
        assertThrows(ConflictoEstadoException.class,
                () -> servicio.aprobar(7L, null, 1L, "ip"));

        when(repositorio.findById(7L)).thenReturn(Optional.of(
                solicitud(7L, 2L, 10L, EstadoSolicitud.PENDIENTE)));
        when(estudiantes.findById(10L)).thenReturn(Optional.of(
                estudiante(10L, EstadoEstudiante.ACTIVO)));
        when(vinculaciones.existsByEstudianteIdAndAcudienteId(10L, 2L)).thenReturn(true);
        assertThrows(ConflictoEstadoException.class,
                () -> servicio.aprobar(7L, null, 1L, "ip"));
        verify(vinculaciones, never()).save(any());
    }

    @Test
    @DisplayName("rechazar: RECHAZADA con trazas; no pendiente -> 409")
    void rechazar() {
        when(repositorio.findById(7L)).thenReturn(Optional.of(
                solicitud(7L, 2L, 10L, EstadoSolicitud.PENDIENTE)));
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("RECHAZADA", servicio.rechazar(7L, "Documento dudoso", 1L, "ip").estado());

        when(repositorio.findById(8L)).thenReturn(Optional.of(
                solicitud(8L, 2L, 10L, EstadoSolicitud.APROBADA)));
        assertThrows(ConflictoEstadoException.class,
                () -> servicio.rechazar(8L, null, 1L, "ip"));
    }

    @Test
    @DisplayName("listar: ACUDIENTE forzado a las suyas (ajeno -> 403); ADMIN ve todas")
    void listar() {
        when(acudientes.findByUsuarioId(50L)).thenReturn(Optional.of(
                acudiente(2L, 50L, EstadoAcudiente.ACTIVO)));
        when(repositorio.findByAcudienteIdOrderByFechaCreacionAsc(2L))
                .thenReturn(List.of(solicitud(7L, 2L, 10L, EstadoSolicitud.PENDIENTE)));

        // Acudiente de la sesion: sin filtro se lista solo lo suyo.
        assertEquals(1, servicio.listar(null, null, null, 50L, false).size());
        // Pide un acudiente ajeno: 403.
        assertThrows(AccessDeniedException.class,
                () -> servicio.listar(null, 5L, null, 50L, false));
        // ADMIN ve todo.
        when(repositorio.findAllByOrderByFechaCreacionAsc())
                .thenReturn(List.of(solicitud(7L, 2L, 10L, EstadoSolicitud.PENDIENTE),
                        solicitud(8L, 3L, 11L, EstadoSolicitud.APROBADA)));
        assertEquals(2, servicio.listar(null, null, null, 1L, true).size());
    }
}
