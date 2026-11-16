package com.semillero.evaluacion.acudientes;

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
import com.semillero.evaluacion.acudientes.dto.ActualizarAcudientePeticion;
import com.semillero.evaluacion.acudientes.dto.AcudienteRespuesta;
import com.semillero.evaluacion.acudientes.dto.CrearAcudientePeticion;
import com.semillero.evaluacion.usuarios.Rol;
import com.semillero.evaluacion.usuarios.Usuario;
import com.semillero.evaluacion.usuarios.UsuarioRepositorio;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/** Pruebas unitarias de las reglas de negocio de acudientes, sin Oracle. */
class AcudienteServicioTests {

    private AcudienteRepositorio repositorio;
    private UsuarioRepositorio usuarios;
    private AuditoriaServicio auditoria;
    private AcudienteServicio servicio;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void preparar() {
        repositorio = Mockito.mock(AcudienteRepositorio.class);
        usuarios = Mockito.mock(UsuarioRepositorio.class);
        auditoria = Mockito.mock(AuditoriaServicio.class);
        doAnswer(inv -> null).when(auditoria)
                .registrar(anyString(), anyString(), anyString(), any(), any(), anyString());
        servicio = new AcudienteServicio(repositorio, usuarios, encoder, auditoria);
    }

    private Usuario cuenta(Long id, String nombre, boolean activo) {
        Usuario u = new Usuario();
        ReflectionTestUtils.setField(u, "id", id);
        u.setUsuario(nombre);
        u.setContrasenaHash(encoder.encode("ClaveActual123"));
        u.setRol(Rol.ACUDIENTE);
        u.setActivo(activo);
        u.setCambioContrasenaPendiente(true);
        return u;
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

    private CrearAcudientePeticion peticion() {
        return new CrearAcudientePeticion("acud1", "Temporal1", "701001",
                "Acudiente", "Prueba", "MADRE", "acud1@colegio.edu", "3100000001");
    }

    @Test
    @DisplayName("crear: cuenta ACUDIENTE (trigger TR_ACUDIENTES_ROL) + perfil ACTIVO, auditado")
    void crearAcudiente() {
        when(usuarios.findByUsuario("acud1")).thenReturn(Optional.empty());
        when(repositorio.existsByDocumento("701001")).thenReturn(false);
        when(usuarios.save(any())).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 50L);
            return u;
        });
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(usuarios.findById(50L)).thenReturn(Optional.of(cuenta(50L, "acud1", true)));

        AcudienteRespuesta respuesta = servicio.crear(peticion(), 1L, "127.0.0.1");

        assertEquals("ACTIVO", respuesta.estado());
        assertEquals("acud1", respuesta.usuario());
        assertEquals(50L, respuesta.usuarioId());
        verify(auditoria).registrar(eq("CREAR"), eq("ACUDIENTES"), anyString(), eq(1L),
                any(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("crear: usuario repetido, documento repetido o contrasena debil -> 400")
    void crearDuplicadosODebil() {
        when(usuarios.findByUsuario("acud1")).thenReturn(Optional.of(cuenta(50L, "acud1", true)));
        assertThrows(ReglaNegocioException.class, () -> servicio.crear(peticion(), 1L, "ip"));

        when(usuarios.findByUsuario("acud1")).thenReturn(Optional.empty());
        when(repositorio.existsByDocumento("701001")).thenReturn(true);
        assertThrows(ReglaNegocioException.class, () -> servicio.crear(peticion(), 1L, "ip"));

        when(repositorio.existsByDocumento("701001")).thenReturn(false);
        assertThrows(ReglaNegocioException.class, () -> servicio.crear(
                new CrearAcudientePeticion("acud1", "12345678", "701001",
                        "A", "P", null, "a@x.com", null), 1L, "ip"));
        verify(usuarios, never()).save(any());
    }

    @Test
    @DisplayName("desactivar: acudiente + cuenta INACTIVO; desactivar dos veces -> 400")
    void desactivarAcudiente() {
        when(repositorio.findById(2L)).thenReturn(Optional.of(
                acudiente(2L, 50L, EstadoAcudiente.ACTIVO)));
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(usuarios.findById(50L)).thenReturn(Optional.of(cuenta(50L, "acud1", true)));

        assertEquals("INACTIVO", servicio.desactivar(2L, 1L, "ip").estado());
        verify(auditoria).registrar(eq("DESACTIVAR"), eq("ACUDIENTES"), anyString(),
                eq(1L), any(), eq("ip"));

        when(repositorio.findById(3L)).thenReturn(Optional.of(
                acudiente(3L, 51L, EstadoAcudiente.INACTIVO)));
        assertThrows(ReglaNegocioException.class, () -> servicio.desactivar(3L, 1L, "ip"));
    }

    @Test
    @DisplayName("activar: INACTIVO -> ACTIVO con cuenta restaurada; ya activo -> 400")
    void activarAcudiente() {
        when(repositorio.findById(2L)).thenReturn(Optional.of(
                acudiente(2L, 50L, EstadoAcudiente.INACTIVO)));
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(usuarios.findById(50L)).thenReturn(Optional.of(cuenta(50L, "acud1", false)));

        assertEquals("ACTIVO", servicio.activar(2L, 1L, "ip").estado());

        when(repositorio.findById(4L)).thenReturn(Optional.of(
                acudiente(4L, 52L, EstadoAcudiente.ACTIVO)));
        assertThrows(ReglaNegocioException.class, () -> servicio.activar(4L, 1L, "ip"));
    }

    @Test
    @DisplayName("actualizar: documento/nombres cambian con unicidad respetada")
    void actualizarAcudiente() {
        when(repositorio.findById(2L)).thenReturn(Optional.of(
                acudiente(2L, 50L, EstadoAcudiente.ACTIVO)));
        when(repositorio.existsByDocumento("701002")).thenReturn(false);
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(usuarios.findById(50L)).thenReturn(Optional.of(cuenta(50L, "acud1", true)));

        AcudienteRespuesta respuesta = servicio.actualizar(2L, new ActualizarAcudientePeticion(
                "701002", "Acudiente", "Prueba Ruiz", "PADRE", "acud1@colegio.edu", null),
                1L, "ip");

        assertEquals("701002", respuesta.documento());
        assertEquals("PADRE", respuesta.parentesco());
        verify(auditoria).registrar(eq("ACTUALIZAR"), eq("ACUDIENTES"), anyString(),
                eq(1L), any(), eq("ip"));
    }

    @Test
    @DisplayName("buscarPorId: acudiente ajeno sin admin -> 403; admin -> 200")
    void buscarPorIdConPermiso() {
        when(repositorio.findById(2L)).thenReturn(Optional.of(acudiente(2L, 50L,
                EstadoAcudiente.ACTIVO)));
        when(usuarios.findById(50L)).thenReturn(Optional.of(cuenta(50L, "acud1", true)));

        assertThrows(AccessDeniedException.class,
                () -> servicio.buscarPorId(2L, 99L, false));
        assertEquals("70100" + 2, servicio.buscarPorId(2L, 99L, true).documento());
    }

    @Test
    @DisplayName("perfilDeLaSesion: cuenta sin perfil de acudiente -> 404")
    void perfilSinAcudiente() {
        when(repositorio.findByUsuarioId(99L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.perfilDeLaSesion(99L));
    }

    @Test
    @DisplayName("listar: filtro de estado invalido -> 400")
    void listarEstadoInvalido() {
        assertThrows(ReglaNegocioException.class, () -> servicio.listar("JUBILADO"));
    }
}
