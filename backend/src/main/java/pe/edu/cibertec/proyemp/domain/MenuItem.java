package pe.edu.cibertec.proyemp.domain;

/**
 * Ítem de Menu (paridad de campos con {@code model.Menu} del legacy:
 * {@code codigo, nombre, tipo, tipoUsuario, cod_submenu -> codSubmenu,
 * estado, action}).
 *
 * <p>{@code codSubmenu} es el {@code codigo} del {@link MenuItem} con
 * {@code tipo=SUBMENU} que es padre de este ítem cuando {@code tipo=ITEM}
 * ({@code null} para submenús raíz y para ítems sin padre asignado). A
 * diferencia del legacy ({@code managedbean/MenuController.
 * establecerPermisos()}, ver {@code progress/current.md} para el detalle
 * completo del bug de origen), {@code codSubmenu} SÍ se usa para enlazar la
 * jerarquía real -- ver {@code service/MenuService}.
 *
 * <p>{@code tipoUsuario} reutiliza el enum {@link TipoUsuario} de
 * {@code Usuario} (feature 5, mismos valores legacy {@code "O"}/{@code "A"})
 * en vez de duplicar un tipo propio.
 */
public record MenuItem(
        String codigo,
        String nombre,
        MenuTipo tipo,
        TipoUsuario tipoUsuario,
        String codSubmenu,
        EstadoMenu estado,
        String action) {
}
