package pe.edu.cibertec.proyemp.web.dto;

/** Cuerpo de request de {@code POST /api/auth/login}. */
public record LoginRequest(String email, String password) {
}
