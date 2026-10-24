package pe.edu.cibertec.proyemp.domain;

/**
 * Excepción de dominio: no existe ningún {@link Usuario} con el {@code id}
 * dado. Mapeada a HTTP 404 en
 * {@code web/exception/UsuarioExceptionMappers.java}.
 */
public class UsuarioNoEncontradoException extends RuntimeException {

    private final String id;

    public UsuarioNoEncontradoException(String id) {
        super("No se encontro usuario con id '" + id + "'");
        this.id = id;
    }

    public String getUsuarioId() {
        return id;
    }
}
