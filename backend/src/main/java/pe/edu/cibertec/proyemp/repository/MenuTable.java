package pe.edu.cibertec.proyemp.repository;

import pe.edu.cibertec.proyemp.domain.MenuTipo;

/**
 * Constantes de la tabla {@code RoleRepositoryMenu} (Patrón 5 del esquema
 * canónico, fuente de verdad en {@code ../database/docs/architecture.md}).
 *
 * <p>A diferencia de los catálogos/Rol/Suministro/Atributo/Usuario (que
 * comparten la tabla única {@code RoleRepository}), el documento canónico es
 * explícito en que Menu vive en una tabla **separada**:
 * {@code PK=MENU#<tipo>} ({@code tipo}="S"|"I"), {@code SK=ORDEN#<codigo>},
 * sin GSI -- {@code tipoUsuario}, {@code cod_submenu} y {@code estado} se
 * guardan como atributos del ítem y se filtran/enlazan en la capa de
 * aplicación (ver {@code MenuRepository}/{@code service/MenuService}),
 * igual que hace hoy el código legado.
 */
public final class MenuTable {

    public static final String TABLE_NAME = "RoleRepositoryMenu";

    public static final String ATTR_PK = "pk";
    public static final String ATTR_SK = "sk";
    public static final String ATTR_CODIGO = "codigo";
    public static final String ATTR_NOMBRE = "nombre";
    public static final String ATTR_TIPO = "tipo";
    public static final String ATTR_TIPO_USUARIO = "tipoUsuario";
    public static final String ATTR_COD_SUBMENU = "codSubmenu";
    public static final String ATTR_ESTADO = "estado";
    public static final String ATTR_ACTION = "action";
    public static final String ATTR_VALOR = "valor";

    public static final String SK_METADATA = "METADATA";
    public static final String SK_ORDEN_PREFIX = "ORDEN#";

    private static final String COUNTER_PK = "COUNTER#MENU";

    private MenuTable() {
    }

    /** PK de un tipo de Menu (Patrón 5): {@code MENU#<tipo>} ({@code tipo}="S"|"I"). */
    public static String menuPk(MenuTipo tipo) {
        return "MENU#" + tipo.codigoLegado();
    }

    /** SK de un ítem de Menu dentro de su tipo: {@code ORDEN#<codigo>}. */
    public static String ordenSk(String codigo) {
        return SK_ORDEN_PREFIX + codigo;
    }

    /** PK del ítem contador atómico usado para generar el siguiente código de altas nuevas. */
    public static String counterPk() {
        return COUNTER_PK;
    }
}
