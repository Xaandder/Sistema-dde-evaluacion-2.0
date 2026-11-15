package com.semillero.evaluacion.periodos;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.semillero.evaluacion.anios.AnioRepositorio;
import com.semillero.evaluacion.seguridad.JwtService;
import com.semillero.evaluacion.soporte.ContextoPruebas;
import com.semillero.evaluacion.usuarios.Rol;
import com.semillero.evaluacion.usuarios.Usuario;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Pruebas de endpoints de periodos academicos, SIN Oracle (perfil test: los
 * repositorios JPA se sustituyen con mocks). Verifica rutas, permisos por rol
 * y respuestas de error uniformes (ApiError).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PeriodoControllerTests extends ContextoPruebas {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private Usuario usuario(String nombre, Rol rol, boolean pendiente) {
        Usuario u = new Usuario();
        u.setUsuario(nombre);
        u.setContrasenaHash(encoder.encode("ClaveActual123"));
        u.setRol(rol);
        u.setActivo(true);
        u.setCambioContrasenaPendiente(pendiente);
        ReflectionTestUtils.setField(u, "id", 1L);
        return u;
    }

    private String token(Rol rol) {
        String nombre = rol.name().toLowerCase();
        Usuario cuenta = usuario(nombre, rol, false);
        when(usuarios.findByUsuario(nombre)).thenReturn(Optional.of(cuenta));
        return jwtService.generarToken(cuenta);
    }

    private Periodo periodo(Long id, Long anioId, Integer numero, String nombre,
            EstadoPeriodo estado) {
        Periodo p = new Periodo();
        ReflectionTestUtils.setField(p, "id", id);
        p.setAnioId(anioId);
        p.setNumero(numero);
        p.setNombre(nombre);
        p.setEstado(estado);
        return p;
    }

    @Test
    @DisplayName("POST /periodos como ADMINISTRADOR: 201 con el periodo PENDIENTE")
    void crearComoAdministrador() throws Exception {
        com.semillero.evaluacion.anios.AnioAcademico anioActivo =
                new com.semillero.evaluacion.anios.AnioAcademico();
        ReflectionTestUtils.setField(anioActivo, "id", 5L);
        anioActivo.setAnio(2026);
        anioActivo.setEstado(com.semillero.evaluacion.anios.EstadoAnio.ACTIVO);
        when(anios.findById(5L)).thenReturn(Optional.of(anioActivo));
        when(periodos.existsByAnioIdAndNumero(5L, 1)).thenReturn(false);
        when(periodos.save(any())).thenAnswer(inv -> {
            Periodo p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 10L);
            return p;
        });

        mockMvc.perform(post("/api/v1/periodos")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anioId\":5,\"numero\":1,\"nombre\":\"Primer periodo\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.anioId").value(5))
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    @DisplayName("POST /periodos como PROFESOR: 403 (solo ADMINISTRADOR)")
    void crearComoProfesorProhibido() throws Exception {
        mockMvc.perform(post("/api/v1/periodos")
                        .header("Authorization", "Bearer " + token(Rol.PROFESOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anioId\":5,\"numero\":1,\"nombre\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /periodos con anio inexistente: 404 ApiError")
    void crearConAnioInexistente() throws Exception {
        when(anios.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/periodos")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anioId\":99,\"numero\":1,\"nombre\":\"X\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.estado").value(404));
    }

    @Test
    @DisplayName("GET /periodos?anioId=5 filtra y ordena por numero (cualquier rol)")
    void listarPorAnio() throws Exception {
        com.semillero.evaluacion.anios.AnioAcademico anioActivo =
                new com.semillero.evaluacion.anios.AnioAcademico();
        anioActivo.setAnio(2026);
        anioActivo.setEstado(com.semillero.evaluacion.anios.EstadoAnio.ACTIVO);
        when(anios.findById(5L)).thenReturn(Optional.of(anioActivo));
        when(periodos.findByAnioIdOrderByNumeroAsc(5L)).thenReturn(List.of(
                periodo(10L, 5L, 1, "Primer periodo", EstadoPeriodo.ABIERTO),
                periodo(11L, 5L, 2, "Segundo periodo", EstadoPeriodo.PENDIENTE)));

        mockMvc.perform(get("/api/v1/periodos")
                        .queryParam("anioId", "5")
                        .header("Authorization", "Bearer " + token(Rol.ESTUDIANTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].numero").value(1))
                .andExpect(jsonPath("$[1].estado").value("PENDIENTE"));
    }

    @Test
    @DisplayName("GET /periodos/{id} inexistente: 404")
    void consultaInexistente() throws Exception {
        when(periodos.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/periodos/99")
                        .header("Authorization", "Bearer " + token(Rol.ACUDIENTE)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /periodos/{id} como ADMINISTRADOR: 200 con datos actualizados")
    void actualizarComoAdministrador() throws Exception {
        when(periodos.findById(10L)).thenReturn(Optional.of(
                periodo(10L, 5L, 1, "Primer periodo", EstadoPeriodo.PENDIENTE)));
        com.semillero.evaluacion.anios.AnioAcademico activo =
                new com.semillero.evaluacion.anios.AnioAcademico();
        activo.setAnio(2026);
        activo.setEstado(com.semillero.evaluacion.anios.EstadoAnio.ACTIVO);
        when(anios.findById(5L)).thenReturn(Optional.of(activo));
        when(periodos.existsByAnioIdAndNumero(5L, 2)).thenReturn(false);
        when(periodos.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/v1/periodos/10")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":2,\"nombre\":\"Primer corte\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value(2))
                .andExpect(jsonPath("$.nombre").value("Primer corte"));
    }

    @Test
    @DisplayName("POST /periodos/{id}/abrir y /cerrar como ADMINISTRADOR: ciclo de vida completo")
    void abrirYCerrar() throws Exception {
        when(periodos.findById(10L)).thenReturn(Optional.of(
                periodo(10L, 5L, 1, "Primer periodo", EstadoPeriodo.PENDIENTE)));
        com.semillero.evaluacion.anios.AnioAcademico activo =
                new com.semillero.evaluacion.anios.AnioAcademico();
        activo.setAnio(2026);
        activo.setEstado(com.semillero.evaluacion.anios.EstadoAnio.ACTIVO);
        when(anios.findById(5L)).thenReturn(Optional.of(activo));
        when(periodos.save(any())).thenAnswer(inv -> inv.getArgument(0));
        String t = token(Rol.ADMINISTRADOR);

        mockMvc.perform(post("/api/v1/periodos/10/abrir")
                        .header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ABIERTO"));

        when(periodos.findById(10L)).thenReturn(Optional.of(
                periodo(10L, 5L, 1, "Primer periodo", EstadoPeriodo.ABIERTO)));
        mockMvc.perform(post("/api/v1/periodos/10/cerrar")
                        .header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CERRADO"));
    }
}
