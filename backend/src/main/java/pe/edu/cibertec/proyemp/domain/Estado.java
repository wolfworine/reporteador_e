package pe.edu.cibertec.proyemp.domain;

/**
 * Catálogo simple Estado. El legacy ({@code model/Estado.java}) no tiene
 * managed bean propio (solo {@code repository}/{@code service}, usado como
 * catálogo de apoyo para filtrar {@code Atributo} por {@code estado.id=1}
 * activo) y ya tenía un único campo identificador ({@code id}, Long
 * autogenerado) sin el split codigo/id de negocio que sí tienen Area/Cargo/
 * Ubicacion/Aplicacion -- encaja de forma natural con el shape
 * {@code (codigo, nombre)} usado aquí para los 5 catálogos.
 */
public record Estado(String codigo, String nombre) {
}
