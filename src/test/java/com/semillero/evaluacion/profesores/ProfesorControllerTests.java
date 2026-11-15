package com.semillero.evaluacion.profesores;

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
 * Pruebas de endpoints de profesores, SIN Oracle (perfil test).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfesorControllerTests extends ContextoPruebas {

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

    private Usuario cuentaProfesor() {
        Usuario u = new Usuario();
        ReflectionTestUtils.setField(u, "id", 30L);
        u.setUsuario("profe1");
        u.setContrasenaHash(encoder.encode("ClaveActual123"));
        u.setRol(Rol.PROFESOR);
        u.setActivo(true);
        u.setCambioContrasenaPendiente(true);
        return u;
    }

    private Profesor profesor(Long id, Long usuarioId, EstadoProfesor estado) {
        Profesor p = new Profesor();
        ReflectionTestUtils.setField(p, "id", id);
        p.setUsuarioId(usuarioId);
        p.setDocumento("801001");
        p.setNombres("Docente");
        p.setApellidos("Prueba");
        p.setEstado(estado);
        return p;
    }

    @Test
    @DisplayName("POST /profesores como ADMINISTRADOR: 201 con cuenta y estado ACTIVO")
    void crearComoAdministrador() throws Exception {
        when(usuarios.findByUsuario("profe1")).thenReturn(Optional.empty());
        when(profesores.existsByDocumento("801001")).thenReturn(false);
        when(usuarios.save(any())).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 30L);
            return u;
        });
        when(profesores.save(any())).thenAnswer(inv -> {
            Profesor p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 4L);
            return p;
        });
        when(usuarios.findById(30L)).thenReturn(Optional.of(cuentaProfesor()));

        mockMvc.perform(post("/api/v1/profesores")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"profe1\",\"contrasenaTemporal\":\"Temporal1\","
                                + "\"documento\":\"801001\",\"nombres\":\"Docente\","
                                + "\"apellidos\":\"Prueba\",\"especialidad\":\"Matematicas\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.usuario").value("profe1"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
    }

    @Test
    @DisplayName("POST /profesores como ACUDIENTE: 403 (solo ADMINISTRADOR)")
    void crearComoAcudienteProhibido() throws Exception {
        mockMvc.perform(post("/api/v1/profesores")
                        .header("Authorization", "Bearer " + token(Rol.ACUDIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"x1\",\"contrasenaTemporal\":\"Temporal1\","
                                + "\"documento\":\"801001\",\"nombres\":\"X\",\"apellidos\":\"Y\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /profesores con contrasena temporal debil: 400")
    void crearConContrasenaDebil() throws Exception {
        when(usuarios.findByUsuario("profe1")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/profesores")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"profe1\",\"contrasenaTemporal\":\"12345678\","
                                + "\"documento\":\"801001\",\"nombres\":\"D\",\"apellidos\":\"P\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(
                        "La contrasena temporal debe combinar letras y numeros"));
    }

    @Test
    @DisplayName("GET /profesores?estado=ACTIVO lista los disponibles (cualquier rol)")
    void consultas() throws Exception {
        when(profesores.findByEstadoOrderByApellidosAscNombresAsc(EstadoProfesor.ACTIVO))
                .thenReturn(List.of(profesor(4L, 30L, EstadoProfesor.ACTIVO)));
        when(profesores.findById(4L)).thenReturn(Optional.of(
                profesor(4L, 30L, EstadoProfesor.ACTIVO)));
        when(usuarios.findById(30L)).thenReturn(Optional.of(cuentaProfesor()));
        String t = token(Rol.ESTUDIANTE);

        mockMvc.perform(get("/api/v1/profesores")
                        .queryParam("estado", "ACTIVO")
                        .header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].documento").value("801001"));

        mockMvc.perform(get("/api/v1/profesores/4").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value("profe1"));
    }

    @Test
    @DisplayName("GET /profesores/{id} inexistente: 404")
    void consultaInexistente() throws Exception {
        when(profesores.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/profesores/99")
                        .header("Authorization", "Bearer " + token(Rol.PROFESOR)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /profesores/{id} como ADMINISTRADOR: 200 con datos actualizados")
    void actualizarComoAdministrador() throws Exception {
        when(profesores.findById(4L)).thenReturn(Optional.of(
                profesor(4L, 30L, EstadoProfesor.ACTIVO)));
        when(profesores.existsByDocumento("801002")).thenReturn(false);
        when(profesores.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(usuarios.findById(30L)).thenReturn(Optional.of(cuentaProfesor()));

        mockMvc.perform(put("/api/v1/profesores/4")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documento\":\"801002\",\"nombres\":\"Docente\","
                                + "\"apellidos\":\"Prueba Ruiz\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apellidos").value("Prueba Ruiz"));
    }

    @Test
    @DisplayName("POST /profesores/{id}/desactivar: 200 INACTIVO (borrado logico doble)")
    void desactivar() throws Exception {
        when(profesores.findById(4L)).thenReturn(Optional.of(
                profesor(4L, 30L, EstadoProfesor.ACTIVO)));
        when(profesores.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(usuarios.findById(30L)).thenReturn(Optional.of(cuentaProfesor()));

        mockMvc.perform(post("/api/v1/profesores/4/desactivar")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INACTIVO"));
    }
}
