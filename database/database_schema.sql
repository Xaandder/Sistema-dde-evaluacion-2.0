-- ============================================================================
-- SISTEMA DE GESTION DE CALIFICACIONES ACADEMICAS
-- Script DDL - Esquema SISTEMA_EVALUACION
-- Base de datos: Oracle AI Database 26ai Free (familia 23.x)
--
-- INSTRUCCIONES DE EJECUCION:
--   * Ejecutar conectado como el usuario ESQUEMA dueño de las tablas
--     (todas las tablas se crean en el esquema del usuario conectado).
--   * NO ejecutar conectado como SYS, SYSTEM ni como el esquema YESID.
--   * Este script NO crea usuarios ni esquemas (solo tablas, FKs, CHECKs,
--     UNIQUEs e índices).
--   * El script no incluye DROP: si algo falla a mitad de ejecución,
--     limpiar manualmente las tablas ya creadas antes de reintentar.
--
-- CONVENCIONES:
--   PK_  clave primaria          FK_  clave foránea
--   UNQ_ clave única             CK_  regla CHECK
--   UX_  índice único funcional  IX_  índice de rendimiento
--   TR_  trigger de integridad   *_REF referencia compuesta (UNIQUE de apoyo)
--   Borrado LÓGICO: los registros no se eliminan, se desactivan (ESTADO/ACTIVO).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. USUARIOS
-- Cuenta de acceso de TODOS los roles. El administrador no tiene fila en las
-- tablas de personas; estudiantes, acudientes y profesores sí (relación 1:1).
-- ----------------------------------------------------------------------------
CREATE TABLE USUARIOS (
    ID                          NUMBER       GENERATED ALWAYS AS IDENTITY,
    USUARIO                     VARCHAR2(100) NOT NULL,
    CONTRASENA_HASH             VARCHAR2(100) NOT NULL,   -- hash BCrypt, nunca texto plano
    ROL                         VARCHAR2(20)  NOT NULL,
    ACTIVO                      NUMBER(1)     DEFAULT 1   NOT NULL,
    CAMBIO_CONTRASENA_PENDIENTE NUMBER(1)     DEFAULT 1   NOT NULL,  -- 1: contraseña temporal, cambiar al primer login
    CREADO_EN                   TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    ACTUALIZADO_EN              TIMESTAMP,
    CONSTRAINT PK_USUARIOS PRIMARY KEY (ID),
    CONSTRAINT UNQ_USUARIOS_USUARIO UNIQUE (USUARIO),
    CONSTRAINT CK_USUARIOS_ROL CHECK (
        ROL IN ('ADMINISTRADOR', 'PROFESOR', 'ESTUDIANTE', 'ACUDIENTE')),
    CONSTRAINT CK_USUARIOS_ACTIVO CHECK (ACTIVO IN (0, 1)),
    CONSTRAINT CK_USUARIOS_CAMBIO_PENDIENTE CHECK (CAMBIO_CONTRASENA_PENDIENTE IN (0, 1))
);

-- ----------------------------------------------------------------------------
-- 2. ESTUDIANTES
-- Datos personales del estudiante. Cada estudiante tiene cuenta obligatoria.
-- ----------------------------------------------------------------------------
CREATE TABLE ESTUDIANTES (
    ID              NUMBER        GENERATED ALWAYS AS IDENTITY,
    USUARIO_ID      NUMBER        NOT NULL,
    DOCUMENTO       VARCHAR2(20)  NOT NULL,
    NOMBRES         VARCHAR2(100) NOT NULL,
    APELLIDOS       VARCHAR2(100) NOT NULL,
    FECHA_NACIMIENTO DATE,
    CORREO          VARCHAR2(150),
    TELEFONO        VARCHAR2(30),
    ESTADO          VARCHAR2(15)  DEFAULT 'ACTIVO' NOT NULL,
    CREADO_EN       TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    ACTUALIZADO_EN  TIMESTAMP,
    CONSTRAINT PK_ESTUDIANTES PRIMARY KEY (ID),
    CONSTRAINT FK_ESTUD_USUARIO FOREIGN KEY (USUARIO_ID) REFERENCES USUARIOS (ID),
    CONSTRAINT UNQ_ESTUD_USUARIO UNIQUE (USUARIO_ID),   -- relación 1:1 con USUARIOS
    CONSTRAINT UNQ_ESTUD_DOCUMENTO UNIQUE (DOCUMENTO),
    CONSTRAINT CK_ESTUD_ESTADO CHECK (ESTADO IN ('ACTIVO', 'INACTIVO'))
);

-- ----------------------------------------------------------------------------
-- 3. ACUDIENTES
-- Datos del acudiente. Se registra por sí mismo (cuenta + datos personales),
-- por eso CORREO es obligatorio y USUARIO_ID también.
-- ----------------------------------------------------------------------------
CREATE TABLE ACUDIENTES (
    ID              NUMBER        GENERATED ALWAYS AS IDENTITY,
    USUARIO_ID      NUMBER        NOT NULL,
    DOCUMENTO       VARCHAR2(20)  NOT NULL,
    NOMBRES         VARCHAR2(100) NOT NULL,
    APELLIDOS       VARCHAR2(100) NOT NULL,
    PARENTESCO      VARCHAR2(30),
    CORREO          VARCHAR2(150) NOT NULL,
    TELEFONO        VARCHAR2(30),
    ESTADO          VARCHAR2(15)  DEFAULT 'ACTIVO' NOT NULL,
    CREADO_EN       TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    ACTUALIZADO_EN  TIMESTAMP,
    CONSTRAINT PK_ACUDIENTES PRIMARY KEY (ID),
    CONSTRAINT FK_ACUD_USUARIO FOREIGN KEY (USUARIO_ID) REFERENCES USUARIOS (ID),
    CONSTRAINT UNQ_ACUD_USUARIO UNIQUE (USUARIO_ID),
    CONSTRAINT UNQ_ACUD_DOCUMENTO UNIQUE (DOCUMENTO),
    CONSTRAINT CK_ACUD_ESTADO CHECK (ESTADO IN ('ACTIVO', 'INACTIVO'))
);

-- ----------------------------------------------------------------------------
-- 4. PROFESORES
-- Datos del profesor. El administrador es quien lo registra (con su cuenta).
-- ----------------------------------------------------------------------------
CREATE TABLE PROFESORES (
    ID              NUMBER        GENERATED ALWAYS AS IDENTITY,
    USUARIO_ID      NUMBER        NOT NULL,
    DOCUMENTO       VARCHAR2(20)  NOT NULL,
    NOMBRES         VARCHAR2(100) NOT NULL,
    APELLIDOS       VARCHAR2(100) NOT NULL,
    ESPECIALIDAD    VARCHAR2(100),
    CORREO          VARCHAR2(150),
    TELEFONO        VARCHAR2(30),
    ESTADO          VARCHAR2(15)  DEFAULT 'ACTIVO' NOT NULL,
    CREADO_EN       TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    ACTUALIZADO_EN  TIMESTAMP,
    CONSTRAINT PK_PROFESORES PRIMARY KEY (ID),
    CONSTRAINT FK_PROF_USUARIO FOREIGN KEY (USUARIO_ID) REFERENCES USUARIOS (ID),
    CONSTRAINT UNQ_PROF_USUARIO UNIQUE (USUARIO_ID),
    CONSTRAINT UNQ_PROF_DOCUMENTO UNIQUE (DOCUMENTO),
    CONSTRAINT CK_PROF_ESTADO CHECK (ESTADO IN ('ACTIVO', 'INACTIVO'))
);

-- ----------------------------------------------------------------------------
-- 5. ESTUDIANTE_ACUDIENTE
-- Vínculo M:N. Una fila solo se crea cuando el administrador APRUEBA una
-- solicitud de vinculación. Un estudiante puede tener varios acudientes y
-- un acudiente varios estudiantes.
-- ----------------------------------------------------------------------------
CREATE TABLE ESTUDIANTE_ACUDIENTE (
    ESTUDIANTE_ID    NUMBER    NOT NULL,
    ACUDIENTE_ID     NUMBER    NOT NULL,
    FECHA_VINCULACION TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_EST_ACUD PRIMARY KEY (ESTUDIANTE_ID, ACUDIENTE_ID),
    CONSTRAINT FK_EA_ESTUDIANTE FOREIGN KEY (ESTUDIANTE_ID) REFERENCES ESTUDIANTES (ID),
    CONSTRAINT FK_EA_ACUDIENTE  FOREIGN KEY (ACUDIENTE_ID)  REFERENCES ACUDIENTES (ID)
);

-- ----------------------------------------------------------------------------
-- 6. SOLICITUDES_VINCULACION
-- Solicitud de un acudiente para vincularse a un estudiante.
-- Ciclo de vida: PENDIENTE -> (APROBADA | RECHAZADA). La aprobación crea la
-- fila en ESTUDIANTE_ACUDIENTE (lo garantiza Java, ver advertencias).
-- ----------------------------------------------------------------------------
CREATE TABLE SOLICITUDES_VINCULACION (
    ID               NUMBER        GENERATED ALWAYS AS IDENTITY,
    ACUDIENTE_ID     NUMBER        NOT NULL,
    ESTUDIANTE_ID    NUMBER        NOT NULL,
    ESTADO           VARCHAR2(15)  DEFAULT 'PENDIENTE' NOT NULL,
    FECHA_CREACION   TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    REVISADO_POR     NUMBER,                -- usuario ADMINISTRADOR que resolvió
    FECHA_RESOLUCION TIMESTAMP,
    OBSERVACION      VARCHAR2(500),
    CONSTRAINT PK_SOLICITUDES PRIMARY KEY (ID),
    CONSTRAINT FK_SOL_ACUDIENTE  FOREIGN KEY (ACUDIENTE_ID)  REFERENCES ACUDIENTES (ID),
    CONSTRAINT FK_SOL_ESTUDIANTE FOREIGN KEY (ESTUDIANTE_ID) REFERENCES ESTUDIANTES (ID),
    CONSTRAINT FK_SOL_REVISADO_POR FOREIGN KEY (REVISADO_POR) REFERENCES USUARIOS (ID),
    CONSTRAINT CK_SOL_ESTADO CHECK (ESTADO IN ('PENDIENTE', 'APROBADA', 'RECHAZADA'))
);

-- Una sola solicitud PENDIENTE por (acudiente, estudiante).
-- El índice funcional ignora las filas resueltas, permitiendo el historial.
CREATE UNIQUE INDEX UX_SOLICITUD_PENDIENTE ON SOLICITUDES_VINCULACION (
    CASE WHEN ESTADO = 'PENDIENTE' THEN ACUDIENTE_ID  END,
    CASE WHEN ESTADO = 'PENDIENTE' THEN ESTUDIANTE_ID END
);

-- ----------------------------------------------------------------------------
-- 7. ANIOS_ACADEMICOS
-- Años lectivos (ej. 2026). La cantidad de grupos, materias y periodos es
-- libre; aquí solo se registra el año y su estado.
-- ----------------------------------------------------------------------------
CREATE TABLE ANIOS_ACADEMICOS (
    ID         NUMBER       GENERATED ALWAYS AS IDENTITY,
    ANIO       NUMBER(4)    NOT NULL,
    ESTADO     VARCHAR2(15) DEFAULT 'ACTIVO' NOT NULL,
    CREADO_EN  TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_ANIOS PRIMARY KEY (ID),
    CONSTRAINT UNQ_ANIOS_ANIO UNIQUE (ANIO),
    CONSTRAINT CK_ANIOS_ANIO CHECK (ANIO BETWEEN 2000 AND 2100),
    CONSTRAINT CK_ANIOS_ESTADO CHECK (ESTADO IN ('ACTIVO', 'CERRADO'))
);

-- ----------------------------------------------------------------------------
-- 8. PERIODOS
-- Periodos de un año académico; la CANTIDAD es configurable por año.
-- Ciclo de vida: PENDIENTE -> ABIERTO -> CERRADO.
-- Solo en ABIERTO los profesores pueden registrar/editar calificaciones
-- (lo garantiza Java, ver advertencias).
-- La combinación UNIQUE (ID, ANIO_ID) existe para poder referenciar
-- (periodo, año) con una FK compuesta desde ASIGNACIONES_DOCENTES.
-- ----------------------------------------------------------------------------
CREATE TABLE PERIODOS (
    ID           NUMBER       GENERATED ALWAYS AS IDENTITY,
    ANIO_ID      NUMBER       NOT NULL,
    NUMERO       NUMBER(2)    NOT NULL,
    NOMBRE       VARCHAR2(50) NOT NULL,
    FECHA_INICIO DATE,
    FECHA_FIN    DATE,
    ESTADO       VARCHAR2(10) DEFAULT 'PENDIENTE' NOT NULL,
    CREADO_EN    TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_PERIODOS PRIMARY KEY (ID),
    CONSTRAINT FK_PERIODO_ANIO FOREIGN KEY (ANIO_ID) REFERENCES ANIOS_ACADEMICOS (ID),
    CONSTRAINT UNQ_PERIODO_ANIO_NUMERO UNIQUE (ANIO_ID, NUMERO),
    CONSTRAINT UNQ_PERIODO_ID_ANIO UNIQUE (ID, ANIO_ID),
    CONSTRAINT CK_PERIODO_NUMERO CHECK (NUMERO >= 1),
    CONSTRAINT CK_PERIODO_ESTADO CHECK (ESTADO IN ('PENDIENTE', 'ABIERTO', 'CERRADO')),
    CONSTRAINT CK_PERIODO_FECHAS CHECK (
        FECHA_FIN IS NULL OR FECHA_INICIO IS NULL OR FECHA_FIN >= FECHA_INICIO)
);

-- ----------------------------------------------------------------------------
-- 9. MATERIAS
-- Catálogo de asignaturas (ej. Matemáticas). Codificación libre por el admin.
-- ----------------------------------------------------------------------------
CREATE TABLE MATERIAS (
    ID          NUMBER        GENERATED ALWAYS AS IDENTITY,
    CODIGO      VARCHAR2(20)  NOT NULL,
    NOMBRE      VARCHAR2(100) NOT NULL,
    DESCRIPCION VARCHAR2(500),
    ESTADO      VARCHAR2(15)  DEFAULT 'ACTIVO' NOT NULL,
    CREADO_EN   TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_MATERIAS PRIMARY KEY (ID),
    CONSTRAINT UNQ_MATERIAS_CODIGO UNIQUE (CODIGO),
    CONSTRAINT CK_MATERIAS_ESTADO CHECK (ESTADO IN ('ACTIVO', 'INACTIVO'))
);

-- ----------------------------------------------------------------------------
-- 10. GRUPOS
-- Cursos (ej. 10-A, 10-B). Sin grados fijos: nombre libre por año.
-- La combinación UNIQUE (ID, ANIO_ID) permite la FK compuesta desde
-- MATRICULAS y ASIGNACIONES_DOCENTES, garantizando coherencia de año.
-- ----------------------------------------------------------------------------
CREATE TABLE GRUPOS (
    ID          NUMBER        GENERATED ALWAYS AS IDENTITY,
    ANIO_ID     NUMBER        NOT NULL,
    NOMBRE      VARCHAR2(20)  NOT NULL,
    DESCRIPCION VARCHAR2(200),
    ESTADO      VARCHAR2(15)  DEFAULT 'ACTIVO' NOT NULL,
    CREADO_EN   TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_GRUPOS PRIMARY KEY (ID),
    CONSTRAINT FK_GRUPO_ANIO FOREIGN KEY (ANIO_ID) REFERENCES ANIOS_ACADEMICOS (ID),
    CONSTRAINT UNQ_GRUPO_ANIO_NOMBRE UNIQUE (ANIO_ID, NOMBRE),
    CONSTRAINT UNQ_GRUPO_ID_ANIO UNIQUE (ID, ANIO_ID),
    CONSTRAINT CK_GRUPOS_ESTADO CHECK (ESTADO IN ('ACTIVO', 'INACTIVO'))
);

-- ----------------------------------------------------------------------------
-- 11. MATRICULAS
-- Inscripción de un estudiante en un grupo de un año académico.
-- * UNIQUE (ESTUDIANTE, GRUPO): no se duplica la matrícula en el mismo
--   grupo y año (el grupo ya determina el año). Si el estudiante se retira
--   y vuelve al MISMO grupo en el MISMO año, Java debe reactivar la fila
--   existente (RETIRADA -> ACTIVA), no insertar una nueva.
-- * UX_MATRICULA_ACTIVA: máximo UNA matrícula ACTIVA por estudiante y año.
-- * FK compuesta (GRUPO, ANIO): la matrícula solo puede apuntar a un grupo
--   que pertenezca exactamente al año declarado.
-- ----------------------------------------------------------------------------
CREATE TABLE MATRICULAS (
    ID              NUMBER        GENERATED ALWAYS AS IDENTITY,
    ESTUDIANTE_ID   NUMBER        NOT NULL,
    GRUPO_ID        NUMBER        NOT NULL,
    ANIO_ID         NUMBER        NOT NULL,
    FECHA_MATRICULA TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    ESTADO          VARCHAR2(15)  DEFAULT 'ACTIVA' NOT NULL,
    CONSTRAINT PK_MATRICULAS PRIMARY KEY (ID),
    CONSTRAINT FK_MAT_ESTUDIANTE FOREIGN KEY (ESTUDIANTE_ID) REFERENCES ESTUDIANTES (ID),
    CONSTRAINT FK_MAT_GRUPO FOREIGN KEY (GRUPO_ID, ANIO_ID) REFERENCES GRUPOS (ID, ANIO_ID),
    CONSTRAINT FK_MAT_ANIO FOREIGN KEY (ANIO_ID) REFERENCES ANIOS_ACADEMICOS (ID),
    CONSTRAINT UNQ_MATRICULA_ESTUD_GRUPO UNIQUE (ESTUDIANTE_ID, GRUPO_ID),
    -- Referencia compuesta: permite que CALIFICACIONES ancle su nota a la
    -- matrícula completa (estudiante + grupo + año) mediante FK compuesta.
    CONSTRAINT UNQ_MATRICULA_REF UNIQUE (ID, ESTUDIANTE_ID, GRUPO_ID, ANIO_ID),
    CONSTRAINT CK_MATRICULA_ESTADO CHECK (ESTADO IN ('ACTIVA', 'RETIRADA', 'FINALIZADA'))
);

-- Máximo una matrícula ACTIVA por (estudiante, año)
CREATE UNIQUE INDEX UX_MATRICULA_ACTIVA ON MATRICULAS (
    CASE WHEN ESTADO = 'ACTIVA' THEN ESTUDIANTE_ID END,
    CASE WHEN ESTADO = 'ACTIVA' THEN ANIO_ID      END
);

-- ----------------------------------------------------------------------------
-- 12. ASIGNACIONES_DOCENTES
-- Relación profesor + grupo + materia + PERIODO. Permite:
--   * un profesor con varias materias en el mismo grupo,
--   * varios profesores compartiendo un grupo,
--   * varios profesores para la misma materia,
--   * asignaciones independientes por periodo.
-- * UX_ASIGNACION_ACTIVA: no pueden existir dos asignaciones ACTIVAS con la
--   misma combinación (profesor, materia, grupo, periodo). Si se reemplaza
--   al profesor, la asignación anterior pasa a INACTIVA y se crea una nueva.
-- * FKs compuestas (grupo, año) y (periodo, año): el grupo y el periodo de
--   una asignación SIEMPRE pertenecen al mismo año académico.
-- ----------------------------------------------------------------------------
CREATE TABLE ASIGNACIONES_DOCENTES (
    ID          NUMBER       GENERATED ALWAYS AS IDENTITY,
    PROFESOR_ID NUMBER       NOT NULL,
    MATERIA_ID  NUMBER       NOT NULL,
    GRUPO_ID    NUMBER       NOT NULL,
    PERIODO_ID  NUMBER       NOT NULL,
    ANIO_ID     NUMBER       NOT NULL,
    ESTADO      VARCHAR2(15) DEFAULT 'ACTIVA' NOT NULL,
    CREADO_EN   TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_ASIGNACIONES PRIMARY KEY (ID),
    CONSTRAINT FK_ASIG_PROFESOR FOREIGN KEY (PROFESOR_ID) REFERENCES PROFESORES (ID),
    CONSTRAINT FK_ASIG_MATERIA  FOREIGN KEY (MATERIA_ID)  REFERENCES MATERIAS (ID),
    CONSTRAINT FK_ASIG_GRUPO FOREIGN KEY (GRUPO_ID, ANIO_ID) REFERENCES GRUPOS (ID, ANIO_ID),
    CONSTRAINT FK_ASIG_PERIODO FOREIGN KEY (PERIODO_ID, ANIO_ID) REFERENCES PERIODOS (ID, ANIO_ID),
    CONSTRAINT FK_ASIG_ANIO FOREIGN KEY (ANIO_ID) REFERENCES ANIOS_ACADEMICOS (ID),
    CONSTRAINT CK_ASIGNACION_ESTADO CHECK (ESTADO IN ('ACTIVA', 'INACTIVA')),
    -- Referencia compuesta: permite que CALIFICACIONES ancle su nota a la
    -- asignación completa (materia + grupo + año + periodo) mediante FK compuesta.
    CONSTRAINT UNQ_ASIGNACION_REF UNIQUE (ID, MATERIA_ID, GRUPO_ID, ANIO_ID, PERIODO_ID)
);

-- Una sola asignación ACTIVA por (profesor, materia, grupo, periodo)
CREATE UNIQUE INDEX UX_ASIGNACION_ACTIVA ON ASIGNACIONES_DOCENTES (
    CASE WHEN ESTADO = 'ACTIVA' THEN PROFESOR_ID END,
    CASE WHEN ESTADO = 'ACTIVA' THEN MATERIA_ID  END,
    CASE WHEN ESTADO = 'ACTIVA' THEN GRUPO_ID    END,
    CASE WHEN ESTADO = 'ACTIVA' THEN PERIODO_ID  END
);

CREATE INDEX IX_ASIGNACION_PROFESOR ON ASIGNACIONES_DOCENTES (PROFESOR_ID);
CREATE INDEX IX_ASIGNACION_GRUPO    ON ASIGNACIONES_DOCENTES (GRUPO_ID);

-- ----------------------------------------------------------------------------
-- 13. CALIFICACIONES
-- Nota de una evaluación, anclada a una MATRICULA y a una ASIGNACION.
-- El PERIODO se hereda de la asignación (no se repite como columna).
--
-- INTEGRIDAD ESTRUCTURAL GARANTIZADA POR LA BASE DE DATOS:
--   La fila copia GRUPO_ID y ANIO_ID y los fija con DOS claves foráneas
--   compuestas, de modo que grupo y año deben coincidir con la matrícula
--   Y con la asignación al mismo tiempo:
--
--     FK_CAL_MATRICULA  (MATRICULA_ID, ESTUDIANTE_ID, GRUPO_ID, ANIO_ID)
--         -> MATRICULAS (ID, ESTUDIANTE_ID, GRUPO_ID, ANIO_ID)
--        La nota pertenece a la matrícula del MISMO estudiante, grupo y año.
--
--     FK_CAL_ASIGNACION (ASIGNACION_ID, MATERIA_ID, GRUPO_ID, ANIO_ID, PERIODO_ID)
--         -> ASIGNACIONES_DOCENTES (ID, MATERIA_ID, GRUPO_ID, ANIO_ID, PERIODO_ID)
--        La nota pertenece a la asignación del MISMO grupo, año y periodo.
--
--   Al obligar ambas FK a que GRUPO_ID y ANIO_ID coincidan con la
--   matrícula y con la asignación, la base garantiza que TODA calificación
--   corresponde a una matrícula del mismo grupo y año de la asignación
--   docente (el estudiante está matriculado en el grupo de la asignación),
--   y que la materia y el periodo de la nota son los de su asignación.
--   Además, como la referencia es única en la tabla padre, las columnas
--   MATERIA_ID, GRUPO_ID, ANIO_ID y PERIODO_ID quedan inmutables tras la
--   inserción.
--
-- Lo que la base NO puede expresar y valida Java (ver informe):
--   matrícula ACTIVA, periodo ABIERTO, asignación ACTIVA, y que SOLO el
--   profesor propietario de la ASIGNACION_ID de la nota pueda modificarla:
--   los co-docentes comparten los diez espacios de registro, pero las notas
--   ya registradas pertenecen a la asignación que las creó.
--
-- Reglas propias de la nota:
-- * CHECK NUMERO_EVALUACION 1..10: máximo 10 evaluaciones posibles.
-- * UNIQUE (ESTUDIANTE, MATERIA, PERIODO, NUMERO): exactamente UNA nota por
--   número de evaluación por estudiante, materia y periodo, sin importar
--   cuántos profesores co-docentes tenga la materia en ese grupo. El
--   segundo profesor que intente registrar la evaluación N choca contra
--   esta clave y la base lo rechaza.
-- * PERMISOS (regla original, validada en Java porque SQL no puede
--   expresarla): cada profesor solo puede modificar las calificaciones de
--   SU propia asignación. La columna ASIGNACION_ID registra qué asignación
--   (profesor) creó la nota y NO cambia al editarla: un co-docente de la
--   misma materia/grupo/año/periodo puede registrar notas libres, pero NO
--   editar ni sobrescribir las registradas por otro profesor.
-- * Las notas SOLO se registran cuando existen (sin filas pendientes);
--   las evaluaciones pendientes se calculan en Java: 10 - registradas.
-- * Notas entre 0.0 y 5.0 con dos decimales (NUMBER(4,2)).
-- * ESTUDIANTE_ID deja de tener FK directa a ESTUDIANTES: su integridad se
--   garantiza por la cadena FK_CAL_MATRICULA -> MATRICULAS -> ESTUDIANTES,
--   evitando una FK redundante.
-- ----------------------------------------------------------------------------
CREATE TABLE CALIFICACIONES (
    ID                  NUMBER       GENERATED ALWAYS AS IDENTITY,
    MATRICULA_ID        NUMBER       NOT NULL,
    ESTUDIANTE_ID       NUMBER       NOT NULL,
    ASIGNACION_ID       NUMBER       NOT NULL,
    MATERIA_ID          NUMBER       NOT NULL,
    GRUPO_ID            NUMBER       NOT NULL,
    ANIO_ID             NUMBER       NOT NULL,
    PERIODO_ID          NUMBER       NOT NULL,
    NUMERO_EVALUACION   NUMBER(2)    NOT NULL,
    NOTA                NUMBER(4,2)  NOT NULL,
    COMENTARIO          VARCHAR2(500),
    FECHA_REGISTRO      TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
    REGISTRADO_POR      NUMBER       NOT NULL,   -- usuario PROFESOR que la registró
    FECHA_ACTUALIZACION TIMESTAMP,
    ACTUALIZADO_POR     NUMBER,                   -- usuario PROFESOR que la editó
    CONSTRAINT PK_CALIFICACIONES PRIMARY KEY (ID),
    CONSTRAINT FK_CAL_MATRICULA FOREIGN KEY (MATRICULA_ID, ESTUDIANTE_ID, GRUPO_ID, ANIO_ID)
        REFERENCES MATRICULAS (ID, ESTUDIANTE_ID, GRUPO_ID, ANIO_ID),
    CONSTRAINT FK_CAL_ASIGNACION FOREIGN KEY (ASIGNACION_ID, MATERIA_ID, GRUPO_ID, ANIO_ID, PERIODO_ID)
        REFERENCES ASIGNACIONES_DOCENTES (ID, MATERIA_ID, GRUPO_ID, ANIO_ID, PERIODO_ID),
    CONSTRAINT FK_CAL_REGISTRADO_POR FOREIGN KEY (REGISTRADO_POR) REFERENCES USUARIOS (ID),
    CONSTRAINT FK_CAL_ACTUALIZADO_POR FOREIGN KEY (ACTUALIZADO_POR) REFERENCES USUARIOS (ID),
    -- Una sola evaluación por estudiante + materia + periodo + número:
    -- los co-docentes COMPARTEN los mismos diez espacios de evaluación.
    CONSTRAINT UNQ_CALIF_ESTUD_MATERIA_PERIODO UNIQUE (ESTUDIANTE_ID, MATERIA_ID, PERIODO_ID, NUMERO_EVALUACION),
    CONSTRAINT CK_CALIF_NUMERO CHECK (NUMERO_EVALUACION BETWEEN 1 AND 10),
    CONSTRAINT CK_CALIF_NOTA CHECK (NOTA BETWEEN 0.0 AND 5.0)
);

CREATE INDEX IX_CALIFICACION_ASIGNACION ON CALIFICACIONES (ASIGNACION_ID);
CREATE INDEX IX_CALIFICACION_ESTUDIANTE ON CALIFICACIONES (ESTUDIANTE_ID);
CREATE INDEX IX_CALIFICACION_MATRICULA  ON CALIFICACIONES (MATRICULA_ID);

-- ----------------------------------------------------------------------------
-- 14. AUDITORIA
-- Registro append-only de acciones importantes: altas/ediciones de
-- usuarios, calificaciones, asignaciones, matrículas, resolución de
-- solicitudes y apertura/cierre de periodos. Java nunca actualiza ni
-- elimina filas de esta tabla.
-- ----------------------------------------------------------------------------
CREATE TABLE AUDITORIA (
    ID           NUMBER       GENERATED ALWAYS AS IDENTITY,
    USUARIO_ID   NUMBER,                       -- usuario que ejecutó la acción (puede ser nulo para acciones del sistema)
    ACCION       VARCHAR2(50) NOT NULL,        -- CREAR, ACTUALIZAR, DESACTIVAR, APROBAR, RECHAZAR, ABRIR, CERRAR, LOGIN_FALLIDO...
    ENTIDAD      VARCHAR2(50) NOT NULL,        -- CALIFICACIONES, USUARIOS, MATRICULAS...
    ENTIDAD_ID   VARCHAR2(50) NOT NULL,        -- identificador de la fila afectada
    DETALLE      CLOB,                         -- detalle JSON (valor anterior/nuevo, etc.)
    DIRECCION_IP VARCHAR2(64),
    FECHA        TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_AUDITORIA PRIMARY KEY (ID),
    CONSTRAINT FK_AUDITORIA_USUARIO FOREIGN KEY (USUARIO_ID) REFERENCES USUARIOS (ID)
);

CREATE INDEX IX_AUDITORIA_FECHA ON AUDITORIA (FECHA);
CREATE INDEX IX_AUDITORIA_ENTIDAD ON AUDITORIA (ENTIDAD, ENTIDAD_ID);

-- ----------------------------------------------------------------------------
-- TRIGGERS de integridad (complementan las restricciones declarativas)
-- Códigos de error propios: -20001 a -20010 (rango reservado de usuario)
-- ----------------------------------------------------------------------------

-- 0) La asignación que originó una calificación no puede cambiarse.
CREATE OR REPLACE TRIGGER TR_CALIFICACIONES_ASIGNACION_INMUTABLE
BEFORE UPDATE OF ASIGNACION_ID, MATERIA_ID, GRUPO_ID, ANIO_ID, PERIODO_ID
ON CALIFICACIONES
FOR EACH ROW
BEGIN
    IF :OLD.ASIGNACION_ID <> :NEW.ASIGNACION_ID
       OR :OLD.MATERIA_ID <> :NEW.MATERIA_ID
       OR :OLD.GRUPO_ID <> :NEW.GRUPO_ID
       OR :OLD.ANIO_ID <> :NEW.ANIO_ID
       OR :OLD.PERIODO_ID <> :NEW.PERIODO_ID THEN
        RAISE_APPLICATION_ERROR(-20010,
            'No se puede cambiar la asignacion original de una calificacion.');
    END IF;
END;
/
-- 1) AUDITORIA append-only: se prohíbe UPDATE y DELETE a cualquier usuario.
--
--    LIMITACIONES REALES DE ESTE TRIGGER (documentadas por decisión de
--    diseño; NO constituye una inmutabilidad absoluta):
--    * El propietario del esquema puede desactivarlo
--      (ALTER TRIGGER ... DISABLE o ALTER TABLE ... DISABLE ALL TRIGGERS),
--      borrarlo (DROP TRIGGER) o recrearlo.
--    * Un TRUNCATE TABLE AUDITORIA es DDL y NO dispara triggers DML de
--      fila: vaciaría la tabla eludiendo este trigger.
--    * El propietario puede DROP TABLE + CREATE TABLE, perdiendo el
--      historial.
--    Mitigación evaluada y NO aplicada (decisión definitiva): mover
--    AUDITORIA a un esquema dedicado con solo INSERT/SELECT. La barrera
--    adicional real es la política de Java: NINGUNA operación de la
--    aplicación ejecuta UPDATE ni DELETE sobre AUDITORIA.
CREATE OR REPLACE TRIGGER TR_AUDITORIA_SOLOLECTURA
BEFORE UPDATE OR DELETE ON AUDITORIA
FOR EACH ROW
BEGIN
    RAISE_APPLICATION_ERROR(-20001,
        'AUDITORIA es append-only: no se permite UPDATE ni DELETE');
END;
/

-- 2) Coherencia USUARIOS.ROL con la tabla de perfil correspondiente:
--    la fila de ESTUDIANTES solo puede apuntar a un usuario con rol ESTUDIANTE.
CREATE OR REPLACE TRIGGER TR_ESTUDIANTES_ROL
BEFORE INSERT OR UPDATE OF USUARIO_ID ON ESTUDIANTES
FOR EACH ROW
DECLARE
    V_ROL USUARIOS.ROL%TYPE;
BEGIN
    SELECT ROL INTO V_ROL FROM USUARIOS WHERE ID = :NEW.USUARIO_ID;
    IF V_ROL <> 'ESTUDIANTE' THEN
        RAISE_APPLICATION_ERROR(-20002,
            'El usuario vinculado a ESTUDIANTES debe tener rol ESTUDIANTE');
    END IF;
END;
/

CREATE OR REPLACE TRIGGER TR_ACUDIENTES_ROL
BEFORE INSERT OR UPDATE OF USUARIO_ID ON ACUDIENTES
FOR EACH ROW
DECLARE
    V_ROL USUARIOS.ROL%TYPE;
BEGIN
    SELECT ROL INTO V_ROL FROM USUARIOS WHERE ID = :NEW.USUARIO_ID;
    IF V_ROL <> 'ACUDIENTE' THEN
        RAISE_APPLICATION_ERROR(-20003,
            'El usuario vinculado a ACUDIENTES debe tener rol ACUDIENTE');
    END IF;
END;
/

CREATE OR REPLACE TRIGGER TR_PROFESORES_ROL
BEFORE INSERT OR UPDATE OF USUARIO_ID ON PROFESORES
FOR EACH ROW
DECLARE
    V_ROL USUARIOS.ROL%TYPE;
BEGIN
    SELECT ROL INTO V_ROL FROM USUARIOS WHERE ID = :NEW.USUARIO_ID;
    IF V_ROL <> 'PROFESOR' THEN
        RAISE_APPLICATION_ERROR(-20004,
            'El usuario vinculado a PROFESORES debe tener rol PROFESOR');
    END IF;
END;
/

-- 3) Al cambiar el rol de un usuario existente, exige que el perfil
--    vinculado sea coherente (los ADMINISTRADOR no deben tener perfil de
--    persona y cada rol exige exactamente su fila de perfil correspondiente).
CREATE OR REPLACE TRIGGER TR_USUARIOS_ROL
BEFORE UPDATE OF ROL ON USUARIOS
FOR EACH ROW
DECLARE
    V_CONTEO NUMBER;
    V_ERROR  CONSTANT PLS_INTEGER := -20005;
BEGIN
    IF :NEW.ROL = 'ADMINISTRADOR' THEN
        SELECT COUNT(*) INTO V_CONTEO FROM ESTUDIANTES WHERE USUARIO_ID = :NEW.ID;
        IF V_CONTEO > 0 THEN
            RAISE_APPLICATION_ERROR(V_ERROR,
                'Un usuario con perfil de ESTUDIANTE no puede cambiar a rol ADMINISTRADOR');
        END IF;
        SELECT COUNT(*) INTO V_CONTEO FROM ACUDIENTES WHERE USUARIO_ID = :NEW.ID;
        IF V_CONTEO > 0 THEN
            RAISE_APPLICATION_ERROR(V_ERROR,
                'Un usuario con perfil de ACUDIENTE no puede cambiar a rol ADMINISTRADOR');
        END IF;
        SELECT COUNT(*) INTO V_CONTEO FROM PROFESORES WHERE USUARIO_ID = :NEW.ID;
        IF V_CONTEO > 0 THEN
            RAISE_APPLICATION_ERROR(V_ERROR,
                'Un usuario con perfil de PROFESOR no puede cambiar a rol ADMINISTRADOR');
        END IF;
    ELSIF :NEW.ROL = 'ESTUDIANTE' THEN
        SELECT COUNT(*) INTO V_CONTEO FROM ESTUDIANTES WHERE USUARIO_ID = :NEW.ID;
        IF V_CONTEO <> 1 THEN
            RAISE_APPLICATION_ERROR(V_ERROR,
                'El rol ESTUDIANTE requiere exactamente una fila en ESTUDIANTES');
        END IF;
    ELSIF :NEW.ROL = 'ACUDIENTE' THEN
        SELECT COUNT(*) INTO V_CONTEO FROM ACUDIENTES WHERE USUARIO_ID = :NEW.ID;
        IF V_CONTEO <> 1 THEN
            RAISE_APPLICATION_ERROR(V_ERROR,
                'El rol ACUDIENTE requiere exactamente una fila en ACUDIENTES');
        END IF;
    ELSIF :NEW.ROL = 'PROFESOR' THEN
        SELECT COUNT(*) INTO V_CONTEO FROM PROFESORES WHERE USUARIO_ID = :NEW.ID;
        IF V_CONTEO <> 1 THEN
            RAISE_APPLICATION_ERROR(V_ERROR,
                'El rol PROFESOR requiere exactamente una fila en PROFESORES');
        END IF;
    END IF;
END;
/

-- 4) Borrado físico prohibido en USUARIOS y en las tres tablas de perfil:
--    la salida de una persona se hace por desactivación lógica
--    (ACTIVO=0 / ESTADO='INACTIVO'), nunca con DELETE. Así ningún usuario
--    queda con un rol incompatible o sin perfil, y se protege el historial
--    referenciado por matrículas, calificaciones y auditoría.
CREATE OR REPLACE TRIGGER TR_USUARIOS_NODELETE
BEFORE DELETE ON USUARIOS
FOR EACH ROW
BEGIN
    RAISE_APPLICATION_ERROR(-20006,
        'USUARIOS no permite borrado fisico: desactive la cuenta (ACTIVO=0)');
END;
/

CREATE OR REPLACE TRIGGER TR_ESTUDIANTES_NODELETE
BEFORE DELETE ON ESTUDIANTES
FOR EACH ROW
BEGIN
    RAISE_APPLICATION_ERROR(-20007,
        'ESTUDIANTES no permite borrado fisico: use ESTADO=INACTIVO');
END;
/

CREATE OR REPLACE TRIGGER TR_ACUDIENTES_NODELETE
BEFORE DELETE ON ACUDIENTES
FOR EACH ROW
BEGIN
    RAISE_APPLICATION_ERROR(-20008,
        'ACUDIENTES no permite borrado fisico: use ESTADO=INACTIVO');
END;
/

CREATE OR REPLACE TRIGGER TR_PROFESORES_NODELETE
BEFORE DELETE ON PROFESORES
FOR EACH ROW
BEGIN
    RAISE_APPLICATION_ERROR(-20009,
        'PROFESORES no permite borrado fisico: use ESTADO=INACTIVO');
END;
/

-- ----------------------------------------------------------------------------
-- Documentación integrada en el diccionario de datos (visible en SQL Developer)
-- ----------------------------------------------------------------------------
COMMENT ON TABLE  USUARIOS               IS 'Cuentas de acceso con rol y estado (BCrypt)';
COMMENT ON TABLE  ESTUDIANTES            IS 'Datos personales de estudiantes (1:1 con USUARIOS)';
COMMENT ON TABLE  ACUDIENTES             IS 'Datos personales de acudientes (auto-registro)';
COMMENT ON TABLE  PROFESORES             IS 'Datos personales de profesores (1:1 con USUARIOS)';
COMMENT ON TABLE  ESTUDIANTE_ACUDIENTE   IS 'Vinculacion aprobada estudiante-acudiente (M:N)';
COMMENT ON TABLE  SOLICITUDES_VINCULACION IS 'Solicitud de vinculacion con ciclo PENDIENTE/APROBADA/RECHAZADA';
COMMENT ON TABLE  ANIOS_ACADEMICOS       IS 'Anios lectivos';
COMMENT ON TABLE  PERIODOS               IS 'Periodos por anio, cantidad configurable, PENDIENTE/ABIERTO/CERRADO';
COMMENT ON TABLE  MATERIAS               IS 'Catalogo de asignaturas';
COMMENT ON TABLE  GRUPOS                 IS 'Grupos por anio con nombre libre (10-A, 11-B...)';
COMMENT ON TABLE  MATRICULAS             IS 'Inscripcion estudiante-grupo-anio con estado';
COMMENT ON TABLE  ASIGNACIONES_DOCENTES  IS 'Asignacion profesor-grupo-materia-periodo del mismo anio';
COMMENT ON TABLE  CALIFICACIONES         IS 'Notas 0.0-5.0, evaluaciones 1-10 unicas por estudiante+materia+periodo (espacios compartidos entre co-docentes), ancladas a matricula y asignacion del mismo grupo y anio';
COMMENT ON TABLE  AUDITORIA              IS 'Registro append-only de acciones importantes';
