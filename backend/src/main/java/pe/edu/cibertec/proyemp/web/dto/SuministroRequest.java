package pe.edu.cibertec.proyemp.web.dto;

/** Cuerpo de request de {@code POST}/{@code PUT} de {@code /api/roles/{idRol}/suministros}. */
public record SuministroRequest(String nombre, String prioridad) {
}
