package pe.edu.cibertec.proyemp.web.dto;

import pe.edu.cibertec.proyemp.domain.EstadoMenu;
import pe.edu.cibertec.proyemp.domain.MenuItem;
import pe.edu.cibertec.proyemp.domain.MenuTipo;
import pe.edu.cibertec.proyemp.domain.TipoUsuario;

/**
 * Cuerpo de request de {@code POST /api/menu}, usado exclusivamente para
 * poblar datos de prueba (ningún acceptance de la feature exige alta vía
 * API) -- mismo precedente que {@code POST /api/atributos} de la feature 4.
 */
public record MenuRequest(
        String nombre,
        MenuTipo tipo,
        TipoUsuario tipoUsuario,
        String codSubmenu,
        EstadoMenu estado,
        String action) {

    /** Convierte a {@link MenuItem} de dominio con {@code codigo} nulo (se asigna al crear). */
    public MenuItem aDominio() {
        return new MenuItem(null, nombre, tipo, tipoUsuario, codSubmenu, estado, action);
    }
}
