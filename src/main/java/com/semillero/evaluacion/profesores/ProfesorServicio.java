package com.semillero.evaluacion.profesores;

import com.semillero.evaluacion.auditoria.AuditoriaServicio;
import com.semillero.evaluacion.exception.RecursoNoEncontradoException;
import com.semillero.evaluacion.exception.ReglaNegocioException;
import com.semillero.evaluacion.profesores.dto.ActualizarProfesorPeticion;
import com.semillero.evaluacion.profesores.dto.CrearProfesorPeticion;
import com.semillero.evaluacion.profesores.dto.ProfesorRespuesta;
import com.semillero.evaluacion.usuarios.Rol;
import com.semillero.evaluacion.usuarios.Usuario;
import com.semillero.evaluacion.usuarios.UsuarioRepositorio;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de negocio de los profesores (tablas PROFESORES y USUARIOS).
 * Espejo del modulo de estudiantes: alta transaccional de cuenta (rol
 * PROFESOR, trigger TR_PROFESORES_ROL) + datos personales 1:1, contrasena
 * temporal con cambio obligatorio, USUARIO y DOCUMENTO unicos, borrado
 * logico doble y auditoria de escrituras.
 */
@Service
public class ProfesorServicio {

    private static final String ENTIDAD = "PROFESORES";

    private final ProfesorRepositorio repositorio;
    private final UsuarioRepositorio usuarios;
    private final PasswordEncoder encoder;
    private final AuditoriaServicio auditoria;

    public ProfesorServicio(ProfesorRepositorio repositorio, UsuarioRepositorio usuarios,
            PasswordEncoder encoder, AuditoriaServicio auditoria) {
        this.repositorio = repositorio;
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.auditoria = auditoria;
    }

    /** Registra un profesor: cuenta nueva con contrasena temporal + datos personales. */
    @Transactional
    public ProfesorRespuesta crear(CrearProfesorPeticion peticion, Long usuarioId, String direccionIp) {
        String nombreUsuario = peticion.usuario().trim();
        String documento = peticion.documento().trim();

        if (usuarios.findByUsuario(nombreUsuario).isPresent()) {
            throw new ReglaNegocioException(
                    "El usuario de acceso '" + nombreUsuario + "' ya esta en uso");
        }
        validarPoliticaContrasena(peticion.contrasenaTemporal());
        if (repositorio.existsByDocumento(documento)) {
            throw new ReglaNegocioException(
                    "Ya existe un profesor con el documento " + documento);
        }

        Usuario cuenta = new Usuario();
        cuenta.setUsuario(nombreUsuario);
        cuenta.setContrasenaHash(encoder.encode(peticion.contrasenaTemporal()));
        cuenta.setRol(Rol.PROFESOR);
        cuenta.setActivo(true);
        cuenta.setCambioContrasenaPendiente(true);
        Usuario cuentaGuardada = usuarios.save(cuenta);

        Profesor profesor = new Profesor();
        profesor.setUsuarioId(cuentaGuardada.getId());
        profesor.setDocumento(documento);
        profesor.setNombres(peticion.nombres().trim());
        profesor.setApellidos(peticion.apellidos().trim());
        profesor.setEspecialidad(peticion.especialidad() != null
                ? peticion.especialidad().trim() : null);
        profesor.setCorreo(peticion.correo() != null ? peticion.correo().trim() : null);
        profesor.setTelefono(peticion.telefono() != null ? peticion.telefono().trim() : null);
        profesor.setEstado(EstadoProfesor.ACTIVO);
        Profesor guardado = repositorio.save(profesor);

        auditar("CREAR", guardado, usuarioId, Map.of(
                "documento", guardado.getDocumento(),
                "cuentaCreada", cuentaGuardada.getUsuario()), direccionIp);
        return aRespuesta(guardado, cuentaGuardada);
    }

    /** Consulta de profesores (los disponibles son los de estado ACTIVO). */
    @Transactional(readOnly = true)
    public List<ProfesorRespuesta> listar(String estado) {
        List<Profesor> filas;
        if (estado != null) {
            filas = repositorio.findByEstadoOrderByApellidosAscNombresAsc(estadoValido(estado));
        } else {
            filas = repositorio.findAllByOrderByApellidosAscNombresAsc();
        }
        return aRespuestas(filas);
    }

    /** Consulta de un profesor por id. */
    @Transactional(readOnly = true)
    public ProfesorRespuesta buscarPorId(Long id) {
        Profesor profesor = buscarEntidad(id);
        return aRespuesta(profesor, buscarCuenta(profesor.getUsuarioId()));
    }

    /** Actualiza los datos personales (el documento mantiene su unicidad). */
    @Transactional
    public ProfesorRespuesta actualizar(Long id, ActualizarProfesorPeticion peticion,
            Long usuarioId, String direccionIp) {
        Profesor profesor = buscarEntidad(id);
        String documento = peticion.documento().trim();

        if (!profesor.getDocumento().equals(documento)
                && repositorio.existsByDocumento(documento)) {
            throw new ReglaNegocioException(
                    "Ya existe un profesor con el documento " + documento);
        }

        String documentoAnterior = profesor.getDocumento();
        profesor.setDocumento(documento);
        profesor.setNombres(peticion.nombres().trim());
        profesor.setApellidos(peticion.apellidos().trim());
        profesor.setEspecialidad(peticion.especialidad() != null
                ? peticion.especialidad().trim() : null);
        profesor.setCorreo(peticion.correo() != null ? peticion.correo().trim() : null);
        profesor.setTelefono(peticion.telefono() != null ? peticion.telefono().trim() : null);
        Profesor guardado = repositorio.save(profesor);

        auditar("ACTUALIZAR", guardado, usuarioId, Map.of(
                "documentoAnterior", documentoAnterior,
                "documentoNuevo", guardado.getDocumento()), direccionIp);
        return aRespuesta(guardado, buscarCuenta(guardado.getUsuarioId()));
    }

    /** Desactiva al profesor y su cuenta (borrado logico doble). */
    @Transactional
    public ProfesorRespuesta desactivar(Long id, Long usuarioId, String direccionIp) {
        Profesor profesor = buscarEntidad(id);
        if (profesor.getEstado() == EstadoProfesor.INACTIVO) {
            throw new ReglaNegocioException("El profesor ya esta inactivo");
        }

        profesor.setEstado(EstadoProfesor.INACTIVO);
        Profesor guardado = repositorio.save(profesor);
        Usuario cuenta = buscarCuenta(guardado.getUsuarioId());
        cuenta.setActivo(false);
        cuenta.setActualizadoEn(java.time.LocalDateTime.now());
        usuarios.save(cuenta);

        auditar("DESACTIVAR", guardado, usuarioId,
                Map.of("documento", guardado.getDocumento()), direccionIp);
        return aRespuesta(guardado, cuenta);
    }

    /** Reactiva al profesor y su cuenta de acceso. */
    @Transactional
    public ProfesorRespuesta activar(Long id, Long usuarioId, String direccionIp) {
        Profesor profesor = buscarEntidad(id);
        if (profesor.getEstado() == EstadoProfesor.ACTIVO) {
            throw new ReglaNegocioException("El profesor ya esta activo");
        }

        profesor.setEstado(EstadoProfesor.ACTIVO);
        Profesor guardado = repositorio.save(profesor);
        Usuario cuenta = buscarCuenta(guardado.getUsuarioId());
        cuenta.setActivo(true);
        cuenta.setActualizadoEn(java.time.LocalDateTime.now());
        usuarios.save(cuenta);

        auditar("ACTIVAR", guardado, usuarioId,
                Map.of("documento", guardado.getDocumento()), direccionIp);
        return aRespuesta(guardado, cuenta);
    }

    /** Misma politica del cambio de contrasena (AuthService): largo y letras/numeros. */
    private void validarPoliticaContrasena(String contrasena) {
        if (contrasena.length() < 8 || contrasena.length() > 100) {
            throw new ReglaNegocioException(
                    "La contrasena temporal debe tener entre 8 y 100 caracteres");
        }
        boolean tieneLetra = contrasena.chars().anyMatch(Character::isLetter);
        boolean tieneNumero = contrasena.chars().anyMatch(Character::isDigit);
        if (!tieneLetra || !tieneNumero) {
            throw new ReglaNegocioException(
                    "La contrasena temporal debe combinar letras y numeros");
        }
    }

    private EstadoProfesor estadoValido(String estado) {
        try {
            return EstadoProfesor.valueOf(estado);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException(
                    "El estado debe ser ACTIVO o INACTIVO (recibido: " + estado + ")");
        }
    }

    private Profesor buscarEntidad(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un profesor con id " + id));
    }

    private Usuario buscarCuenta(Long usuarioId) {
        return usuarios.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La cuenta del profesor (usuario " + usuarioId + ") no existe"));
    }

    private List<ProfesorRespuesta> aRespuestas(List<Profesor> filas) {
        List<Long> ids = filas.stream().map(Profesor::getUsuarioId).toList();
        Map<Long, Usuario> cuentas = StreamSupport
                .stream(usuarios.findAllById(ids).spliterator(), false)
                .collect(Collectors.toMap(Usuario::getId, Function.identity()));
        return filas.stream()
                .map(p -> aRespuesta(p, cuentas.get(p.getUsuarioId())))
                .toList();
    }

    private void auditar(String accion, Profesor profesor, Long usuarioId,
            Map<String, Object> detalle, String direccionIp) {
        try {
            auditoria.registrar(accion, ENTIDAD, String.valueOf(profesor.getId()),
                    usuarioId, detalle, direccionIp);
        } catch (Exception ignorada) {
            // fail-open: el registro de auditoria nunca bloquea la operacion
        }
    }

    static ProfesorRespuesta aRespuesta(Profesor profesor, Usuario cuenta) {
        return new ProfesorRespuesta(
                profesor.getId(),
                profesor.getUsuarioId(),
                cuenta != null ? cuenta.getUsuario() : null,
                profesor.getDocumento(),
                profesor.getNombres(),
                profesor.getApellidos(),
                profesor.getEspecialidad(),
                profesor.getCorreo(),
                profesor.getTelefono(),
                profesor.getEstado().name(),
                profesor.getCreadoEn(),
                profesor.getActualizadoEn());
    }
}
