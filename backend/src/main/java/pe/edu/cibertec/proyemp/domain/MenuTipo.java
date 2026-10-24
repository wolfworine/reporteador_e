package pe.edu.cibertec.proyemp.domain;

/**
 * Tipo de fila de {@code Menu} (legacy {@code model.Menu.tipo}, 1 carácter):
 * {@code "S"} = submenú (nodo padre), {@code "I"} = ítem (nodo hijo) -- ver
 * {@code MenuController.init()} del legacy ({@code findTipo("S")},
 * {@code findTipo("I")}). Modelado como enum propio en vez de {@code String}
 * de 1 char, mismo criterio que {@link CategoriaSuministro}/
 * {@link EstadoAtributo}/{@link TipoUsuario}.
 *
 * <p>{@link #codigoLegado()} expone el valor de un carácter usado como parte
 * literal de la PK {@code MENU#<tipo>} del esquema canónico
 * ({@code ../database/docs/architecture.md}, Patrón 5).
 */
public enum MenuTipo {
    SUBMENU("S"),
    ITEM("I");

    private final String codigoLegado;

    MenuTipo(String codigoLegado) {
        this.codigoLegado = codigoLegado;
    }

    public String codigoLegado() {
        return codigoLegado;
    }
}
