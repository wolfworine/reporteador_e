package pe.edu.cibertec.proyemp.domain;

import java.util.List;

/**
 * Nodo del árbol de menú devuelto por {@code GET /api/menu/{tipoUsuario}}.
 * Recursivo para no cerrar la puerta a más de 2 niveles en el futuro, aunque
 * el modelo real ({@code model.Menu}) solo tiene S(padre)/I(hijo) -- ningún
 * ítem legado tiene hijos propios, así que {@code hijos()} de un nodo de
 * tipo ítem siempre es una lista vacía.
 *
 * <p>Reemplaza el producto cruzado del bug de origen (ver
 * {@code MenuController.establecerPermisos()} del legacy, documentado en
 * {@code progress/current.md}): cada nodo hijo aquí es un ítem real cuyo
 * {@code MenuItem.codSubmenu()} coincide con el {@code codigo} de este
 * nodo, nunca todos los ítems del tipoUsuario.
 */
public record MenuNodo(String codigo, String nombre, String action, List<MenuNodo> hijos) {
}
