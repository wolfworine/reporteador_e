package pe.edu.cibertec.proyemp.domain;

import java.util.List;

/**
 * Paridad conceptual con {@code model.DetalleRol} del legacy: un Rol dentro
 * de un {@link Reporte}, con sus 4 categorías de suministro
 * (Roles/Grupos/Inbox/DefaultInbox). Reutiliza {@link Atributo} (fila
 * combinada Atributo+Detalle ya definida en la feature 4) para cada
 * categoría en vez de reinventar el {@code model.Detalle} del legacy, y
 * {@link CategoriaSuministro} (enum) en vez del {@code ArrayList<String>
 * tiporol} de texto libre del legacy.
 *
 * <p>{@code tipo} es la traducción de {@code RolResumen.tipo()} (valor
 * crudo persistido, ej. {@code "DINAMICO"}/{@code "NORMAL"}) con la misma
 * regla que {@code ReporteManagedBean.TipoRol(String)}: {@code "DINAMICO"}
 * -&gt; {@code "BASE"}, cualquier otro valor -&gt; {@code "ADICIONAL"}.
 *
 * <p>No se migra el campo {@code cantidadSuministros} que tenía
 * {@code model.DetalleRol} del legacy además de {@code cantidadSuministroRol}
 * -- estaba declarado con getter/setter pero nunca se poblaba en
 * {@code ReporteManagedBean.reporte()} (dead code), así que aquí solo queda
 * {@code cantidadSuministroRol} (suma de las 4 listas de esta categoría).
 */
public record DetalleRol(
        String idRol,
        String rol,
        String tipo,
        String aplicacion,
        List<CategoriaSuministro> tiposConDatos,
        List<Atributo> suministroRoles,
        List<Atributo> suministroGrupos,
        List<Atributo> suministroInbox,
        List<Atributo> suministroDefaultInbox,
        int cantidadSuministroRol) {
}
