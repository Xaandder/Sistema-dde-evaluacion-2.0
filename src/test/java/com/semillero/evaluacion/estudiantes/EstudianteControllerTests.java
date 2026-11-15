package com.semillero.evaluacion.estudiantes;

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
 * Pruebas de endpoints de estudiantes, SIN Oracle (perfil test).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EstudianteControllerTests extends ContextoPruebas {

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
        ReflectionTestUtils.setField(u, "id", 20L);
        return u;
    }

    private String token(Rol rol) {
        String nombre = rol.name().toLowerCase();
        Usuario cuenta = usuario(nombre, rol, false);
        when(usuarios.findByUsuario(nombre)).thenReturn(Optional.of(cuenta));
        return jwtService.generarToken(cuenta);
    }

    private Usuario cuentaEstudiante() {
        return usuario("est.nuevo", Rol.ESTUDIANTE, true);
    }

    @Test
    @DisplayName("POST /estudiantes como ADMINISTRADOR: 201 con cuenta y estado ACTIVO")
    void crearComoAdministrador() throws Exception {
        when(usuarios.findByUsuario("est.nuevo")).thenReturn(Optional.empty());
        when(estudiantes.existsByDocumento("101010101")).thenReturn(false);
        when(usuarios.save(any())).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 20L);
            return u;
        });
        when(estudiantes.save(any())).thenAnswer(inv -> {
            Estudiante e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", 10L);
            return e;
        });
        when(usuarios.findById(20L)).thenReturn(Optional.of(cuentaEstudiante()));

        mockMvc.perform(post("/api/v1/estudiantes")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"est.nuevo\",\"contrasenaTemporal\":\"Temporal1\","
                                + "\"documento\":\"101010101\",\"nombres\":\"Ana\","
                                + "\"apellidos\":\"Gomez\",\"correo\":\"ana@colegio.edu\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.usuario").value("est.nuevo"))
                .andExpect(jsonPath("$.documento").value("101010101"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
    }

    @Test
    @DisplayName("POST /estudiantes con usuario de acceso repetido: 400")
    void crearConUsuarioRepetido() throws Exception {
        when(usuarios.findByUsuario("est.nuevo")).thenReturn(Optional.of(cuentaEstudiante()));

        mockMvc.perform(post("/api/v1/estudiantes")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"est.nuevo\",\"contrasenaTemporal\":\"Temporal1\","
                                + "\"documento\":\"101010101\",\"nombres\":\"Ana\","
                                + "\"apellidos\":\"Gomez\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(
                        "El usuario de acceso 'est.nuevo' ya esta en uso"));
    }

    @Test
    @DisplayName("POST /estudiantes con contrasena temporal debil: 400 (politica del API)")
    void crearConContrasenaDebil() throws Exception {
        when(usuarios.findByUsuario("est.nuevo")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/estudiantes")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"est.nuevo\",\"contrasenaTemporal\":\"12345678\","
                                + "\"documento\":\"101010101\",\"nombres\":\"Ana\","
                                + "\"apellidos\":\"Gomez\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(
                        "La contrasena temporal debe combinar letras y numeros"));
    }

    @Test
    @DisplayName("POST /estudiantes como PROFESOR: 403 (solo ADMINISTRADOR)")
    void crearComoProfesorProhibido() throws Exception {
        // Cuerpo valido: el rechazo debe venir de la seguridad por rol, no del DTO.
        mockMvc.perform(post("/api/v1/estudiantes")
                        .header("Authorization", "Bearer " + token(Rol.PROFESOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"x1\",\"contrasenaTemporal\":\"Temporal1\","
                                + "\"documento\":\"101\",\"nombres\":\"X\",\"apellidos\":\"Y\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /estudiantes y /estudiantes/{id} accesibles para ESTUDIANTE autenticado")
    void consultas() throws Exception {
        Estudiante e = new Estudiante();
        ReflectionTestUtils.setField(e, "id", 10L);
        e.setUsuarioId(20L);
        e.setDocumento("101010101");
        e.setNombres("Ana");
        e.setApellidos("Gomez");
        e.setEstado(EstadoEstudiante.ACTIVO);
        when(estudiantes.findAllByOrderByApellidosAscNombresAsc()).thenReturn(List.of(e));
        when(estudiantes.findById(10L)).thenReturn(Optional.of(e));
        when(usuarios.findAllById(List.of(20L))).thenReturn(List.of(cuentaEstudiante()));
        when(usuarios.findById(20L)).thenReturn(Optional.of(cuentaEstudiante()));
        String t = token(Rol.ESTUDIANTE);

        mockMvc.perform(get("/api/v1/estudiantes").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].usuario").value("est.nuevo"));

        mockMvc.perform(get("/api/v1/estudiantes/10").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apellidos").value("Gomez"));
    }

    @Test
    @DisplayName("PUT /estudiantes/{id} como ADMINISTRADOR: 200 con datos actualizados")
    void actualizarComoAdministrador() throws Exception {
        Estudiante e = new Estudiante();
        ReflectionTestUtils.setField(e, "id", 10L);
        e.setUsuarioId(20L);
        e.setDocumento("101010101");
        e.setNombres("Ana");
        e.setApellidos("Gomez");
        e.setEstado(EstadoEstudiante.ACTIVO);
        when(estudiantes.findById(10L)).thenReturn(Optional.of(e));
        when(estudiantes.existsByDocumento("101010102")).thenReturn(false);
        when(estudiantes.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(usuarios.findById(20L)).thenReturn(Optional.of(cuentaEstudiante()));

        mockMvc.perform(put("/api/v1/estudiantes/10")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documento\":\"101010102\",\"nombres\":\"Ana Maria\","
                                + "\"apellidos\":\"Gomez Ruiz\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombres").value("Ana Maria"))
                .andExpect(jsonPath("$.documento").value("101010102"));
    }

    @Test
    @DisplayName("POST /estudiantes/{id}/desactivar: 200 INACTIVO; GET inexistente: 404")
    void desactivarYConsultaInexistente() throws Exception {
        Estudiante e = new Estudiante();
        ReflectionTestUtils.setField(e, "id", 10L);
        e.setUsuarioId(20L);
        e.setDocumento("101010101");
        e.setNombres("Ana");
        e.setApellidos("Gomez");
        e.setEstado(EstadoEstudiante.ACTIVO);
        when(estudiantes.findById(10L)).thenReturn(Optional.of(e));
        when(usuarios.findById(20L)).thenReturn(Optional.of(cuentaEstudiante()));
        when(estudiantes.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/estudiantes/10/desactivar")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INACTIVO"));

        when(estudiantes.findById(99L)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/v1/estudiantes/99")
                        .header("Authorization", "Bearer " + token(Rol.PROFESOR)))
                .andExpect(status().isNotFound());
    }
}
