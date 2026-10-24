package pe.edu.cibertec.proyemp.domain;

/**
 * Excepción de dominio: no existe ningún {@link Suministro} con el
 * {@code id} dado bajo el {@link Rol} dado. Mapeada a HTTP 404 en
 * {@code web/exception/RolExceptionMappers.java}.
 */
public class SuministroNoEncontradoException extends RuntimeException {

    private final String rolId;
    private final String suministroId;

    public SuministroNoEncontradoException(String rolId, String suministroId) {
        super("No se encontro suministro '" + suministroId + "' bajo el rol '" + rolId + "'");
        this.rolId = rolId;
        this.suministroId = suministroId;
    }

    public String getRolId() {
        return rolId;
    }

    public String getSuministroId() {
        return suministroId;
    }
}
