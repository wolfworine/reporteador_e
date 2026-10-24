package com.entel.migration.schema;

/**
 * Nombres de atributo compartidos por los items de {@link TableNames#ROLE_REPOSITORY}
 * y {@link TableNames#ROLE_REPOSITORY_MENU}: las claves de particion/orden,
 * la clave del GSI1, y los atributos denormalizados que necesitan los
 * access patterns 2/3/4/5 (ver docs/architecture.md).
 *
 * <p><b>Corregido 2026-08-19 (coordinacion cross-track backend/database):</b>
 * hasta esta fecha, {@code PK}/{@code SK}/{@code GSI1PK}/{@code GSI1SK}
 * usaban mayusculas y el resto de atributos de {@code ATRIBUTO#}/{@code ROL#}
 * no coincidian en absoluto con los nombres reales que usa el backend
 * (verificado leyendo directamente {@code ../backend/src/main/java/pe/edu/
 * cibertec/proyemp/repository/RoleRepositoryTable.java} y sus repositorios,
 * fuente de verdad real -- el documento canonico de docs/architecture.md
 * nunca fijo la convencion literal de mayusculas/minusculas, cada track
 * eligio la suya por separado y nunca se probo una migracion real contra el
 * backend real hasta hoy). Esta clase ahora es un espejo exacto de las
 * constantes {@code ATTR_*} de {@code RoleRepositoryTable.java} del backend
 * -- si alguna vez diverge de nuevo, {@code MigrateCommand} fallara con un
 * error de DynamoDB "key element does not match schema" igual que ocurrio
 * hoy, es la señal de que hay que re-sincronizar ambos lados.
 */
public final class ItemAttributes {

    /** Clave de particion primaria (ambas tablas). */
    public static final String PK = "pk";

    /** Clave de orden primaria (ambas tablas). */
    public static final String SK = "sk";

    /** Clave de particion de GSI1 (solo {@code RoleRepository}). */
    public static final String GSI1_PK = "gsi1pk";

    /** Clave de orden de GSI1 (solo {@code RoleRepository}). */
    public static final String GSI1_SK = "gsi1sk";

    // --- Catalogos simples (Patron 1) y genericos (id numerico legado) ---
    public static final String ID = "id";
    public static final String TIPO = "tipo";
    public static final String CODIGO = "codigo";
    public static final String NOMBRE = "nombre";
    public static final String VALOR = "valor";

    // --- Rol (fila propia PK=ROL#<id>/SK=METADATA, ver RolRepository.java) ---
    public static final String UBICACION_CODIGO = "ubicacionCodigo";
    public static final String AREA_CODIGO = "areaCodigo";
    public static final String CARGO_CODIGO = "cargoCodigo";
    public static final String DESCRIPCION = "descripcion";
    public static final String ROL_TIPO = "rolTipo";
    public static final String REGLA_NEGOCIO = "reglaNegocio";
    public static final String AMBITO = "ambito";
    public static final String PROPIETARIO_ROL = "propietarioRol";
    public static final String PROPIETARIO_PERSONA = "propietarioPersona";
    public static final String SITUACION = "situacion";
    public static final String ROL_BASE = "rolBase";

    // --- Suministro (Patron 2b, PK=ROL#<idRol>/SK=SUMINISTRO#<id>) ---
    public static final String ROL_ID = "rolId";
    public static final String PRIORIDAD = "prioridad";

    // --- Atributo (Patrones 2/3/4, PK=ATRIBUTO#<id>/SK=DETALLE#<idDetalle>#<categoria>) ---
    public static final String DETALLE_ID = "detalleId";
    public static final String CATEGORIA = "categoria";
    public static final String APLICACION_CODIGO = "aplicacionCodigo";
    public static final String CARGO_NOMBRE = "cargoNombre";
    public static final String ROL_NOMBRE = "rolNombre";
    public static final String SUMINISTRO_ID = "suministroId";
    public static final String ATRIBUTO_CODIGO = "atributoCodigo";
    public static final String ATRIBUTO_NOMBRE = "atributoNombre";
    public static final String ESTADO = "estado";

    /**
     * Atributo extra, NO presente en el esquema real del backend -- se
     * mantiene solo porque {@code command/VerifyCommand.java} (feature 5,
     * propia de este track) lo usa para su propio chequeo de integridad
     * referencial de Cargo. Redundante con {@link #CARGO_CODIGO} desde la
     * corrección 2026-08-19 (mismo valor), pero eliminarlo requeriría
     * reescribir VerifyCommand -- fuera de alcance de la corrección del
     * contrato con el backend real. No lo lea ningún código nuevo.
     */
    public static final String ROL_CARGO_ID = "rolCargoId";

    /**
     * Atributo extra, NO presente en el esquema real del backend -- mismo
     * motivo que {@link #ROL_CARGO_ID}. Redundante con {@link #CATEGORIA}.
     */
    public static final String DETALLE_PARAMETRO = "detalleParametro";

    /**
     * Atributo del item de menu ({@code RoleRepositoryMenu}); el filtro
     * por tipo de usuario ocurre en la capa de aplicacion, igual que en el
     * codigo legado real (ver docs/architecture.md).
     */
    public static final String TIPO_USUARIO = "tipoUsuario";

    /**
     * Atributo del item de menu usado por la capa de aplicacion para
     * reconstruir la jerarquia submenu-&gt;item, igual que el codigo legado.
     * Corregido 2026-08-19: era {@code "cod_submenu"} (snake_case), el
     * backend real ({@code MenuTable.ATTR_COD_SUBMENU}) usa camelCase
     * {@code "codSubmenu"} -- sin efecto practico hoy (este dataset no trae
     * tabla {@code menu}), corregido por completitud.
     */
    public static final String COD_SUBMENU = "codSubmenu";

    private ItemAttributes() {
    }
}
