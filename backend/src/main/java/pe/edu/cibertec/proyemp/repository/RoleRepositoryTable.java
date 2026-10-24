package pe.edu.cibertec.proyemp.repository;

import java.util.Locale;

/**
 * Constantes de la tabla única {@code RoleRepository} (esquema canónico,
 * fuente de verdad en {@code ../database/docs/architecture.md} y su
 * {@code RoleRepositoryKeys.java}). Esta feature (catálogos simples) solo
 * ejercita el patrón 1 (PK {@code ENTITY#<tipo>#<codigo>} / SK
 * {@code METADATA}); los demás patrones de esa tabla (ROL#/SUMINISTRO#,
 * ATRIBUTO#/DETALLE#, GSI1) son de las features 4 y 6, no se implementan
 * aquí -- pero la tabla se crea con el esquema físico completo (ver
 * {@link RoleRepositoryTableInitializer}) para que esas features no tengan
 * que alterar una tabla ya en uso.
 *
 * <p>Convención de nombres de atributo elegida por esta feature (no
 * especificada más allá de PK/SK en el documento canónico): minúsculas,
 * genéricas de tabla única, igual que {@code pk} en
 * {@code infra/DynamoDbConnectivityTest} de la feature 2.
 */
public final class RoleRepositoryTable {

    public static final String TABLE_NAME = "RoleRepository";

    public static final String ATTR_PK = "pk";
    public static final String ATTR_SK = "sk";
    public static final String ATTR_GSI1PK = "gsi1pk";
    public static final String ATTR_GSI1SK = "gsi1sk";
    public static final String ATTR_TIPO = "tipo";
    public static final String ATTR_CODIGO = "codigo";
    public static final String ATTR_NOMBRE = "nombre";
    public static final String ATTR_VALOR = "valor";

    // Atributos propios de la feature 4 (Rol/Suministro/Atributo/Detalle).
    public static final String ATTR_ID = "id";
    public static final String ATTR_UBICACION_CODIGO = "ubicacionCodigo";
    public static final String ATTR_AREA_CODIGO = "areaCodigo";
    public static final String ATTR_CARGO_CODIGO = "cargoCodigo";
    public static final String ATTR_CARGO_NOMBRE = "cargoNombre";
    public static final String ATTR_DESCRIPCION = "descripcion";
    public static final String ATTR_ROL_TIPO = "rolTipo";
    public static final String ATTR_REGLA_NEGOCIO = "reglaNegocio";
    public static final String ATTR_AMBITO = "ambito";
    public static final String ATTR_PROPIETARIO_ROL = "propietarioRol";
    public static final String ATTR_PROPIETARIO_PERSONA = "propietarioPersona";
    public static final String ATTR_SITUACION = "situacion";
    public static final String ATTR_ROL_BASE = "rolBase";
    public static final String ATTR_ROL_ID = "rolId";
    public static final String ATTR_ROL_NOMBRE = "rolNombre";
    public static final String ATTR_PRIORIDAD = "prioridad";
    public static final String ATTR_SUMINISTRO_ID = "suministroId";
    public static final String ATTR_APLICACION_CODIGO = "aplicacionCodigo";
    public static final String ATTR_CATEGORIA = "categoria";
    public static final String ATTR_ATRIBUTO_CODIGO = "atributoCodigo";
    public static final String ATTR_ATRIBUTO_NOMBRE = "atributoNombre";
    public static final String ATTR_DETALLE_ID = "detalleId";
    public static final String ATTR_ESTADO = "estado";

    // Atributos propios de la feature 5 (Usuario/Login/hash de contraseñas).
    public static final String ATTR_NOMBRES = "nombres";
    public static final String ATTR_APELLIDOS = "apellidos";
    public static final String ATTR_EMAIL = "email";
    public static final String ATTR_PASSWORD_HASH = "passwordHash";
    public static final String ATTR_TIPO_USUARIO = "tipoUsuario";

    // Atributos propios de la feature 9 (reportes_generacion_async_kafka):
    // ejecución asíncrona de generación de reporte, fila PK=REPORTE#<id>/
    // SK=METADATA -- ver domain/ReporteEjecucion.java y
    // repository/ReporteEjecucionRepository.java.
    public static final String ATTR_REPORTE_ESTADO = "estado";
    public static final String ATTR_REPORTE_FORMATO = "formato";
    public static final String ATTR_REPORTE_ID_APLICACION = "idAplicacion";
    public static final String ATTR_REPORTE_ID_AREA = "idArea";
    public static final String ATTR_REPORTE_BINARIO = "binario";
    public static final String ATTR_REPORTE_JSON = "reporteJson";
    public static final String ATTR_REPORTE_MOTIVO_ERROR = "motivoError";
    public static final String ATTR_REPORTE_INTENTOS = "intentos";
    public static final String ATTR_REPORTE_CREADO_EN = "creadoEn";
    public static final String ATTR_REPORTE_ACTUALIZADO_EN = "actualizadoEn";

    public static final String GSI1_NAME = "GSI1";
    public static final String SK_METADATA = "METADATA";
    public static final String SK_SUMINISTRO_PREFIX = "SUMINISTRO#";
    public static final String SK_DETALLE_PREFIX = "DETALLE#";

    private RoleRepositoryTable() {
    }

    /** PK de un catálogo simple (Patrón 1): {@code ENTITY#<tipo>#<codigo>}. */
    public static String catalogPk(String tipo, String codigo) {
        return "ENTITY#" + tipo + "#" + codigo;
    }

    /** Prefijo de PK usado para listar todos los ítems de un tipo (Scan + begins_with). */
    public static String catalogPkPrefix(String tipo) {
        return "ENTITY#" + tipo + "#";
    }

    /** PK del ítem contador atómico usado para generar el siguiente código numérico de altas nuevas. */
    public static String counterPk(String tipo) {
        return "COUNTER#" + tipo;
    }

    /**
     * PK de un Rol -- usada tanto por su propia fila {@code METADATA}
     * (alta/lectura de Rol, extensión documentada en
     * {@code progress/current.md}) como por las filas
     * {@code SUMINISTRO#<id>} de sus Suministros (Patrón 2b).
     */
    public static String rolPk(String idRol) {
        return "ROL#" + idRol;
    }

    /** SK de un Suministro bajo un Rol (Patrón 2b). */
    public static String suministroSk(String idSuministro) {
        return SK_SUMINISTRO_PREFIX + idSuministro;
    }

    /** PK de un Atributo (raíz de los patrones 2/3/4 vía GSI1). */
    public static String atributoPk(String idAtributo) {
        return "ATRIBUTO#" + idAtributo;
    }

    /** SK de Detalle bajo un Atributo: {@code DETALLE#<idDetalleAtributo>#<categoria>}. */
    public static String detalleSk(String idDetalleAtributo, String categoria) {
        return SK_DETALLE_PREFIX + idDetalleAtributo + "#" + categoria;
    }

    /** GSI1PK compartido por los patrones 2, 3 y 4: Aplicación + Área. */
    public static String gsi1PkAplicacionArea(String idAplicacion, String idArea) {
        return "APLICACION#" + idAplicacion + "#AREA#" + idArea;
    }

    /** GSI1SK completo: Cargo + Rol. */
    public static String gsi1SkCargoRol(String idCargo, String idRol) {
        return gsi1SkCargoPrefix(idCargo) + "#ROL#" + idRol;
    }

    /** Prefijo de GSI1SK usado por el Patrón 4 con {@code begins_with}. */
    public static String gsi1SkCargoPrefix(String idCargo) {
        return "CARGO#" + idCargo;
    }

    /**
     * PK de la fila principal de un {@code Usuario} (feature 5), extensión
     * del mismo esquema de fila {@code METADATA} usada por Rol/catálogos --
     * no requiere alterar {@link RoleRepositoryTableInitializer} porque
     * {@code pk}/{@code sk} son atributos string genéricos ya declarados en
     * el esquema físico de la tabla. Decisión documentada en
     * {@code progress/current.md}.
     */
    public static String usuarioPk(String idUsuario) {
        return "USUARIO#" + idUsuario;
    }

    /**
     * PK de la fila "puntero" de email -> id de Usuario (feature 5). Existe
     * para que el login (camino caliente, busca por email, no por id) resuelva
     * con un {@code GetItem} directo en vez de un {@code Scan}, y para hacer
     * cumplir con un {@code TransactWriteItems} + {@code attribute_not_exists}
     * la misma unicidad que tenía {@code Usuario.c_email unique=true} en el
     * legacy. El email se normaliza a minúsculas antes de construir la clave
     * (el legacy no lo hacía, decisión nueva para evitar altas duplicadas por
     * capitalización distinta del mismo correo).
     */
    public static String emailPk(String email) {
        return "EMAIL#" + email.toLowerCase(Locale.ROOT);
    }

    /**
     * PK de una {@code ReporteEjecucion} (feature 9). {@code id} es un UUID
     * asignado por {@code web/ReporteGenerarResource} -- no colisiona con
     * los contadores atómicos ({@code counterPk}) usados por otras
     * entidades, y sirve directamente como clave de partición Kafka (ver
     * {@code docs/kafka-reactive-guidance.md}).
     */
    public static String reportePk(String id) {
        return "REPORTE#" + id;
    }
}
