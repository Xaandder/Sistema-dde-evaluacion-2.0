package com.semillero.evaluacion.anios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.semillero.evaluacion.anios.dto.ActualizarAnioPeticion;
import com.semillero.evaluacion.anios.dto.AnioRespuesta;
import com.semillero.evaluacion.anios.dto.CrearAnioPeticion;
import com.semillero.evaluacion.auditoria.AuditoriaServicio;
import com.semillero.evaluacion.exception.RecursoNoEncontradoException;
import com.semillero.evaluacion.exception.ReglaNegocioException;
import com.semillero.evaluacion.periodos.EstadoPeriodo;
import com.semillero.evaluacion.periodos.PeriodoRepositorio;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Pruebas unitarias de las reglas de negocio de anios lectivos, sin base de
 * datos (repositorio y auditoria simulados).
 */
class AnioServicioTests {

    private AnioRepositorio repositorio;
    private PeriodoRepositorio periodos;
    private AuditoriaServicio auditoria;
    private AnioServicio servicio;

    @BeforeEach
    void preparar() {
        repositorio = Mockito.mock(AnioRepositorio.class);
        periodos = Mockito.mock(PeriodoRepositorio.class);
        auditoria = Mockito.mock(AuditoriaServicio.class);
        // fail-open: el servicio captura cualquier error de auditoria
        doAnswer(invocacion -> null).when(auditoria)
                .registrar(anyString(), anyString(), anyString(), any(), any(), anyString());
        servicio = new AnioServicio(repositorio, periodos, auditoria);
    }

    private AnioAcademico anio(Long id, Integer numero, EstadoAnio estado) {
        AnioAcademico a = new AnioAcademico();
        a.setAnio(numero);
        a.setEstado(estado);
        return a;
    }

    @Test
    @DisplayName("crear: un anio nuevo nace ACTIVO y se audita como CREAR")
    void crearAnioNuevo() {
        when(repositorio.existsByAnio(2026)).thenReturn(false);
        when(repositorio.save(any())).thenAnswer(inv -> {
            AnioAcademico a = inv.getArgument(0);
            return a;
        });

        AnioRespuesta respuesta = servicio.crear(new CrearAnioPeticion(2026), 1L, "127.0.0.1");

        assertEquals(2026, respuesta.anio());
        assertEquals("ACTIVO", respuesta.estado());
        verify(auditoria).registrar(eq("CREAR"), eq("ANIOS_ACADEMICOS"), anyString(),
                eq(1L), eq(Map.of("anio", 2026)), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("crear: anio duplicado rechazado con regla de negocio")
    void crearAnioDuplicado() {
        when(repositorio.existsByAnio(2026)).thenReturn(true);

        assertThrows(ReglaNegocioException.class,
                () -> servicio.crear(new CrearAnioPeticion(2026), 1L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("actualizar: cambio de numero en anio ACTIVO se audita con antes y despues")
    void actualizarAnioActivo() {
        AnioAcademico existente = anio(5L, 2026, EstadoAnio.ACTIVO);
        when(repositorio.findById(5L)).thenReturn(Optional.of(existente));
        when(repositorio.existsByAnio(2027)).thenReturn(false);
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnioRespuesta respuesta =
                servicio.actualizar(5L, new ActualizarAnioPeticion(2027), 1L, "127.0.0.1");

        assertEquals(2027, respuesta.anio());
        verify(auditoria).registrar(eq("ACTUALIZAR"), eq("ANIOS_ACADEMICOS"), anyString(),
                eq(1L), any(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("actualizar: un anio CERRADO no admite cambios")
    void actualizarAnioCerrado() {
        when(repositorio.findById(5L))
                .thenReturn(Optional.of(anio(5L, 2025, EstadoAnio.CERRADO)));

        assertThrows(ReglaNegocioException.class,
                () -> servicio.actualizar(5L, new ActualizarAnioPeticion(2027), 1L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("actualizar: el numero nuevo no puede chocar con otro anio existente")
    void actualizarAnioDuplicado() {
        when(repositorio.findById(5L)).thenReturn(Optional.of(anio(5L, 2026, EstadoAnio.ACTIVO)));
        when(repositorio.existsByAnio(2027)).thenReturn(true);

        assertThrows(ReglaNegocioException.class,
                () -> servicio.actualizar(5L, new ActualizarAnioPeticion(2027), 1L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("activar: un anio CERRADO vuelve a ACTIVO y se audita como ABRIR")
    void activarAnioCerrado() {
        when(repositorio.findById(5L)).thenReturn(Optional.of(anio(5L, 2025, EstadoAnio.CERRADO)));
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnioRespuesta respuesta = servicio.activar(5L, 1L, "127.0.0.1");

        assertEquals("ACTIVO", respuesta.estado());
        verify(auditoria).registrar(eq("ABRIR"), eq("ANIOS_ACADEMICOS"), anyString(),
                eq(1L), any(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("activar: un anio ya ACTIVO se rechaza")
    void activarAnioYaActivo() {
        when(repositorio.findById(5L)).thenReturn(Optional.of(anio(5L, 2026, EstadoAnio.ACTIVO)));

        assertThrows(ReglaNegocioException.class, () -> servicio.activar(5L, 1L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("cerrar: un anio ACTIVO queda CERRADO y se audita como CERRAR")
    void cerrarAnioActivo() {
        when(repositorio.findById(5L)).thenReturn(Optional.of(anio(5L, 2026, EstadoAnio.ACTIVO)));
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnioRespuesta respuesta = servicio.cerrar(5L, 1L, "127.0.0.1");

        assertEquals("CERRADO", respuesta.estado());
        verify(auditoria).registrar(eq("CERRAR"), eq("ANIOS_ACADEMICOS"), anyString(),
                eq(1L), any(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("cerrar: un anio ya CERRADO se rechaza")
    void cerrarAnioYaCerrado() {
        when(repositorio.findById(5L)).thenReturn(Optional.of(anio(5L, 2025, EstadoAnio.CERRADO)));

        assertThrows(ReglaNegocioException.class, () -> servicio.cerrar(5L, 1L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("buscarPorId: id inexistente lanza 404")
    void buscarInexistente() {
        when(repositorio.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> servicio.buscarPorId(99L));
    }

    @Test
    @DisplayName("cerrar: no se cierra un anio con periodos ABIERTO")
    void cerrarAnioConPeriodosAbiertos() {
        when(repositorio.findById(5L)).thenReturn(Optional.of(anio(5L, 2026, EstadoAnio.ACTIVO)));
        when(periodos.existsByAnioIdAndEstado(5L, EstadoPeriodo.ABIERTO)).thenReturn(true);

        assertThrows(ReglaNegocioException.class, () -> servicio.cerrar(5L, 1L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }
}
