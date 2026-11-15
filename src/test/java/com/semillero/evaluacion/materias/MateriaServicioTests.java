package com.semillero.evaluacion.materias;

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
import com.semillero.evaluacion.materias.dto.ActualizarMateriaPeticion;
import com.semillero.evaluacion.materias.dto.CrearMateriaPeticion;
import com.semillero.evaluacion.materias.dto.MateriaRespuesta;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Pruebas unitarias de las reglas de negocio del catalogo de materias, sin
 * base de datos (repositorios y auditoria simulados).
 */
class MateriaServicioTests {

    private MateriaRepositorio repositorio;
    private AuditoriaServicio auditoria;
    private MateriaServicio servicio;

    @BeforeEach
    void preparar() {
        repositorio = Mockito.mock(MateriaRepositorio.class);
        auditoria = Mockito.mock(AuditoriaServicio.class);
        doAnswer(invocacion -> null).when(auditoria)
                .registrar(anyString(), anyString(), anyString(), any(), any(), anyString());
        servicio = new MateriaServicio(repositorio, auditoria);
    }

    private Materia materia(Long id, String codigo, String nombre, EstadoMateria estado) {
        Materia m = new Materia();
        ReflectionTestUtils.setField(m, "id", id);
        m.setCodigo(codigo);
        m.setNombre(nombre);
        m.setEstado(estado);
        return m;
    }

    @Test
    @DisplayName("crear: materia nueva ACTIVA, auditada como CREAR")
    void crearMateria() {
        when(repositorio.existsByCodigo("MAT-101")).thenReturn(false);
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MateriaRespuesta respuesta = servicio.crear(
                new CrearMateriaPeticion("MAT-101", "Matematicas", "Aritmetica y algebra"),
                1L, "127.0.0.1");

        assertEquals("ACTIVO", respuesta.estado());
        assertEquals("MAT-101", respuesta.codigo());
        verify(auditoria).registrar(eq("CREAR"), eq("MATERIAS"), anyString(), eq(1L),
                any(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("crear: codigo duplicado -> 400")
    void crearCodigoDuplicado() {
        when(repositorio.existsByCodigo("MAT-101")).thenReturn(true);

        assertThrows(ReglaNegocioException.class, () -> servicio.crear(
                new CrearMateriaPeticion("MAT-101", "Matematicas", null), 1L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("actualizar: codigo y nombre cambian; el codigo nuevo no puede repetirse")
    void actualizarMateria() {
        when(repositorio.findById(3L)).thenReturn(Optional.of(
                materia(3L, "MAT-101", "Matematicas", EstadoMateria.ACTIVO)));
        when(repositorio.existsByCodigo("MAT-1")).thenReturn(false);
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MateriaRespuesta respuesta = servicio.actualizar(3L,
                new ActualizarMateriaPeticion("MAT-1", "Matematicas basicas", null),
                1L, "127.0.0.1");

        assertEquals("MAT-1", respuesta.codigo());
        assertEquals("Matematicas basicas", respuesta.nombre());
        verify(auditoria).registrar(eq("ACTUALIZAR"), eq("MATERIAS"), anyString(), eq(1L),
                any(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("actualizar: el codigo nuevo choca con otra materia -> 400")
    void actualizarCodigoDuplicado() {
        when(repositorio.findById(3L)).thenReturn(Optional.of(
                materia(3L, "MAT-101", "Matematicas", EstadoMateria.ACTIVO)));
        when(repositorio.existsByCodigo("MAT-102")).thenReturn(true);

        assertThrows(ReglaNegocioException.class, () -> servicio.actualizar(3L,
                new ActualizarMateriaPeticion("MAT-102", "Matematicas", null), 1L, "127.0.0.1"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("desactivar: ACTIVO -> INACTIVO, auditado como DESACTIVAR; ya inactiva -> 400")
    void desactivarMateria() {
        when(repositorio.findById(3L)).thenReturn(Optional.of(
                materia(3L, "MAT-101", "Matematicas", EstadoMateria.ACTIVO)));
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("INACTIVO", servicio.desactivar(3L, 1L, "127.0.0.1").estado());
        verify(auditoria).registrar(eq("DESACTIVAR"), eq("MATERIAS"), anyString(), eq(1L),
                any(), eq("127.0.0.1"));

        when(repositorio.findById(4L)).thenReturn(Optional.of(
                materia(4L, "MAT-102", "Lengua", EstadoMateria.INACTIVO)));
        assertThrows(ReglaNegocioException.class, () -> servicio.desactivar(4L, 1L, "127.0.0.1"));
    }

    @Test
    @DisplayName("activar: INACTIVO -> ACTIVO, auditado como ACTIVAR; ya activa -> 400")
    void activarMateria() {
        when(repositorio.findById(3L)).thenReturn(Optional.of(
                materia(3L, "MAT-101", "Matematicas", EstadoMateria.INACTIVO)));
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("ACTIVO", servicio.activar(3L, 1L, "127.0.0.1").estado());
        verify(auditoria).registrar(eq("ACTIVAR"), eq("MATERIAS"), anyString(), eq(1L),
                any(), eq("127.0.0.1"));

        when(repositorio.findById(4L)).thenReturn(Optional.of(
                materia(4L, "MAT-102", "Lengua", EstadoMateria.ACTIVO)));
        assertThrows(ReglaNegocioException.class, () -> servicio.activar(4L, 1L, "127.0.0.1"));
    }

    @Test
    @DisplayName("listar: filtro de estado invalido -> 400; id inexistente -> 404")
    void validacionesDeConsulta() {
        assertThrows(ReglaNegocioException.class, () -> servicio.listar("BORRADOR"));
        when(repositorio.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.buscarPorId(99L));
    }
}
