package pe.edu.cibertec.proyemp.domain;

/**
 * Excepción de dominio para un fallo de infraestructura externa (DynamoDB o
 * Kafka no disponibles al momento de atender la request) -- feature 12
 * ({@code reactive_resilience_observability}), acceptance 3: "Los endpoints
 * de reportes y de escritura (POST/PUT) tienen manejo de error explícito que
 * devuelve un código HTTP y mensaje claros ante fallo de DynamoDB/Kafka".
 *
 * <p>Distinta a propósito de un {@code CatalogoNoEncontradoException`}/
 * {@code RolNoEncontradoException} etc. (esas son fallos de <b>negocio</b>,
 * 404/400, el sistema respondió correctamente que el dato no existe) -- esta
 * excepción representa que el sistema **no pudo responder** porque una
 * dependencia externa (DynamoDB/Kafka) no está disponible, mapeada a 503
 * Service Unavailable en {@code web/exception/InfraestructuraExceptionMappers}
 * (distinción deliberada de los 4xx existentes, ver esa clase).
 */
public class InfraestructuraNoDisponibleException extends RuntimeException {

    public InfraestructuraNoDisponibleException(String contexto, Throwable causa) {
        super("Fallo de infraestructura (" + contexto + "): "
                + (causa.getMessage() != null ? causa.getMessage() : causa.getClass().getSimpleName())
                + ". Intente nuevamente en unos segundos.", causa);
    }
}
