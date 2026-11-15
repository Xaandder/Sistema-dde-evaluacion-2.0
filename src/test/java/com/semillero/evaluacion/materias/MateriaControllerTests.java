package com.semillero.evaluacion.materias;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Pruebas de endpoints del catalogo de materias, SIN Oracle (perfil test).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MateriaControllerTests extends ContextoPruebas {

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

    private Materia materia(Long id, String codigo, String nombre, EstadoMateria estado) {
        Materia m = new Materia();
        ReflectionTestUtils.setField(m, "id", id);
        m.setCodigo(codigo);
        m.setNombre(nombre);
        m.setEstado(estado);
        return m;
    }

    @Test
    @DisplayName("POST /materias como ADMINISTRADOR: 201 ACTIVA")
    void crearComoAdministrador() throws Exception {
        when(materias.existsByCodigo("MAT-101")).thenReturn(false);
        when(materias.save(any())).thenAnswer(inv -> {
            Materia m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 3L);
            return m;
        });

        mockMvc.perform(post("/api/v1/materias")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"MAT-101\",\"nombre\":\"Matematicas\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.codigo").value("MAT-101"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
    }

    @Test
    @DisplayName("POST /materias como ESTUDIANTE: 403")
    void crearComoEstudianteProhibido() throws Exception {
        mockMvc.perform(post("/api/v1/materias")
                        .header("Authorization", "Bearer " + token(Rol.ESTUDIANTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"MAT-101\",\"nombre\":\"Matematicas\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /materias con codigo duplicado: 400 ApiError")
    void crearDuplicado() throws Exception {
        when(materias.existsByCodigo("MAT-101")).thenReturn(true);

        mockMvc.perform(post("/api/v1/materias")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"MAT-101\",\"nombre\":\"Matematicas\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Ya existe una materia con el codigo MAT-101"));
    }

    @Test
    @DisplayName("GET /materias y /materias/{id} accesibles para ACUDIENTE; filtro de estado")
    void consultas() throws Exception {
        when(materias.findAllByOrderByNombreAsc()).thenReturn(List.of(
                materia(3L, "MAT-101", "Matematicas", EstadoMateria.ACTIVO)));
        when(materias.findByEstadoOrderByNombreAsc(EstadoMateria.INACTIVO)).thenReturn(List.of());
        when(materias.findById(3L)).thenReturn(Optional.of(
                materia(3L, "MAT-101", "Matematicas", EstadoMateria.ACTIVO)));
        String t = token(Rol.ACUDIENTE);

        mockMvc.perform(get("/api/v1/materias").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Matematicas"));

        mockMvc.perform(get("/api/v1/materias")
                        .queryParam("estado", "INACTIVO")
                        .header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/materias/3").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("MAT-101"));
    }

    @Test
    @DisplayName("GET /materias?estado=BORRADOR: 400 (filtro invalido)")
    void filtroInvalido() throws Exception {
        mockMvc.perform(get("/api/v1/materias")
                        .queryParam("estado", "BORRADOR")
                        .header("Authorization", "Bearer " + token(Rol.PROFESOR)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /materias/{id} como ADMINISTRADOR: 200")
    void actualizarComoAdministrador() throws Exception {
        when(materias.findById(3L)).thenReturn(Optional.of(
                materia(3L, "MAT-101", "Matematicas", EstadoMateria.ACTIVO)));
        when(materias.existsByCodigo("MAT-1")).thenReturn(false);
        when(materias.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/v1/materias/3")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"MAT-1\",\"nombre\":\"Matematicas basicas\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("MAT-1"));
    }

    @Test
    @DisplayName("POST /materias/{id}/desactivar y /activar: ciclo de borrado logico")
    void desactivarYActivar() throws Exception {
        when(materias.findById(3L)).thenReturn(Optional.of(
                materia(3L, "MAT-101", "Matematicas", EstadoMateria.ACTIVO)));
        when(materias.save(any())).thenAnswer(inv -> inv.getArgument(0));
        String t = token(Rol.ADMINISTRADOR);

        mockMvc.perform(post("/api/v1/materias/3/desactivar")
                        .header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INACTIVO"));

        when(materias.findById(3L)).thenReturn(Optional.of(
                materia(3L, "MAT-101", "Matematicas", EstadoMateria.INACTIVO)));
        mockMvc.perform(post("/api/v1/materias/3/activar")
                        .header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
    }
}
