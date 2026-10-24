package com.entel.migration.schema;

/**
 * Constructores de PK/SK/GSI1 de la tabla {@link TableNames#ROLE_REPOSITORY},
 * segun el diseno canonico de docs/architecture.md.
 *
 * <p>Los IDs se reciben como {@code String} porque, por decision de diseno
 * (ver docs/architecture.md, seccion "Generacion de IDs"), los datos
 * migrados del dump legado preservan su ID numerico original tal cual como
 * parte literal de la clave (idempotencia: misma PK/SK en cada corrida).
 */
public final class RoleRepositoryKeys {

    /** SK fijo de los items de catalogo simple (Patron 1). */
    public static final String CATALOG_SK = "METADATA";

    /** Nombre del (unico) GSI de esta tabla. */
    public static final String GSI1_NAME = "GSI1";

    private RoleRepositoryKeys() {
    }

    /**
     * PK de un catalogo simple (Area/Cargo/Ubicacion/Aplicacion/Estado)
     * identificado por codigo (Patron 1: Get directo).
     *
     * @param tipo   discriminador del catalogo, ej. {@code "AREA"}, {@code "CARGO"},
     *               {@code "UBICACION"}, {@code "APLICACION"}, {@code "ESTADO"}.
     * @param codigo codigo/id legado de la fila de catalogo.
     */
    public static String catalogPk(String tipo, String codigo) {
        return "ENTITY#" + tipo + "#" + codigo;
    }

    /** PK de un Rol (usado por el Patron 2b y como parte de GSI1SK). */
    public static String rolPk(String idRol) {
        return "ROL#" + idRol;
    }

    /** SK de un Suministro bajo un Rol (Patron 2b: {@code SuministroRepository.findSuministro}). */
    public static String suministroSk(String idSuministro) {
        return "SUMINISTRO#" + idSuministro;
    }

    /** PK de un Atributo (raiz de los patrones 2/3/4 via GSI1). */
    public static String atributoPk(String idAtributo) {
        return "ATRIBUTO#" + idAtributo;
    }

    /**
     * SK de Detalle bajo un Atributo. {@code categoria} es el valor literal
     * de {@code Detalle.d_parametro} (p.ej. {@code "ROLES"}, {@code
     * "GRUPOS"}, {@code "INBOX"}, {@code "DEFAULTINBOX"}) - ver
     * docs/architecture.md sobre por que esto no modela la entidad
     * {@code Suministro}.
     */
    public static String detalleSk(String idDetalleAtributo, String categoria) {
        return "DETALLE#" + idDetalleAtributo + "#" + categoria;
    }

    /**
     * GSI1PK compartido por los patrones 2, 3 y 4: filtra por Aplicacion +
     * Area (nunca por cargo en la propia PK del indice).
     */
    public static String gsi1Pk(String idAplicacion, String idArea) {
        return "APLICACION#" + idAplicacion + "#AREA#" + idArea;
    }

    /**
     * GSI1SK completo: Cargo + Rol. Se usa tal cual solo cuando se necesita
     * unicidad exacta; los patrones 2/3 consultan solo por GSI1PK (sin
     * condicion de SK) y el patron 4 usa {@link #gsi1SkCargoPrefix}.
     */
    public static String gsi1Sk(String idCargo, String idRol) {
        return gsi1SkCargoPrefix(idCargo) + "#ROL#" + idRol;
    }

    /**
     * Prefijo de GSI1SK usado por el Patron 4
     * ({@code AtributoRepository.findAppArea2}) con
     * {@code begins_with(GSI1SK, gsi1SkCargoPrefix(idCargo))}.
     */
    public static String gsi1SkCargoPrefix(String idCargo) {
        return "CARGO#" + idCargo;
    }
}
