package com.semillero.evaluacion.solicitudes;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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

/** Pruebas de endpoints de solicitudes de vinculacion, SIN Oracle (perfil test). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SolicitudControllerTests extends ContextoPruebas {

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

    private SolicitudVinculacion solicitud(Long id, Long acudienteId, Long estudianteId,
            EstadoSolicitud estado) {
        SolicitudVinculacion s = new SolicitudVinculacion();
        ReflectionTestUtils.setField(s, "id", id);
        s.setAcudienteId(acudienteId);
        s.setEstudianteId(estudianteId);
        s.setEstado(estado);
        return s;
    }

    @Test
    @DisplayName("POST /solicitudes-vinculacion: ACUDIENTE crea PENDIENTE (201); ESTUDIANTE 403")
    void crear() throws Exception {
        when(acudientes.findByUsuarioId(1L)).thenReturn(Optional.of(acudiente(2L, 1L)));
        when(estudiantes.findById(10L)).thenReturn(Optional.of(estudiante(10L)));
        when(vinculaciones.existsByEstudianteIdAndAcudienteId(10L, 2L)).thenReturn(false);
        when(solicitudes.existsByAcudienteIdAndEstudianteIdAndEstado(
                2L, 10L, EstadoSolicitud.PENDIENTE)).thenReturn(false);
        when(solicitudes.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String cuerpo = "{\"estudianteId\":10,\"observacion\":\"Soy la madre\"}";

        mockMvc.perform(post("/api/v1/solicitudes-vinculacion")
                        .header("Authorization", "Bearer " + token(Rol.ACUDIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.estudianteId").value(10));

        mockMvc.perform(post("/api/v1/solicitudes-vinculacion")
                        .header("Authorization", "Bearer " + token(Rol.ESTUDIANTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /solicitudes-vinculacion: ACUDIENTE forzado a las suyas; ADMIN ve todas")
    void listar() throws Exception {
        when(acudientes.findByUsuarioId(1L)).thenReturn(Optional.of(acudiente(2L, 1L)));
        when(solicitudes.findByAcudienteIdOrderByFechaCreacionAsc(2L))
                .thenReturn(List.of(solicitud(7L, 2L, 10L, EstadoSolicitud.PENDIENTE)));

        mockMvc.perform(get("/api/v1/solicitudes-vinculacion")
                        .header("Authorization", "Bearer " + token(Rol.ACUDIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].acudienteId").value(2));

        when(solicitudes.findAllByOrderByFechaCreacionAsc())
                .thenReturn(List.of(solicitud(7L, 2L, 10L, EstadoSolicitud.PENDIENTE),
                        solicitud(8L, 3L, 11L, EstadoSolicitud.APROBADA)));

        mockMvc.perform(get("/api/v1/solicitudes-vinculacion")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("POST /solicitudes-vinculacion/{id}/aprobar: ADMIN aprueba (200); no pendiente -> 409")
    void aprobar() throws Exception {
        when(solicitudes.findById(7L)).thenReturn(Optional.of(
                solicitud(7L, 2L, 10L, EstadoSolicitud.PENDIENTE)));
        when(estudiantes.findById(10L)).thenReturn(Optional.of(estudiante(10L)));
        when(vinculaciones.existsByEstudianteIdAndAcudienteId(10L, 2L)).thenReturn(false);
        when(solicitudes.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/solicitudes-vinculacion/7/aprobar")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observacion\":\"Autorizada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"))
                .andExpect(jsonPath("$.revisadoPor").value(1));

        when(solicitudes.findById(8L)).thenReturn(Optional.of(
                solicitud(8L, 2L, 10L, EstadoSolicitud.APROBADA)));
        mockMvc.perform(post("/api/v1/solicitudes-vinculacion/8/aprobar")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /solicitudes-vinculacion/{id}/rechazar: ESTUDIANTE 403; ADMIN rechaza 200")
    void rechazar() throws Exception {
        when(solicitudes.findById(7L)).thenReturn(Optional.of(
                solicitud(7L, 2L, 10L, EstadoSolicitud.PENDIENTE)));
        when(solicitudes.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/solicitudes-vinculacion/7/rechazar")
                        .header("Authorization", "Bearer " + token(Rol.ESTUDIANTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/solicitudes-vinculacion/7/rechazar")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observacion\":\"Documento dudoso\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"));
    }
}
