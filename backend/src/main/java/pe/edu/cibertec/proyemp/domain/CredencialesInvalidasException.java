package pe.edu.cibertec.proyemp.domain;

/**
 * Excepción de dominio: el email no existe o el password no coincide con el
 * hash guardado. Deliberadamente **no distingue** cuál de los dos casos
 * ocurrió (mismo mensaje genérico para ambos) -- evitar que el endpoint de
 * login confirme por temporización/mensaje si un email está registrado o no
 * es una mejora de seguridad respecto al legacy, que sí distinguía casos
 * (aunque solo en mensajes de UI JSF, nunca expuesto como API). Mapeada a
 * HTTP 401 en {@code web/exception/UsuarioExceptionMappers.java}.
 */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Credenciales invalidas");
    }
}
