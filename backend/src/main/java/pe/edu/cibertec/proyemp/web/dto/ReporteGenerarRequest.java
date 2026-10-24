package pe.edu.cibertec.proyemp.web.dto;

/**
 * Cuerpo de {@code POST /api/reportes/generar} (feature 9). {@code formato}
 * es {@code "pdf"} o {@code "excel"} (case-insensitive) -- decisión de
 * diseño documentada en {@code progress/current.md}: el cliente pide un
 * solo formato por vez, el worker solo genera ese uno, evitando
 * trabajo/almacenamiento duplicado que nadie pidió.
 */
public record ReporteGenerarRequest(String idAplicacion, String idArea, String formato) {

    private static final java.util.Set<String> FORMATOS_VALIDOS = java.util.Set.of("pdf", "excel");

    /** Normaliza y valida {@code formato} -- {@code IllegalArgumentException} -> 400 (mapper ya global). */
    public String formatoNormalizado() {
        if (formato == null || !FORMATOS_VALIDOS.contains(formato.toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("formato debe ser 'pdf' o 'excel', recibido: '" + formato + "'");
        }
        return formato.toLowerCase(java.util.Locale.ROOT);
    }
}
