package com.semillero.evaluacion.integracion;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.semillero.evaluacion.acudientes.AcudienteRepositorio;
import com.semillero.evaluacion.solicitudes.SolicitudRepositorio;
import com.semillero.evaluacion.usuarios.Usuario;
import com.semillero.evaluacion.usuarios.UsuarioRepositorio;
import com.semillero.evaluacion.vinculaciones.VinculacionRepositorio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prueba de humo de integracion contra el Oracle REAL (perfil local).
 *
 * <p>NO corre en la suite normal: se ejecuta SOLO cuando se define la
 * variable de entorno E2E_ORACLE=true, para no acoplar el build a la base.
 * Uso: {@code E2E_ORACLE=true ./mvnw test -Dtest=PruebaHumoOracleTests}</p>
 *
 * <p>El arranque del contexto con el perfil "local" ya comprueba, por si
 * solo, que: la conexion con Oracle funciona, {@code ddl-auto=validate}
 * valida TODAS las entidades contra el esquema real (incluidas ACUDIENTES,
 * ESTUDIANTE_ACUDIENTE y SOLICITUDES_VINCULACION del modulo de acudientes)
 * y la configuracion de seguridad/JWT carga.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "E2E_ORACLE", matches = "true")
class PruebaHumoOracleTests {

    @Autowired
    private UsuarioRepositorio usuarios;

    @Test
    @DisplayName("E2E: context carga contra Oracle y los repositorios leen datos reales")
    void contextoContraOracleReal() {
        Iterable<Usuario> cuentas = usuarios.findAll();
        assertTrue(cuentas.iterator().hasNext(),
                "La base debe tener al menos la cuenta del administrador");
        for (Usuario cuenta : cuentas) {
            assertTrue(cuenta.getRol() != null,
                    "Todas las cuentas leidas desde Oracle deben tener rol");
        }
    }

    @Test
    @DisplayName("E2E: los 14 repositorios JPA estan disponibles (tablas validadas)")
    void repositoriosValidados(
            @Autowired org.springframework.context.ApplicationContext contexto) {
        // 13 repos extienden CrudRepository; AuditoriaRepositorio extiende
        // Repository (tabla append-only de solo lectura): 14 en total.
        assertTrue(contexto.getBeanNamesForType(
                        org.springframework.data.repository.CrudRepository.class).length >= 13,
                "Se esperaban al menos 13 repositorios CrudRepository");
        // El modulo de acudientes (3 tablas nuevas) esta mapeado y validado:
        contexto.getBean(AcudienteRepositorio.class);
        contexto.getBean(VinculacionRepositorio.class);
        contexto.getBean(SolicitudRepositorio.class);
        // La tabla append-only AUDITORIA es de solo lectura (sin save):
        assertTrue(contexto.getBeanNamesForType(
                        com.semillero.evaluacion.auditoria.AuditoriaRepositorio.class).length == 1,
                "AuditoriaRepositorio debe existir como repositorio de solo lectura");
    }
}
