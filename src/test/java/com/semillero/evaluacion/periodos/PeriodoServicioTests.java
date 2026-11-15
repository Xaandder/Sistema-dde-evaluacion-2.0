package com.semillero.evaluacion.periodos;

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
import com.semillero.evaluacion.periodos.dto.ActualizarPeriodoPeticion;
import com.semillero.evaluacion.periodos.dto.CrearPeriodoPeticion;
import com.semillero.evaluacion.periodos.dto.PeriodoRespuesta;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Pruebas unitarias de las reglas de negocio de periodos academicos, sin
 * base de datos (repositorios y auditoria simulados).
 */
class PeriodoServicioTests {

    private PeriodoRepositorio repositorio;
    private AnioRepositorio anios;
    private AuditoriaServicio auditoria;
    private PeriodoServicio servicio;

    @BeforeEach
    void preparar() {
        repositorio = Mockito.mock(PeriodoRepositorio.class);
        anios = Mockito.mock(AnioRepositorio.class);
        auditoria = Mockito.mock(AuditoriaServicio.class);
        doAnswer(invocacion -> null).when(auditoria)
                .registrar(anyString(), anyString(), anyString(), any(), any(), anyString());
        servicio = new PeriodoServicio(repositorio, anios, auditoria);
    }

    private AnioAcademico anio(Long id, Integer numero, EstadoAnio estado) {
        AnioAcademico a = new AnioAcademico();
        ReflectionTestUtils.setField(a, "id", id);
        a.setAnio(numero);
        a.setEstado(estado);
        return a;
    }

    private Periodo periodo(Long id, Long anioId, Integer numero, String nombre,
            EstadoPeriodo estado) {
        Periodo p = new Periodo();
        p.setAnioId(anioId);
        p.setNumero(numero);
        p.setNombre(nombre);
        p.setEstado(estado);
        return p;
    }

    @Test
    @DisplayName("crear: periodo nuevo PENDIENTE en un anio ACTIVO, auditado como CREAR")
    void crearPeriodo() {
        when(anios.findById(1L)).thenReturn(Optional.of(anio(1L, 2026, EstadoAnio.ACTIVO)));
        when(repositorio.existsByAnioIdAndNumero(1L, 1)).thenReturn(false);
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PeriodoRespuesta respuesta = servicio.crear(new CrearPeriodoPeticion(
                1L, 1, "Primer periodo", LocalDate.of(2026, 2, 2), LocalDate.of(2026, 6, 1)),
                1L, "127.0.0.1");

        assertEquals("PENDIENTE", respuesta.estado());
        assertEquals(1L, respuesta.anioId());
        assertEquals("Primer periodo", respuesta.nombre());
        verify(auditoria).registrar(eq("CREAR"), eq("PERIODOS"), anyString(),
                eq(1L), any(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("crear: anio inexistente -> 404; anio cerrado -> 400")
    void crearConAnioInvalido() {
        when(anios.findById(99L)).thenReturn(Optional.empty());
        when(anios.findById(2L)).thenReturn(Optional.of(anio(2L, 2025, EstadoAnio.CERRADO)));

        assertThrows(RecursoNoEncontradoException.class, () -> servicio.crear(
                new CrearPeriodoPeticion(99L, 1, "X", null, null), 1L, "127.0.0.1"));
        assertThrows(ReglaNegocioException.class, () -> servicio.crear(
                new CrearPeriodoPeticion(2L, 1, "X", null, null), 1L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("crear: numero duplicado dentro del anio -> 400")
    void crearNumeroDuplicado() {
        when(anios.findById(1L)).thenReturn(Optional.of(anio(1L, 2026, EstadoAnio.ACTIVO)));
        when(repositorio.existsByAnioIdAndNumero(1L, 1)).thenReturn(true);

        assertThrows(ReglaNegocioException.class, () -> servicio.crear(
                new CrearPeriodoPeticion(1L, 1, "Duplicado", null, null), 1L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("crear: FECHA_FIN anterior a FECHA_INICIO -> 400")
    void crearFechasInvalidas() {
        when(anios.findById(1L)).thenReturn(Optional.of(anio(1L, 2026, EstadoAnio.ACTIVO)));

        assertThrows(ReglaNegocioException.class, () -> servicio.crear(
                new CrearPeriodoPeticion(1L, 1, "X", LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 2, 2)), 1L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("abrir: PENDIENTE -> ABIERTO en anio ACTIVO, auditado como ABRIR")
    void abrirPeriodo() {
        when(repositorio.findById(10L)).thenReturn(Optional.of(
                periodo(10L, 1L, 1, "Primer periodo", EstadoPeriodo.PENDIENTE)));
        when(anios.findById(1L)).thenReturn(Optional.of(anio(1L, 2026, EstadoAnio.ACTIVO)));
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PeriodoRespuesta respuesta = servicio.abrir(10L, 1L, "127.0.0.1");

        assertEquals("ABIERTO", respuesta.estado());
        verify(auditoria).registrar(eq("ABRIR"), eq("PERIODOS"), anyString(), eq(1L), any(),
                eq("127.0.0.1"));
    }

    @Test
    @DisplayName("abrir: anio CERRADO impide abrir el periodo")
    void abrirEnAnioCerrado() {
        when(repositorio.findById(10L)).thenReturn(Optional.of(
                periodo(10L, 1L, 1, "Primer periodo", EstadoPeriodo.PENDIENTE)));
        when(anios.findById(1L)).thenReturn(Optional.of(anio(1L, 2025, EstadoAnio.CERRADO)));

        assertThrows(ReglaNegocioException.class, () -> servicio.abrir(10L, 1L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("abrir: periodo ya ABIERTO -> 400; CERRADO -> 400")
    void abrirEstadosInvalidos() {
        when(repositorio.findById(11L)).thenReturn(Optional.of(
                periodo(11L, 1L, 1, "X", EstadoPeriodo.ABIERTO)));
        when(repositorio.findById(12L)).thenReturn(Optional.of(
                periodo(12L, 1L, 2, "Y", EstadoPeriodo.CERRADO)));

        assertThrows(ReglaNegocioException.class, () -> servicio.abrir(11L, 1L, "127.0.0.1"));
        assertThrows(ReglaNegocioException.class, () -> servicio.abrir(12L, 1L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("cerrar: ABIERTO -> CERRADO, auditado como CERRAR")
    void cerrarPeriodo() {
        when(repositorio.findById(10L)).thenReturn(Optional.of(
                periodo(10L, 1L, 1, "Primer periodo", EstadoPeriodo.ABIERTO)));
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PeriodoRespuesta respuesta = servicio.cerrar(10L, 1L, "127.0.0.1");

        assertEquals("CERRADO", respuesta.estado());
        verify(auditoria).registrar(eq("CERRAR"), eq("PERIODOS"), anyString(), eq(1L), any(),
                eq("127.0.0.1"));
    }

    @Test
    @DisplayName("cerrar: PENDIENTE no se cierra (ciclo PENDIENTE->ABIERTO->CERRADO)")
    void cerrarPeriodoPendiente() {
        when(repositorio.findById(10L)).thenReturn(Optional.of(
                periodo(10L, 1L, 1, "X", EstadoPeriodo.PENDIENTE)));

        assertThrows(ReglaNegocioException.class, () -> servicio.cerrar(10L, 1L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("actualizar: periodo no cerrado cambia numero, nombre y fechas")
    void actualizarPeriodo() {
        when(repositorio.findById(10L)).thenReturn(Optional.of(
                periodo(10L, 1L, 1, "Primer periodo", EstadoPeriodo.ABIERTO)));
        when(anios.findById(1L)).thenReturn(Optional.of(anio(1L, 2026, EstadoAnio.ACTIVO)));
        when(repositorio.existsByAnioIdAndNumero(1L, 2)).thenReturn(false);
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PeriodoRespuesta respuesta = servicio.actualizar(10L,
                new ActualizarPeriodoPeticion(2, "Primer corte",
                        LocalDate.of(2026, 2, 2), LocalDate.of(2026, 6, 1)),
                1L, "127.0.0.1");

        assertEquals(2, respuesta.numero());
        assertEquals("Primer corte", respuesta.nombre());
        verify(auditoria).registrar(eq("ACTUALIZAR"), eq("PERIODOS"), anyString(), eq(1L), any(),
                eq("127.0.0.1"));
    }

    @Test
    @DisplayName("actualizar: periodo CERRADO inmutable")
    void actualizarPeriodoCerrado() {
        when(repositorio.findById(10L)).thenReturn(Optional.of(
                periodo(10L, 1L, 1, "X", EstadoPeriodo.CERRADO)));

        assertThrows(ReglaNegocioException.class, () -> servicio.actualizar(10L,
                new ActualizarPeriodoPeticion(2, "Y", null, null), 1L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("buscarPorId: id inexistente lanza 404")
    void buscarInexistente() {
        when(repositorio.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> servicio.buscarPorId(99L));
    }
}
