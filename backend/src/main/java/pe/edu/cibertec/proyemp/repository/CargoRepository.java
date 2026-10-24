package pe.edu.cibertec.proyemp.repository;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.edu.cibertec.proyemp.domain.Cargo;
import pe.edu.cibertec.proyemp.domain.TipoCatalogo;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

/**
 * Repositorio DynamoDB de {@link Cargo}. Ver {@link AreaRepository} para el
 * patrón de delegación compartido por los 5 catálogos simples.
 */
@ApplicationScoped
public class CargoRepository implements CatalogoRepositorio<Cargo> {

    private final CatalogoSimpleRepository<Cargo> delegado;

    @Inject
    public CargoRepository(DynamoDbAsyncClient client) {
        this.delegado = new CatalogoSimpleRepository<>(client, TipoCatalogo.CARGO, Cargo::new);
    }

    @Override
    public Uni<Cargo> buscarPorCodigo(String codigo) {
        return delegado.buscarPorCodigo(codigo);
    }

    @Override
    public Multi<Cargo> listarTodos() {
        return delegado.listarTodos();
    }

    @Override
    public Uni<Cargo> crear(String nombre) {
        return delegado.crear(nombre);
    }

    @Override
    public Uni<Cargo> actualizar(String codigo, String nombre) {
        return delegado.actualizar(codigo, nombre);
    }

    @Override
    public Uni<Void> eliminar(String codigo) {
        return delegado.eliminar(codigo);
    }
}
