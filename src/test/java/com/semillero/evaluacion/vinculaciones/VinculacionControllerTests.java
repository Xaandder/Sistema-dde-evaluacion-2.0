package com.semillero.evaluacion.vinculaciones;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.semillero.evaluacion.acudientes.Acudiente;
import com.semillero.evaluacion.acudientes.EstadoAcudiente;
import com.semillero.evaluacion.estudiantes.EstadoEstudiante;
import com.semillero.evaluacion.estudiantes.Estudiante;
import com.semillero.evaluacion.seguridad.JwtService;
import com.semillero.evaluacion.soporte.ContextoPruebas;
import com.semillero.evaluacion.usuarios.Rol;
import com.semillero.evaluacion.usuarios.Usuario;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/** Pruebas de endpoints de vinculaciones, SIN Oracle (perfil test). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VinculacionControllerTests extends ContextoPruebas {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private Usuario usuario(String nombre, Rol rol) {
        Usuario u = new Usuario();
        u.setUsuario(nombre);
        u.setContrasenaHash(encoder.encode("ClaveActual123"));
        u.setRol(rol);
        u.setActivo(true);
        u.setCambioContrasenaPendiente(false);
        ReflectionTestUtils.setField(u, "id", 1L);
        return u;
    }

    private String token(Rol rol) {
        String nombre = rol.name().toLowerCase();
        Usuario cuenta = usuario(nombre, rol);
        when(usuarios.findByUsuario(nombre)).thenReturn(Optional.of(cuenta));
        return jwtService.generarToken(cuenta);
    }

    private EstudianteAcudiente vinculacion(Long estudianteId, Long acudienteId) {
        EstudianteAcudiente v = new EstudianteAcudiente();
        v.setEstudianteId(estudianteId);
        v.setAcudienteId(acudienteId);
        return v;
    }

    @Test
    @DisplayName("POST /vinculaciones: ADMIN crea (200); ESTUDIANTE recibe 403")
    void vincular() throws Exception {
        when(acudientes.findById(2L)).thenReturn(Optional.of(acudiente(2L, 50L)));
        when(estudiantes.findById(10L)).thenReturn(Optional.of(estudiante(10L)));
        when(vinculaciones.existsByEstudianteIdAndAcudienteId(10L, 2L)).thenReturn(false);
        when(vinculaciones.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String cuerpo = "{\"estudianteId\":10,\"acudienteId\":2}";

        mockMvc.perform(post("/api/v1/vinculaciones")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estudianteId").value(10))
                .andExpect(jsonPath("$.acudienteId").value(2));

        mockMvc.perform(post("/api/v1/vinculaciones")
                        .header("Authorization", "Bearer " + token(Rol.ESTUDIANTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /vinculaciones: solo ADMIN (ACUDIENTE recibe 403)")
    void listar() throws Exception {
        when(vinculaciones.findAll())
                .thenReturn(List.of(vinculacion(10L, 2L)));

        mockMvc.perform(get("/api/v1/vinculaciones")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/v1/vinculaciones")
                        .header("Authorization", "Bearer " + token(Rol.ACUDIENTE)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /vinculaciones/mis-estudiantes: el ACUDIENTE ve los suyos; ajeno -> 403")
    void misEstudiantes() throws Exception {
        when(acudientes.findById(2L)).thenReturn(Optional.of(acudiente(2L, 50L)));
        when(vinculaciones.findByAcudienteIdOrderByFechaVinculacionAsc(2L))
                .thenReturn(List.of(vinculacion(10L, 2L)));
        when(estudiantes.findAllById(List.of(10L))).thenReturn(List.of(estudiante(10L)));

        // El token ACUDIENTE tiene usuarioId 1; el perfil 2 pertenece al usuario 50.
        mockMvc.perform(get("/api/v1/vinculaciones/mis-estudiantes")
                        .queryParam("acudienteId", "2")
                        .header("Authorization", "Bearer " + token(Rol.ACUDIENTE)))
                .andExpect(status().isForbidden());

        // Perfil propio: usuarioId de la sesion (1L) = usuarioId del acudiente.
        when(acudientes.findById(3L)).thenReturn(Optional.of(acudiente(3L, 1L)));
        when(vinculaciones.findByAcudienteIdOrderByFechaVinculacionAsc(3L))
                .thenReturn(List.of(vinculacion(10L, 3L)));
        when(estudiantes.findAllById(List.of(10L))).thenReturn(List.of(estudiante(10L)));

        mockMvc.perform(get("/api/v1/vinculaciones/mis-estudiantes")
                        .queryParam("acudienteId", "3")
                        .header("Authorization", "Bearer " + token(Rol.ACUDIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].estudianteId").value(10))
                .andExpect(jsonPath("$[0].documento").value("1010010"));
    }

    @Test
    @DisplayName("DELETE /vinculaciones: ADMIN desvincula (204); inexistente -> 404")
    void desvincular() throws Exception {
        when(vinculaciones.findByEstudianteIdAndAcudienteId(10L, 2L))
                .thenReturn(Optional.of(vinculacion(10L, 2L)));

        mockMvc.perform(delete("/api/v1/vinculaciones")
                        .queryParam("estudianteId", "10")
                        .queryParam("acudienteId", "2")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR)))
                .andExpect(status().isNoContent());

        when(vinculaciones.findByEstudianteIdAndAcudienteId(11L, 2L))
                .thenReturn(Optional.empty());
        mockMvc.perform(delete("/api/v1/vinculaciones")
                        .queryParam("estudianteId", "11")
                        .queryParam("acudienteId", "2")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR)))
                .andExpect(status().isNotFound());
    }

    private Acudiente acudiente(Long id, Long usuarioId) {
        Acudiente a = new Acudiente();
        ReflectionTestUtils.setField(a, "id", id);
        a.setUsuarioId(usuarioId);
        a.setDocumento("70100" + id);
        a.setNombres("Acudiente");
        a.setApellidos("Prueba");
        a.setCorreo("acudiente" + id + "@colegio.edu");
        a.setEstado(EstadoAcudiente.ACTIVO);
        return a;
    }

    private Estudiante estudiante(Long id) {
        Estudiante e = new Estudiante();
        ReflectionTestUtils.setField(e, "id", id);
        e.setUsuarioId(90L + id);
        e.setDocumento("10100" + id);
        e.setNombres("Estudiante");
        e.setApellidos("Prueba");
        e.setEstado(EstadoEstudiante.ACTIVO);
        return e;
    }
}
