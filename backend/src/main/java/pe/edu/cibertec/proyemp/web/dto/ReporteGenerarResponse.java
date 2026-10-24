package pe.edu.cibertec.proyemp.web.dto;

/**
 * Cuerpo de la respuesta {@code 202 Accepted} de
 * {@code POST /api/reportes/generar} -- únicamente el {@code id} de
 * seguimiento, nunca el binario (acceptance 1 de la feature 9: el endpoint
 * jamás devuelve el reporte generado en la misma respuesta).
 */
public record ReporteGenerarResponse(String id) {
}
