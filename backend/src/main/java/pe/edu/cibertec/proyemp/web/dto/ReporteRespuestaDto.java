package pe.edu.cibertec.proyemp.web.dto;

import java.util.List;
import pe.edu.cibertec.proyemp.domain.Reporte;

/**
 * Respuesta JSON de {@code GET /api/reportes/{id}}. Vista de serialización
 * sobre {@link Reporte} (no lo reemplaza -- el dominio de las features 7/9
 * queda intacto) con los nombres de campo que espera
 * {@code ../frontend/src/types/reporte.ts#Reporte}: coordinación
 * cross-track 2026-08-19 explícita entre este track y {@code ../frontend/}
 * (ver {@code handoff.md} de ambos, y el diagnóstico campo a campo en
 * {@code progress/review.md} de la feature 10). Resuelve el gap que la
 * review de la feature 10 dejó abierto como no bloqueante: antes de esta
 * clase, {@code ReporteResource#obtener} serializaba {@link Reporte} crudo
 * (campos {@code areaCodigo}/{@code areaNombre}/{@code cargoCodigo}/
 * {@code cargoNombre}), que no coincidía con lo que el frontend ya
 * consumía.
 */
public record ReporteRespuestaDto(
        String cargo,
        String area,
        String codigoArea,
        String codigoCargo,
        List<DetalleRolRespuestaDto> detalleRol,
        int cantidadRol,
        int cantidadSuministro) {

    public static ReporteRespuestaDto de(Reporte reporte) {
        return new ReporteRespuestaDto(
                reporte.cargoNombre(),
                reporte.areaNombre(),
                reporte.areaCodigo(),
                reporte.cargoCodigo(),
                reporte.detalleRol().stream().map(DetalleRolRespuestaDto::de).toList(),
                reporte.cantidadRol(),
                reporte.cantidadSuministro());
    }

    public static List<ReporteRespuestaDto> de(List<Reporte> reportes) {
        return reportes.stream().map(ReporteRespuestaDto::de).toList();
    }
}
