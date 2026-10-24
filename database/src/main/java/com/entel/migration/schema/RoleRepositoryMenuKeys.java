package com.entel.migration.schema;

/**
 * Constructores de PK/SK de la tabla {@link TableNames#ROLE_REPOSITORY_MENU}
 * (Patron 5: {@code MenuRepository.findTipo(tipo)}), segun el diseno
 * canonico de docs/architecture.md.
 *
 * <p>La PK indexa por {@code Menu.tipo} (no por {@code tipoUsuario}): el
 * filtro por tipo de usuario y la jerarquia por {@code cod_submenu} ocurren
 * en la capa de aplicacion, igual que en el codigo legado real.
 */
public final class RoleRepositoryMenuKeys {

    private RoleRepositoryMenuKeys() {
    }

    /**
     * PK de un menu por tipo.
     *
     * @param tipo {@code "S"} (submenu) o {@code "I"} (item), tal como
     *             {@code Menu.tipo} en el codigo legado.
     */
    public static String menuPk(String tipo) {
        return "MENU#" + tipo;
    }

    /** SK de una entrada de menu, ordenada por su codigo legado. */
    public static String ordenSk(String codigo) {
        return "ORDEN#" + codigo;
    }
}
