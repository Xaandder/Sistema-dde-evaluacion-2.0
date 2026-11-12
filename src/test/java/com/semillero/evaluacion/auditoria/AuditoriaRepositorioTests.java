package com.semillero.evaluacion.auditoria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Garantia estructural: el repositorio de auditoria SOLO expone save().
 * Java no podra compilar UPDATE ni DELETE sobre AUDITORIA (regla 11).
 */
class AuditoriaRepositorioTests {

    @Test
    @DisplayName("La interfaz expone exactamente un metodo: save(Auditoria)")
    void soloExponeSave() {
        Method[] metodos = AuditoriaRepositorio.class.getDeclaredMethods();

        assertEquals(1, metodos.length, "El repositorio debe declarar un unico metodo");
        assertEquals("save", metodos[0].getName());
        assertEquals(1, metodos[0].getParameterCount());
        assertEquals(Auditoria.class, metodos[0].getParameterTypes()[0]);
        assertEquals(Auditoria.class, metodos[0].getReturnType());
    }

    @Test
    @DisplayName("Ningun metodo heredado expone modificacion o eliminacion")
    void noHayMetodosDeModificacionNiEliminacion() {
        for (Method metodo : AuditoriaRepositorio.class.getMethods()) {
            String nombre = metodo.getName();
            boolean prohibido = nombre.startsWith("delete")
                    || nombre.equals("saveAll")
                    || nombre.equals("deleteAll")
                    || nombre.equals("deleteAllInBatch")
                    || nombre.equals("deleteInBatch");
            assertTrue(!prohibido || nombre.equals("save"),
                    "Metodo prohibido expuesto: " + nombre);
        }
    }
}
