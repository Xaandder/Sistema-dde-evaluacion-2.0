package com.semillero.evaluacion.grupos;

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
import com.semillero.evaluacion.grupos.dto.ActualizarGrupoPeticion;
import com.semillero.evaluacion.grupos.dto.CrearGrupoPeticion;
import com.semillero.evaluacion.grupos.dto.GrupoRespuesta;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Pruebas unitarias de las reglas de negocio de grupos academicos, sin base
 * de datos (repositorios y auditoria simulados).
 */
class GrupoServicioTests {

    private GrupoRepositorio repositorio;
    private AnioRepositorio anios;
    private AuditoriaServicio auditoria;
    private GrupoServicio servicio;

    @BeforeEach
    void preparar() {
        repositorio = Mockito.mock(GrupoRepositorio.class);
        anios = Mockito.mock(AnioRepositorio.class);
        auditoria = Mockito.mock(AuditoriaServicio.class);
        doAnswer(invocacion -> null).when(auditoria)
                .registrar(anyString(), anyString(), anyString(), any(), any(), anyString());
        servicio = new GrupoServicio(repositorio, anios, auditoria);
    }

    private AnioAcademico anio(Long id, Integer numero, EstadoAnio estado) {
        AnioAcademico a = new AnioAcademico();
        ReflectionTestUtils.setField(a, "id", id);
        a.setAnio(numero);
        a.setEstado(estado);
        return a;
    }

    private Grupo grupo(Long id, Long anioId, String nombre, EstadoGrupo estado) {
        Grupo g = new Grupo();
        ReflectionTestUtils.setField(g, "id", id);
        g.setAnioId(anioId);
        g.setNombre(nombre);
        g.setEstado(estado);
        return g;
    }

    @Test
    @DisplayName("crear: grupo ACTIVO en anio ACTIVO, auditado como CREAR")
    void crearGrupo() {
        when(anios.findById(1L)).thenReturn(Optional.of(anio(1L, 2026, EstadoAnio.ACTIVO)));
        when(repositorio.existsByAnioIdAndNombre(1L, "10-A")).thenReturn(false);
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GrupoRespuesta respuesta = servicio.crear(
                new CrearGrupoPeticion(1L, "10-A", "Grupo de decimo"), 1L, "127.0.0.1");

        assertEquals("ACTIVO", respuesta.estado());
        assertEquals(1L, respuesta.anioId());
        assertEquals("10-A", respuesta.nombre());
        verify(auditoria).registrar(eq("CREAR"), eq("GRUPOS"), anyString(), eq(1L),
                any(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("crear: anio inexistente -> 404; anio cerrado -> 400")
    void crearConAnioInvalido() {
        when(anios.findById(99L)).thenReturn(Optional.empty());
        when(anios.findById(2L)).thenReturn(Optional.of(anio(2L, 2025, EstadoAnio.CERRADO)));

        assertThrows(RecursoNoEncontradoException.class, () -> servicio.crear(
                new CrearGrupoPeticion(99L, "10-A", null), 1L, "127.0.0.1"));
        assertThrows(ReglaNegocioException.class, () -> servicio.crear(
                new CrearGrupoPeticion(2L, "10-A", null), 1L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("crear: nombre duplicado dentro del anio -> 400")
    void crearNombreDuplicado() {
        when(anios.findById(1L)).thenReturn(Optional.of(anio(1L, 2026, EstadoAnio.ACTIVO)));
        when(repositorio.existsByAnioIdAndNombre(1L, "10-A")).thenReturn(true);

        assertThrows(ReglaNegocioException.class, () -> servicio.crear(
                new CrearGrupoPeticion(1L, "10-A", null), 1L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("actualizar: nombre unico dentro del anio; anio cerrado bloquea")
    void actualizarGrupo() {
        when(repositorio.findById(6L)).thenReturn(Optional.of(grupo(6L, 1L, "10-A",
                EstadoGrupo.ACTIVO)));
        when(anios.findById(1L)).thenReturn(Optional.of(anio(1L, 2026, EstadoAnio.ACTIVO)));
        when(repositorio.existsByAnioIdAndNombre(1L, "10-B")).thenReturn(false);
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GrupoRespuesta respuesta = servicio.actualizar(6L,
                new ActualizarGrupoPeticion("10-B", null), 1L, "127.0.0.1");

        assertEquals("10-B", respuesta.nombre());
        verify(auditoria).registrar(eq("ACTUALIZAR"), eq("GRUPOS"), anyString(), eq(1L),
                any(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("actualizar: grupo de un anio cerrado no admite cambios")
    void actualizarGrupoDeAnioCerrado() {
        when(repositorio.findById(6L)).thenReturn(Optional.of(grupo(6L, 2L, "10-A",
                EstadoGrupo.ACTIVO)));
        when(anios.findById(2L)).thenReturn(Optional.of(anio(2L, 2025, EstadoAnio.CERRADO)));

        assertThrows(ReglaNegocioException.class, () -> servicio.actualizar(6L,
                new ActualizarGrupoPeticion("10-B", null), 1L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("desactivar y activar: borrado logico con reactivacion en anio ACTIVO")
    void desactivarYActivar() {
        when(anios.findById(1L)).thenReturn(Optional.of(anio(1L, 2026, EstadoAnio.ACTIVO)));
        when(repositorio.findById(6L)).thenReturn(Optional.of(grupo(6L, 1L, "10-A",
                EstadoGrupo.ACTIVO)));
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("INACTIVO", servicio.desactivar(6L, 1L, "127.0.0.1").estado());
        verify(auditoria).registrar(eq("DESACTIVAR"), eq("GRUPOS"), anyString(), eq(1L),
                any(), eq("127.0.0.1"));

        when(repositorio.findById(6L)).thenReturn(Optional.of(grupo(6L, 1L, "10-A",
                EstadoGrupo.INACTIVO)));
        assertEquals("ACTIVO", servicio.activar(6L, 1L, "127.0.0.1").estado());
        verify(auditoria).registrar(eq("ACTIVAR"), eq("GRUPOS"), anyString(), eq(1L),
                any(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("desactivar: grupo ya inactivo -> 400")
    void desactivarYaInactivo() {
        when(repositorio.findById(6L)).thenReturn(Optional.of(grupo(6L, 1L, "10-A",
                EstadoGrupo.INACTIVO)));

        assertThrows(ReglaNegocioException.class, () -> servicio.desactivar(6L, 1L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("buscarPorId: id inexistente lanza 404")
    void buscarInexistente() {
        when(repositorio.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> servicio.buscarPorId(99L));
    }
}
