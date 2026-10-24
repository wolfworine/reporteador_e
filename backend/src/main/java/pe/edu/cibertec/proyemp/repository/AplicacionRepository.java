package pe.edu.cibertec.proyemp.repository;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.edu.cibertec.proyemp.domain.Aplicacion;
import pe.edu.cibertec.proyemp.domain.TipoCatalogo;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

/**
 * Repositorio DynamoDB de {@link Aplicacion}. Ver {@link AreaRepository}
 * para el patrón de delegación compartido por los 5 catálogos simples.
 */
@ApplicationScoped
public class AplicacionRepository implements CatalogoRepositorio<Aplicacion> {

    private final CatalogoSimpleRepository<Aplicacion> delegado;

    @Inject
    public AplicacionRepository(DynamoDbAsyncClient client) {
        this.delegado = new CatalogoSimpleRepository<>(client, TipoCatalogo.APLICACION, Aplicacion::new);
    }

    @Override
    public Uni<Aplicacion> buscarPorCodigo(String codigo) {
        return delegado.buscarPorCodigo(codigo);
    }

    @Override
    public Multi<Aplicacion> listarTodos() {
        return delegado.listarTodos();
    }

    @Override
    public Uni<Aplicacion> crear(String nombre) {
        return delegado.crear(nombre);
    }

    @Override
    public Uni<Aplicacion> actualizar(String codigo, String nombre) {
        return delegado.actualizar(codigo, nombre);
    }

    @Override
    public Uni<Void> eliminar(String codigo) {
        return delegado.eliminar(codigo);
    }
}
