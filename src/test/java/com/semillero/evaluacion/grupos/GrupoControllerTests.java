package com.semillero.evaluacion.grupos;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.semillero.evaluacion.anios.AnioAcademico;
import com.semillero.evaluacion.anios.EstadoAnio;
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
 * Pruebas de endpoints de grupos academicos, SIN Oracle (perfil test).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GrupoControllerTests extends ContextoPruebas {

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

    private AnioAcademico anioActivo(Long id) {
        AnioAcademico a = new AnioAcademico();
        ReflectionTestUtils.setField(a, "id", id);
        a.setAnio(2026);
        a.setEstado(EstadoAnio.ACTIVO);
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
    @DisplayName("POST /grupos como ADMINISTRADOR: 201 ACTIVO")
    void crearComoAdministrador() throws Exception {
        when(anios.findById(5L)).thenReturn(Optional.of(anioActivo(5L)));
        when(grupos.existsByAnioIdAndNombre(5L, "10-A")).thenReturn(false);
        when(grupos.save(any())).thenAnswer(inv -> {
            Grupo g = inv.getArgument(0);
            ReflectionTestUtils.setField(g, "id", 6L);
            return g;
        });

        mockMvc.perform(post("/api/v1/grupos")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anioId\":5,\"nombre\":\"10-A\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(6))
                .andExpect(jsonPath("$.anioId").value(5))
                .andExpect(jsonPath("$.nombre").value("10-A"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
    }

    @Test
    @DisplayName("POST /grupos como ACUDIENTE: 403")
    void crearComoAcudienteProhibido() throws Exception {
        mockMvc.perform(post("/api/v1/grupos")
                        .header("Authorization", "Bearer " + token(Rol.ACUDIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anioId\":5,\"nombre\":\"10-A\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /grupos con anio inexistente: 404")
    void crearConAnioInexistente() throws Exception {
        when(anios.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/grupos")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anioId\":99,\"nombre\":\"10-A\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /grupos?anioId=5 filtra por anio (cualquier rol autenticado)")
    void listarPorAnio() throws Exception {
        when(anios.findById(5L)).thenReturn(Optional.of(anioActivo(5L)));
        when(grupos.findByAnioIdOrderByNombreAsc(5L)).thenReturn(List.of(
                grupo(6L, 5L, "10-A", EstadoGrupo.ACTIVO),
                grupo(7L, 5L, "10-B", EstadoGrupo.ACTIVO)));

        mockMvc.perform(get("/api/v1/grupos")
                        .queryParam("anioId", "5")
                        .header("Authorization", "Bearer " + token(Rol.PROFESOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nombre").value("10-A"))
                .andExpect(jsonPath("$[1].nombre").value("10-B"));
    }

    @Test
    @DisplayName("PUT /grupos/{id} como ADMINISTRADOR: 200 con el nombre actualizado")
    void actualizarComoAdministrador() throws Exception {
        when(grupos.findById(6L)).thenReturn(Optional.of(grupo(6L, 5L, "10-A", EstadoGrupo.ACTIVO)));
        when(anios.findById(5L)).thenReturn(Optional.of(anioActivo(5L)));
        when(grupos.existsByAnioIdAndNombre(5L, "10-B")).thenReturn(false);
        when(grupos.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/v1/grupos/6")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"10-B\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("10-B"));
    }

    @Test
    @DisplayName("POST /grupos/{id}/desactivar y /activar: borrado logico completo")
    void desactivarYActivar() throws Exception {
        when(grupos.findById(6L)).thenReturn(Optional.of(grupo(6L, 5L, "10-A", EstadoGrupo.ACTIVO)));
        when(anios.findById(5L)).thenReturn(Optional.of(anioActivo(5L)));
        when(grupos.save(any())).thenAnswer(inv -> inv.getArgument(0));
        String t = token(Rol.ADMINISTRADOR);

        mockMvc.perform(post("/api/v1/grupos/6/desactivar")
                        .header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INACTIVO"));

        when(grupos.findById(6L)).thenReturn(Optional.of(grupo(6L, 5L, "10-A", EstadoGrupo.INACTIVO)));
        mockMvc.perform(post("/api/v1/grupos/6/activar")
                        .header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
    }
}
