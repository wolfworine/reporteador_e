package pe.edu.cibertec.proyemp.domain;

/**
 * Excepción de dominio: no existe ningún {@link Rol} con el {@code id}
 * dado. Mapeada a HTTP 404 en {@code web/exception/RolExceptionMappers.java}.
 */
public class RolNoEncontradoException extends RuntimeException {

    private final String id;

    public RolNoEncontradoException(String id) {
        super("No se encontro rol con id '" + id + "'");
        this.id = id;
    }

    public String getRolId() {
        return id;
    }
}
