package pe.edu.cibertec.proyemp.web.dto;

/** Cuerpo de request de {@code POST /api/atributos} (alta usada para poblar datos de prueba). */
public record AtributoRequest(
        String aplicacionCodigo,
        String rolId,
        String suministroId,
        String categoria,
        String atributoCodigo,
        String atributoNombre,
        String estado) {
}
