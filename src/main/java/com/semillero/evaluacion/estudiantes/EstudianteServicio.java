package com.semillero.evaluacion.estudiantes;

import com.semillero.evaluacion.auditoria.AuditoriaServicio;
import com.semillero.evaluacion.exception.RecursoNoEncontradoException;
import com.semillero.evaluacion.exception.ReglaNegocioException;
import com.semillero.evaluacion.estudiantes.dto.ActualizarEstudiantePeticion;
import com.semillero.evaluacion.estudiantes.dto.CrearEstudiantePeticion;
import com.semillero.evaluacion.estudiantes.dto.EstudianteRespuesta;
import com.semillero.evaluacion.comun.PaginaRespuesta;
import com.semillero.evaluacion.usuarios.Rol;
import com.semillero.evaluacion.usuarios.Usuario;
import com.semillero.evaluacion.usuarios.UsuarioRepositorio;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de negocio de los estudiantes (tablas ESTUDIANTES y USUARIOS).
 *
 * <p>Reglas vigentes:</p>
 * <ul>
 *   <li>El alta crea EN UNA TRANSACCION la cuenta (rol ESTUDIANTE, exigido
 *       por el trigger TR_ESTUDIANTES_ROL) y los datos personales 1:1.</li>
 *   <li>La contrasena temporal sigue la misma politica del API (8-100
 *       caracteres, letras y numeros) y nace con cambio obligatorio.</li>
 *   <li>USUARIO y DOCUMENTO son unicos (UNQ_USUARIOS_USUARIO,
 *       UNQ_ESTUD_DOCUMENTO).</li>
 *   <li>Sin borrado fisico: desactivar pone ESTADO=INACTIVO y desactiva la
 *       cuenta (ACTIVO=0); activar restaura ambas.</li>
 *   <li>Toda operacion de escritura se audita (tabla append-only AUDITORIA).</li>
 * </ul>
 */
@Service
public class EstudianteServicio {

    private static final String ENTIDAD = "ESTUDIANTES";

    private final EstudianteRepositorio repositorio;
    private final UsuarioRepositorio usuarios;
    private final PasswordEncoder encoder;
    private final AuditoriaServicio auditoria;

    public EstudianteServicio(EstudianteRepositorio repositorio, UsuarioRepositorio usuarios,
            PasswordEncoder encoder, AuditoriaServicio auditoria) {
        this.repositorio = repositorio;
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.auditoria = auditoria;
    }

    /** Registra un estudiante: cuenta nueva con contrasena temporal + datos personales. */
    @Transactional
    public EstudianteRespuesta crear(CrearEstudiantePeticion peticion,
            Long usuarioId, String direccionIp) {
        String nombreUsuario = peticion.usuario().trim();
        String documento = peticion.documento().trim();

        if (usuarios.findByUsuario(nombreUsuario).isPresent()) {
            throw new ReglaNegocioException("El usuario de acceso '" + nombreUsuario
                    + "' ya esta en uso");
        }
        validarPoliticaContrasena(peticion.contrasenaTemporal());
        if (repositorio.existsByDocumento(documento)) {
            throw new ReglaNegocioException("Ya existe un estudiante con el documento " + documento);
        }

        Usuario cuenta = new Usuario();
        cuenta.setUsuario(nombreUsuario);
        cuenta.setContrasenaHash(encoder.encode(peticion.contrasenaTemporal()));
        cuenta.setRol(Rol.ESTUDIANTE);
        cuenta.setActivo(true);
        cuenta.setCambioContrasenaPendiente(true);
        Usuario cuentaGuardada = usuarios.save(cuenta);

        Estudiante estudiante = new Estudiante();
        estudiante.setUsuarioId(cuentaGuardada.getId());
        estudiante.setDocumento(documento);
        estudiante.setNombres(peticion.nombres().trim());
        estudiante.setApellidos(peticion.apellidos().trim());
        estudiante.setFechaNacimiento(peticion.fechaNacimiento());
        estudiante.setCorreo(peticion.correo() != null ? peticion.correo().trim() : null);
        estudiante.setTelefono(peticion.telefono() != null ? peticion.telefono().trim() : null);
        estudiante.setEstado(EstadoEstudiante.ACTIVO);
        Estudiante guardado = repositorio.save(estudiante);

        auditar("CREAR", guardado, usuarioId, Map.of(
                "documento", guardado.getDocumento(),
                "cuentaCreada", cuentaGuardada.getUsuario()), direccionIp);
        return aRespuesta(guardado, cuentaGuardada);
    }

    /** Consulta de estudiantes; con ?estado= filtra por ACTIVO o INACTIVO. */
    /** Consulta PAGINADA de estudiantes; con ?estado= filtra por ACTIVO o INACTIVO. */
    @Transactional(readOnly = true)
    public PaginaRespuesta<EstudianteRespuesta> listar(String estado, Pageable pageable) {
        Page<Estudiante> filas = estado != null
                ? repositorio.findByEstadoOrderByApellidosAscNombresAsc(
                        estadoValido(estado), pageable)
                : repositorio.findAllByOrderByApellidosAscNombresAsc(pageable);
        return new PaginaRespuesta<>(aRespuestas(filas.getContent()),
                filas.getNumber(), filas.getSize(), filas.getTotalElements(),
                filas.getTotalPages());
    }

    /** Consulta de un estudiante por id. */
    @Transactional(readOnly = true)
    public EstudianteRespuesta buscarPorId(Long id) {
        Estudiante estudiante = buscarEntidad(id);
        Usuario cuenta = buscarCuenta(estudiante.getUsuarioId());
        return aRespuesta(estudiante, cuenta);
    }

    /** Actualiza los datos personales (el documento mantiene su unicidad). */
    @Transactional
    public EstudianteRespuesta actualizar(Long id, ActualizarEstudiantePeticion peticion,
            Long usuarioId, String direccionIp) {
        Estudiante estudiante = buscarEntidad(id);
        String documento = peticion.documento().trim();

        if (!estudiante.getDocumento().equals(documento)
                && repositorio.existsByDocumento(documento)) {
            throw new ReglaNegocioException("Ya existe un estudiante con el documento " + documento);
        }

        String documentoAnterior = estudiante.getDocumento();
        estudiante.setDocumento(documento);
        estudiante.setNombres(peticion.nombres().trim());
        estudiante.setApellidos(peticion.apellidos().trim());
        estudiante.setFechaNacimiento(peticion.fechaNacimiento());
        estudiante.setCorreo(peticion.correo() != null ? peticion.correo().trim() : null);
        estudiante.setTelefono(peticion.telefono() != null ? peticion.telefono().trim() : null);
        Estudiante guardado = repositorio.save(estudiante);

        auditar("ACTUALIZAR", guardado, usuarioId, Map.of(
                "documentoAnterior", documentoAnterior,
                "documentoNuevo", guardado.getDocumento()), direccionIp);
        return aRespuesta(guardado, buscarCuenta(guardado.getUsuarioId()));
    }

    /**
     * Desactiva al estudiante y su cuenta (borrado logico doble: la base
     * prohibe DELETE en ambas tablas).
     */
    @Transactional
    public EstudianteRespuesta desactivar(Long id, Long usuarioId, String direccionIp) {
        Estudiante estudiante = buscarEntidad(id);
        if (estudiante.getEstado() == EstadoEstudiante.INACTIVO) {
            throw new ReglaNegocioException("El estudiante ya esta inactivo");
        }

        estudiante.setEstado(EstadoEstudiante.INACTIVO);
        Estudiante guardado = repositorio.save(estudiante);
        Usuario cuenta = buscarCuenta(guardado.getUsuarioId());
        cuenta.setActivo(false);
        cuenta.setActualizadoEn(java.time.LocalDateTime.now());
        usuarios.save(cuenta);

        auditar("DESACTIVAR", guardado, usuarioId,
                Map.of("documento", guardado.getDocumento()), direccionIp);
        return aRespuesta(guardado, cuenta);
    }

    /** Reactiva al estudiante y su cuenta de acceso. */
    @Transactional
    public EstudianteRespuesta activar(Long id, Long usuarioId, String direccionIp) {
        Estudiante estudiante = buscarEntidad(id);
        if (estudiante.getEstado() == EstadoEstudiante.ACTIVO) {
            throw new ReglaNegocioException("El estudiante ya esta activo");
        }

        estudiante.setEstado(EstadoEstudiante.ACTIVO);
        Estudiante guardado = repositorio.save(estudiante);
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

    private EstadoEstudiante estadoValido(String estado) {
        try {
            return EstadoEstudiante.valueOf(estado);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException(
                    "El estado debe ser ACTIVO o INACTIVO (recibido: " + estado + ")");
        }
    }

    private Estudiante buscarEntidad(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un estudiante con id " + id));
    }

    private Usuario buscarCuenta(Long usuarioId) {
        return usuarios.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La cuenta del estudiante (usuario " + usuarioId + ") no existe"));
    }

    /** Resuelve el nombre de cuenta de cada estudiante en una sola consulta. */
    private List<EstudianteRespuesta> aRespuestas(List<Estudiante> filas) {
        List<Long> ids = filas.stream().map(Estudiante::getUsuarioId).toList();
        Map<Long, Usuario> cuentas = java.util.stream.StreamSupport
                .stream(usuarios.findAllById(ids).spliterator(), false)
                .collect(Collectors.toMap(Usuario::getId, Function.identity()));
        return filas.stream()
                .map(e -> aRespuesta(e, cuentas.get(e.getUsuarioId())))
                .toList();
    }

    private void auditar(String accion, Estudiante estudiante, Long usuarioId,
            Map<String, Object> detalle, String direccionIp) {
        try {
            auditoria.registrar(accion, ENTIDAD, String.valueOf(estudiante.getId()),
                    usuarioId, detalle, direccionIp);
        } catch (Exception ignorada) {
            // fail-open: el registro de auditoria nunca bloquea la operacion
        }
    }

    static EstudianteRespuesta aRespuesta(Estudiante estudiante, Usuario cuenta) {
        return new EstudianteRespuesta(
                estudiante.getId(),
                estudiante.getUsuarioId(),
                cuenta != null ? cuenta.getUsuario() : null,
                estudiante.getDocumento(),
                estudiante.getNombres(),
                estudiante.getApellidos(),
                estudiante.getFechaNacimiento(),
                estudiante.getCorreo(),
                estudiante.getTelefono(),
                estudiante.getEstado().name(),
                estudiante.getCreadoEn(),
                estudiante.getActualizadoEn());
    }
}
