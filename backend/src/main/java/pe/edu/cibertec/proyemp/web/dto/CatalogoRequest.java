package pe.edu.cibertec.proyemp.web.dto;

/**
 * Cuerpo de request compartido por los 5 endpoints de catálogo simple
 * (POST/PUT) -- todos tienen el mismo shape ({@code nombre}), ya que
 * {@code codigo} nunca lo envía el cliente (se genera en {@code crear}, o
 * viaja en el path en {@code actualizar}).
 */
public record CatalogoRequest(String nombre) {
}
