package com.semillero.evaluacion.acudientes;

import com.semillero.evaluacion.acudientes.dto.ActualizarAcudientePeticion;
import com.semillero.evaluacion.acudientes.dto.AcudienteRespuesta;
import com.semillero.evaluacion.acudientes.dto.CrearAcudientePeticion;
import com.semillero.evaluacion.auditoria.AuditoriaServicio;
import com.semillero.evaluacion.exception.RecursoNoEncontradoException;
import com.semillero.evaluacion.exception.ReglaNegocioException;
import com.semillero.evaluacion.usuarios.Rol;
import com.semillero.evaluacion.usuarios.Usuario;
import com.semillero.evaluacion.usuarios.UsuarioRepositorio;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de negocio de los acudientes (tablas ACUDIENTES y USUARIOS).
 *
 * <p>Reglas vigentes (coherentes con el DDL):</p>
 * <ul>
 *   <li>El alta crea EN UNA TRANSACCION la cuenta (rol ACUDIENTE, exigido
 *       por el trigger TR_ACUDIENTES_ROL) y los datos personales 1:1.</li>
 *   <li>La contrasena temporal sigue la misma politica del API (8-100
 *       caracteres, letras y numeros) y nace con cambio obligatorio.</li>
 *   <li>USUARIO y DOCUMENTO son unicos (UNQ_ACUD_USUARIO,
 *       UNQ_ACUD_DOCUMENTO); el correo es obligatorio.</li>
 *   <li>Sin borrado fisico: desactivar pone ESTADO=INACTIVO y desactiva la
 *       cuenta (ACTIVO=0); activar restaura ambas. Un acudiente inactivo no
 *       solicita ni mantiene vinculaciones nuevas.</li>
 *   <li>Un acudiente solo consulta SU perfil (el controlador resuelve el
 *       perfil por el USUARIO_ID de la sesion); el resto de operaciones de
 *       escritura son exclusivas del ADMINISTRADOR.</li>
 *   <li>Toda operacion de escritura se audita (tabla append-only AUDITORIA).</li>
 * </ul>
 */
@Service
public class AcudienteServicio {

    private static final String ENTIDAD = "ACUDIENTES";

    private final AcudienteRepositorio repositorio;
    private final UsuarioRepositorio usuarios;
    private final PasswordEncoder encoder;
    private final AuditoriaServicio auditoria;

    public AcudienteServicio(AcudienteRepositorio repositorio, UsuarioRepositorio usuarios,
            PasswordEncoder encoder, AuditoriaServicio auditoria) {
        this.repositorio = repositorio;
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.auditoria = auditoria;
    }

    /** Registra un acudiente: cuenta nueva con contrasena temporal + datos personales. */
    @Transactional
    public AcudienteRespuesta crear(CrearAcudientePeticion peticion,
            Long usuarioId, String direccionIp) {
        String nombreUsuario = peticion.usuario().trim();
        String documento = peticion.documento().trim();

        if (usuarios.findByUsuario(nombreUsuario).isPresent()) {
            throw new ReglaNegocioException("El usuario de acceso '" + nombreUsuario
                    + "' ya esta en uso");
        }
        validarPoliticaContrasena(peticion.contrasenaTemporal());
        if (repositorio.existsByDocumento(documento)) {
            throw new ReglaNegocioException("Ya existe un acudiente con el documento " + documento);
        }

        Usuario cuenta = new Usuario();
        cuenta.setUsuario(nombreUsuario);
        cuenta.setContrasenaHash(encoder.encode(peticion.contrasenaTemporal()));
        cuenta.setRol(Rol.ACUDIENTE);
        cuenta.setActivo(true);
        cuenta.setCambioContrasenaPendiente(true);
        Usuario cuentaGuardada = usuarios.save(cuenta);

        Acudiente acudiente = new Acudiente();
        acudiente.setUsuarioId(cuentaGuardada.getId());
        acudiente.setDocumento(documento);
        acudiente.setNombres(peticion.nombres().trim());
        acudiente.setApellidos(peticion.apellidos().trim());
        acudiente.setParentesco(peticion.parentesco() != null
                ? peticion.parentesco().trim() : null);
        acudiente.setCorreo(peticion.correo().trim());
        acudiente.setTelefono(peticion.telefono() != null ? peticion.telefono().trim() : null);
        acudiente.setEstado(EstadoAcudiente.ACTIVO);
        Acudiente guardado = repositorio.save(acudiente);

        auditar("CREAR", guardado, usuarioId, Map.of(
                "documento", guardado.getDocumento(),
                "cuentaCreada", cuentaGuardada.getUsuario()), direccionIp);
        return aRespuesta(guardado, cuentaGuardada);
    }

    /** Consulta de acudientes; con ?estado= filtra por ACTIVO o INACTIVO (solo ADMIN). */
    @Transactional(readOnly = true)
    public List<AcudienteRespuesta> listar(String estado) {
        List<Acudiente> filas = estado != null
                ? repositorio.findByEstadoOrderByApellidosAscNombresAsc(estadoValido(estado))
                : repositorio.findAllByOrderByApellidosAscNombresAsc();
        return filas.stream()
                .map(a -> aRespuesta(a, buscarCuenta(a.getUsuarioId())))
                .toList();
    }

    /** Consulta de un acudiente por id: ADMIN o el propio acudiente. */
    @Transactional(readOnly = true)
    public AcudienteRespuesta buscarPorId(Long id, Long usuarioIdSesion, boolean esAdmin) {
        Acudiente acudiente = buscarEntidad(id);
        if (!esAdmin) {
            exigirPerfilPropio(acudiente, usuarioIdSesion);
        }
        return aRespuesta(acudiente, buscarCuenta(acudiente.getUsuarioId()));
    }

    /** Perfil del acudiente autenticado (el rol ACUDIENTE consulta lo suyo). */
    @Transactional(readOnly = true)
    public AcudienteRespuesta perfilDeLaSesion(Long usuarioIdSesion) {
        Acudiente acudiente = repositorio.findByUsuarioId(usuarioIdSesion)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La cuenta autenticada no esta vinculada a un acudiente"));
        return aRespuesta(acudiente, buscarCuenta(acudiente.getUsuarioId()));
    }

    /** Actualiza los datos personales (el documento mantiene su unicidad). */
    @Transactional
    public AcudienteRespuesta actualizar(Long id, ActualizarAcudientePeticion peticion,
            Long usuarioId, String direccionIp) {
        Acudiente acudiente = buscarEntidad(id);
        String documento = peticion.documento().trim();

        if (!acudiente.getDocumento().equals(documento)
                && repositorio.existsByDocumento(documento)) {
            throw new ReglaNegocioException("Ya existe un acudiente con el documento " + documento);
        }

        acudiente.setDocumento(documento);
        acudiente.setNombres(peticion.nombres().trim());
        acudiente.setApellidos(peticion.apellidos().trim());
        acudiente.setParentesco(peticion.parentesco() != null
                ? peticion.parentesco().trim() : null);
        acudiente.setCorreo(peticion.correo().trim());
        acudiente.setTelefono(peticion.telefono() != null ? peticion.telefono().trim() : null);
        Acudiente guardado = repositorio.save(acudiente);

        auditar("ACTUALIZAR", guardado, usuarioId, Map.of(
                "documento", guardado.getDocumento()), direccionIp);
        return aRespuesta(guardado, buscarCuenta(guardado.getUsuarioId()));
    }

    /** Desactiva el acudiente y su cuenta (borrado logico doble). */
    @Transactional
    public AcudienteRespuesta desactivar(Long id, Long usuarioId, String direccionIp) {
        Acudiente acudiente = buscarEntidad(id);
        if (acudiente.getEstado() == EstadoAcudiente.INACTIVO) {
            throw new ReglaNegocioException(
                    "El acudiente ya esta INACTIVO (estado actual: "
                            + acudiente.getEstado() + ")");
        }

        acudiente.setEstado(EstadoAcudiente.INACTIVO);
        Acudiente guardado = repositorio.save(acudiente);
        desactivarCuenta(guardado.getUsuarioId(), false);

        auditar("DESACTIVAR", guardado, usuarioId,
                Map.of("documento", guardado.getDocumento()), direccionIp);
        return aRespuesta(guardado, buscarCuenta(guardado.getUsuarioId()));
    }

    /** Reactiva el acudiente y su cuenta (borrado logico). */
    @Transactional
    public AcudienteRespuesta activar(Long id, Long usuarioId, String direccionIp) {
        Acudiente acudiente = buscarEntidad(id);
        if (acudiente.getEstado() == EstadoAcudiente.ACTIVO) {
            throw new ReglaNegocioException(
                    "El acudiente ya esta ACTIVO (estado actual: "
                            + acudiente.getEstado() + ")");
        }

        acudiente.setEstado(EstadoAcudiente.ACTIVO);
        Acudiente guardado = repositorio.save(acudiente);
        desactivarCuenta(guardado.getUsuarioId(), true);

        auditar("ACTIVAR", guardado, usuarioId,
                Map.of("documento", guardado.getDocumento()), direccionIp);
        return aRespuesta(guardado, buscarCuenta(guardado.getUsuarioId()));
    }

    private void desactivarCuenta(Long usuarioIdCuenta, boolean activo) {
        usuarios.findById(usuarioIdCuenta).ifPresent(cuenta -> {
            cuenta.setActivo(activo);
            usuarios.save(cuenta);
        });
    }

    private void exigirPerfilPropio(Acudiente acudiente, Long usuarioIdSesion) {
        if (!acudiente.getUsuarioId().equals(usuarioIdSesion)) {
            throw new AccessDeniedException(
                    "Un acudiente solo puede consultar su propio perfil");
        }
    }

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

    private EstadoAcudiente estadoValido(String estado) {
        try {
            return EstadoAcudiente.valueOf(estado);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException(
                    "El estado debe ser ACTIVO o INACTIVO (recibido: " + estado + ")");
        }
    }

    private Acudiente buscarEntidad(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un acudiente con id " + id));
    }

    private Usuario buscarCuenta(Long usuarioId) {
        return usuarios.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La cuenta del acudiente (usuario " + usuarioId + ") no existe"));
    }

    private void auditar(String accion, Acudiente acudiente, Long usuarioId,
            Map<String, Object> detalle, String direccionIp) {
        try {
            auditoria.registrar(accion, ENTIDAD, String.valueOf(acudiente.getId()),
                    usuarioId, detalle, direccionIp);
        } catch (Exception ignorada) {
            // fail-open: el registro de auditoria nunca bloquea la operacion
        }
    }

    static AcudienteRespuesta aRespuesta(Acudiente acudiente, Usuario cuenta) {
        return new AcudienteRespuesta(
                acudiente.getId(),
                acudiente.getUsuarioId(),
                cuenta != null ? cuenta.getUsuario() : null,
                acudiente.getDocumento(),
                acudiente.getNombres(),
                acudiente.getApellidos(),
                acudiente.getParentesco(),
                acudiente.getCorreo(),
                acudiente.getTelefono(),
                acudiente.getEstado().name(),
                acudiente.getCreadoEn());
    }
}
