package pe.edu.cibertec.proyemp.domain;

/**
 * Excepción de dominio para un catálogo simple (Área/Cargo/Ubicación/
 * Aplicación/Estado) no encontrado por código. Mapeada a HTTP 404 en
 * {@code web/exception/CatalogoExceptionMappers.java} vía
 * {@code @ServerExceptionMapper} (docs/conventions.md prohíbe devolver
 * null/vacío en silencio ante un fallo real).
 */
public class CatalogoNoEncontradoException extends RuntimeException {

    private final TipoCatalogo tipo;
    private final String codigo;

    public CatalogoNoEncontradoException(TipoCatalogo tipo, String codigo) {
        super("No se encontro " + tipo.name().toLowerCase() + " con codigo '" + codigo + "'");
        this.tipo = tipo;
        this.codigo = codigo;
    }

    public TipoCatalogo getTipo() {
        return tipo;
    }

    public String getCodigo() {
        return codigo;
    }
}
