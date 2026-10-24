package pe.edu.cibertec.proyemp.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.edu.cibertec.proyemp.domain.MenuItem;
import pe.edu.cibertec.proyemp.domain.MenuNodo;
import pe.edu.cibertec.proyemp.domain.MenuTipo;
import pe.edu.cibertec.proyemp.domain.TipoUsuario;
import pe.edu.cibertec.proyemp.infra.SnapshotCache;
import pe.edu.cibertec.proyemp.repository.MenuRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Servicio del menú dinámico -- reemplaza
 * {@code MenuController.establecerPermisos()} del legacy, corrigiendo sus
 * dos bugs de origen (evidencia completa en {@code progress/current.md} y
 * en el prompt de esta feature, confirmada por grep sobre el árbol fuente
 * legado real):
 * <ol>
 *   <li><b>Filtro por tipoUsuario asimétrico.</b> El legacy solo filtraba
 *   de verdad los ítems/submenús cuando el usuario en sesión era
 *   {@code "O"} (Operador); para cualquier otro valor (incluido
 *   {@code "A"}, Administrador) el filtro por tipoUsuario se saltaba por
 *   completo. Esta implementación filtra siempre, de forma simétrica, en
 *   {@link pe.edu.cibertec.proyemp.repository.MenuRepository#listarPorTipoYTipoUsuario}.</li>
 *   <li><b>Jerarquía por producto cruzado.</b> El legacy agregaba TODOS los
 *   ítems que pasaban el filtro de tipoUsuario a CADA submenú, sin usar
 *   {@code cod_submenu} para nada (confirmado: ese campo solo aparece en el
 *   getter/setter/{@code toString()} de {@code model.Menu.java}, nunca en
 *   una condición). Esta implementación arma la jerarquía real enlazando
 *   {@code MenuItem.codSubmenu() == MenuItem.codigo()} del submenú padre.</li>
 * </ol>
 */
@ApplicationScoped
public class MenuService {

    private final MenuRepository menuRepository;
    private final SnapshotCache snapshot;

    @Inject
    public MenuService(MenuRepository menuRepository, SnapshotCache snapshot) {
        this.menuRepository = menuRepository;
        this.snapshot = snapshot;
    }

    /**
     * Alta de un submenú o ítem de Menu, solo para poblar datos de prueba
     * -- ningún acceptance criterion de esta feature exige un endpoint de
     * escritura, pero los tests de integración necesitan una forma de crear
     * el escenario sin acceder a DynamoDB directamente. {@code web/} nunca
     * llama a {@code repository/} directamente (regla dura de
     * docs/architecture.md) -- este método existe para que
     * {@code web/MenuResource} pase siempre por esta capa.
     */
    public Uni<MenuItem> crear(MenuItem datos) {
        return menuRepository.crear(datos).invoke(() -> snapshot.invalidar("menu:"));
    }

    /**
     * Árbol de menú para un {@code tipoUsuario} dado: lista de submenús
     * (tipo=SUBMENU) con sus ítems hijos reales (tipo=ITEM cuyo
     * {@code codSubmenu} apunta al {@code codigo} del submenú), ambos ya
     * filtrados por {@code tipoUsuario} y estado activo. Un tipoUsuario sin
     * ningún submenú configurado devuelve una lista vacía, no un error --
     * no es un recurso identificable individualmente, no aplica 404.
     */
    /** Servido desde el snapshot en memoria (ver {@link SnapshotCache}). */
    public Uni<List<MenuNodo>> obtenerArbol(TipoUsuario tipoUsuario) {
        return snapshot.obtener("menu:arbol:" + tipoUsuario, () -> cargarArbol(tipoUsuario));
    }

    private Uni<List<MenuNodo>> cargarArbol(TipoUsuario tipoUsuario) {
        Uni<List<MenuItem>> submenusUni = menuRepository
                .listarPorTipoYTipoUsuario(MenuTipo.SUBMENU, tipoUsuario)
                .collect().asList();
        Uni<List<MenuItem>> itemsUni = menuRepository
                .listarPorTipoYTipoUsuario(MenuTipo.ITEM, tipoUsuario)
                .collect().asList();

        return Uni.combine().all().unis(submenusUni, itemsUni)
                .asTuple()
                .onItem().transform(tuple -> armarArbol(tuple.getItem1(), tuple.getItem2()));
    }

    /**
     * Arma la jerarquía real vía {@code codSubmenu == submenu.codigo()} --
     * ver Javadoc de la clase para el contraste explícito con el bug de
     * producto cruzado del legacy.
     */
    private static List<MenuNodo> armarArbol(List<MenuItem> submenus, List<MenuItem> items) {
        List<MenuNodo> arbol = new ArrayList<>();
        for (MenuItem submenu : submenus) {
            List<MenuNodo> hijos = new ArrayList<>();
            for (MenuItem item : items) {
                if (submenu.codigo().equals(item.codSubmenu())) {
                    hijos.add(new MenuNodo(item.codigo(), item.nombre(), item.action(), List.of()));
                }
            }
            arbol.add(new MenuNodo(submenu.codigo(), submenu.nombre(), submenu.action(), hijos));
        }
        return arbol;
    }
}
