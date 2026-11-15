package com.semillero.evaluacion.profesores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.semillero.evaluacion.auditoria.AuditoriaServicio;
import com.semillero.evaluacion.exception.RecursoNoEncontradoException;
import com.semillero.evaluacion.exception.ReglaNegocioException;
import com.semillero.evaluacion.profesores.dto.ActualizarProfesorPeticion;
import com.semillero.evaluacion.profesores.dto.CrearProfesorPeticion;
import com.semillero.evaluacion.profesores.dto.ProfesorRespuesta;
import com.semillero.evaluacion.usuarios.Rol;
import com.semillero.evaluacion.usuarios.Usuario;
import com.semillero.evaluacion.usuarios.UsuarioRepositorio;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Pruebas unitarias de las reglas de negocio de profesores, sin base de
 * datos (repositorios y auditoria simulados).
 */
class ProfesorServicioTests {

    private ProfesorRepositorio repositorio;
    private UsuarioRepositorio usuarios;
    private AuditoriaServicio auditoria;
    private ProfesorServicio servicio;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void preparar() {
        repositorio = Mockito.mock(ProfesorRepositorio.class);
        usuarios = Mockito.mock(UsuarioRepositorio.class);
        auditoria = Mockito.mock(AuditoriaServicio.class);
        doAnswer(invocacion -> null).when(auditoria)
                .registrar(anyString(), anyString(), anyString(), any(), any(), anyString());
        servicio = new ProfesorServicio(repositorio, usuarios, encoder, auditoria);
    }

    private Usuario cuenta(Long id, String nombre, boolean activo) {
        Usuario u = new Usuario();
        ReflectionTestUtils.setField(u, "id", id);
        u.setUsuario(nombre);
        u.setContrasenaHash(encoder.encode("ClaveActual123"));
        u.setRol(Rol.PROFESOR);
        u.setActivo(activo);
        u.setCambioContrasenaPendiente(true);
        return u;
    }

    private Profesor profesor(Long id, Long usuarioId, EstadoProfesor estado) {
        Profesor p = new Profesor();
        ReflectionTestUtils.setField(p, "id", id);
        p.setUsuarioId(usuarioId);
        p.setDocumento("80100" + id);
        p.setNombres("Docente");
        p.setApellidos("Prueba");
        p.setEstado(estado);
        return p;
    }

    @Test
    @DisplayName("crear: cuenta PROFESOR con cambio obligatorio + perfil ACTIVO, auditado")
    void crearProfesor() {
        when(usuarios.findByUsuario("profe1")).thenReturn(Optional.empty());
        when(repositorio.existsByDocumento("801001")).thenReturn(false);
        when(usuarios.save(any())).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 30L);
            return u;
        });
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(usuarios.findById(30L)).thenReturn(Optional.of(cuenta(30L, "profe1", true)));

        ProfesorRespuesta respuesta = servicio.crear(new CrearProfesorPeticion(
                "profe1", "Temporal1", "801001", "Docente", "Prueba",
                "Matematicas", "profe@colegio.edu", null, null), 1L, "127.0.0.1");

        assertEquals("ACTIVO", respuesta.estado());
        assertEquals("profe1", respuesta.usuario());
        assertEquals(30L, respuesta.usuarioId());
        verify(auditoria).registrar(eq("CREAR"), eq("PROFESORES"), anyString(), eq(1L),
                any(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("crear: usuario de acceso repetido -> 400; documento repetido -> 400")
    void crearDuplicados() {
        when(usuarios.findByUsuario("profe1")).thenReturn(Optional.of(cuenta(30L, "profe1", true)));
        assertThrows(ReglaNegocioException.class, () -> servicio.crear(new CrearProfesorPeticion(
                "profe1", "Temporal1", "801001", "D", "P", null, null, null, null),
                1L, "127.0.0.1"));

        when(usuarios.findByUsuario("nuevo1")).thenReturn(Optional.empty());
        when(repositorio.existsByDocumento("801001")).thenReturn(true);
        assertThrows(ReglaNegocioException.class, () -> servicio.crear(new CrearProfesorPeticion(
                "nuevo1", "Temporal1", "801001", "D", "P", null, null, null, null),
                1L, "127.0.0.1"));
        verify(usuarios, never()).save(any());
    }

    @Test
    @DisplayName("crear: contrasena temporal sin letras o menor a 8 -> 400 (politica del API)")
    void crearContrasenaDebil() {
        when(usuarios.findByUsuario("nuevo1")).thenReturn(Optional.empty());

        assertThrows(ReglaNegocioException.class, () -> servicio.crear(new CrearProfesorPeticion(
                "nuevo1", "12345678", "801001", "D", "P", null, null, null, null),
                1L, "127.0.0.1"));
        assertThrows(ReglaNegocioException.class, () -> servicio.crear(new CrearProfesorPeticion(
                "nuevo1", "corta1", "801001", "D", "P", null, null, null, null),
                1L, "127.0.0.1"));
        verify(usuarios, never()).save(any());
    }

    @Test
    @DisplayName("actualizar: documento y nombres cambian con unicidad respetada")
    void actualizarProfesor() {
        when(repositorio.findById(4L)).thenReturn(Optional.of(
                profesor(4L, 30L, EstadoProfesor.ACTIVO)));
        when(repositorio.existsByDocumento("801002")).thenReturn(false);
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(usuarios.findById(30L)).thenReturn(Optional.of(cuenta(30L, "profe1", true)));

        ProfesorRespuesta respuesta = servicio.actualizar(4L, new ActualizarProfesorPeticion(
                "801002", "Docente", "Prueba Ruiz", null, null, null), 1L, "127.0.0.1");

        assertEquals("801002", respuesta.documento());
        assertEquals("Prueba Ruiz", respuesta.apellidos());
        verify(auditoria).registrar(eq("ACTUALIZAR"), eq("PROFESORES"), anyString(), eq(1L),
                any(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("actualizar: documento nuevo repetido -> 400")
    void actualizarDocumentoDuplicado() {
        when(repositorio.findById(4L)).thenReturn(Optional.of(
                profesor(4L, 30L, EstadoProfesor.ACTIVO)));
        when(repositorio.existsByDocumento("801002")).thenReturn(true);

        assertThrows(ReglaNegocioException.class, () -> servicio.actualizar(4L,
                new ActualizarProfesorPeticion("801002", "D", "P", null, null, null),
                1L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("desactivar: profesor + cuenta ACTIVO=0; desactivar dos veces -> 400")
    void desactivarProfesor() {
        when(repositorio.findById(4L)).thenReturn(Optional.of(
                profesor(4L, 30L, EstadoProfesor.ACTIVO)));
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(usuarios.findById(30L)).thenReturn(Optional.of(cuenta(30L, "profe1", true)));

        ProfesorRespuesta respuesta = servicio.desactivar(4L, 1L, "127.0.0.1");

        assertEquals("INACTIVO", respuesta.estado());
        verify(auditoria).registrar(eq("DESACTIVAR"), eq("PROFESORES"), anyString(), eq(1L),
                any(), eq("127.0.0.1"));

        when(repositorio.findById(5L)).thenReturn(Optional.of(
                profesor(5L, 31L, EstadoProfesor.INACTIVO)));
        assertThrows(ReglaNegocioException.class, () -> servicio.desactivar(5L, 1L, "127.0.0.1"));
    }

    @Test
    @DisplayName("activar: INACTIVO -> ACTIVO; ya activo -> 400")
    void activarProfesor() {
        when(repositorio.findById(4L)).thenReturn(Optional.of(
                profesor(4L, 30L, EstadoProfesor.INACTIVO)));
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(usuarios.findById(30L)).thenReturn(Optional.of(cuenta(30L, "profe1", false)));

        assertEquals("ACTIVO", servicio.activar(4L, 1L, "127.0.0.1").estado());
        verify(auditoria).registrar(eq("ACTIVAR"), eq("PROFESORES"), anyString(), eq(1L),
                any(), eq("127.0.0.1"));

        when(repositorio.findById(6L)).thenReturn(Optional.of(
                profesor(6L, 32L, EstadoProfesor.ACTIVO)));
        assertThrows(ReglaNegocioException.class, () -> servicio.activar(6L, 1L, "127.0.0.1"));
    }

    @Test
    @DisplayName("listar: filtro de estado invalido -> 400; id inexistente -> 404")
    void validacionesDeConsulta() {
        assertThrows(ReglaNegocioException.class, () -> servicio.listar("JUBILADO"));
        when(repositorio.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.buscarPorId(99L));
    }
}
