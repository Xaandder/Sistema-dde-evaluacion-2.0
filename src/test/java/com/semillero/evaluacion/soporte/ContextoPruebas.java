package com.semillero.evaluacion.soporte;

import com.semillero.evaluacion.anios.AnioRepositorio;
import com.semillero.evaluacion.asignaciones.AsignacionRepositorio;
import com.semillero.evaluacion.auditoria.AuditoriaRepositorio;
import com.semillero.evaluacion.estudiantes.EstudianteRepositorio;
import com.semillero.evaluacion.grupos.GrupoRepositorio;
import com.semillero.evaluacion.materias.MateriaRepositorio;
import com.semillero.evaluacion.matriculas.MatriculaRepositorio;
import com.semillero.evaluacion.periodos.PeriodoRepositorio;
import com.semillero.evaluacion.profesores.ProfesorRepositorio;
import com.semillero.evaluacion.usuarios.UsuarioRepositorio;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Base para pruebas {@code @SpringBootTest} del perfil "test" (sin Oracle).
 *
 * <p>El perfil excluye la autoconfiguracion de JPA, asi que TODO test que
 * levanta el contexto completo necesita un sustituto por cada repositorio
 * (porque los controladores/servicios del sistema piden esos beans).
 * Centralizar los {@code @MockitoBean} aqui evita repetir la lista en cada
 * clase de prueba; las clases hijas heredan estos campos cuando se agregan
 * modulos nuevos.</p>
 */
public abstract class ContextoPruebas {

    @MockitoBean
    protected UsuarioRepositorio usuarios;

    @MockitoBean
    protected AuditoriaRepositorio auditoria;

    @MockitoBean
    protected AnioRepositorio anios;

    @MockitoBean
    protected PeriodoRepositorio periodos;

    @MockitoBean
    protected MateriaRepositorio materias;

    @MockitoBean
    protected GrupoRepositorio grupos;

    @MockitoBean
    protected EstudianteRepositorio estudiantes;

    @MockitoBean
    protected MatriculaRepositorio matriculas;

    @MockitoBean
    protected ProfesorRepositorio profesores;

    @MockitoBean
    protected AsignacionRepositorio asignaciones;
}
