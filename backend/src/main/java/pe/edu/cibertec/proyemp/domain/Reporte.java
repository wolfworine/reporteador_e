package pe.edu.cibertec.proyemp.domain;

import java.util.List;

/**
 * Paridad conceptual con {@code model.Reporte} del legacy: consolidado de
 * accesos de un Cargo dentro de un Área, con sus Roles y las 4 categorías
 * de suministro de cada uno ({@link DetalleRol}). Es el contrato funcional
 * central de reportes (ver {@code docs/architecture.md} §"Decisiones a
 * preservar conceptualmente") -- cualquier feature de reportes (8, 9, 10)
 * reproduce esta forma, no inventa una nueva.
 *
 * <p>Ensamblado por {@code service/ReporteService} componiendo los
 * repositorios/servicios ya existentes de las features 3
 * ({@code AreaRepository}/{@code AplicacionRepository}) y 4
 * ({@code AtributoService}) -- sin duplicar su lógica de acceso a datos, y
 * sin el bug de duplicados con {@code HashSet} de
 * {@code ReporteManagedBean.reporte()}/{@code imprimir()} (ver
 * {@code docs/migration-plan.md} §3.5 y el Javadoc de {@code ReporteService}
 * para el detalle de dónde entraban esos duplicados en el legacy y por qué
 * la query nueva no los reproduce).
 */
public record Reporte(
        String areaCodigo,
        String areaNombre,
        String cargoCodigo,
        String cargoNombre,
        List<DetalleRol> detalleRol,
        int cantidadRol,
        int cantidadSuministro) {
}
