package pe.edu.cibertec.proyemp.repository;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.edu.cibertec.proyemp.domain.Estado;
import pe.edu.cibertec.proyemp.domain.TipoCatalogo;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

/**
 * Repositorio DynamoDB de {@link Estado}. Ver {@link AreaRepository} para el
 * patrón de delegación compartido por los 5 catálogos simples.
 */
@ApplicationScoped
public class EstadoRepository implements CatalogoRepositorio<Estado> {

    private final CatalogoSimpleRepository<Estado> delegado;

    @Inject
    public EstadoRepository(DynamoDbAsyncClient client) {
        this.delegado = new CatalogoSimpleRepository<>(client, TipoCatalogo.ESTADO, Estado::new);
    }

    @Override
    public Uni<Estado> buscarPorCodigo(String codigo) {
        return delegado.buscarPorCodigo(codigo);
    }

    @Override
    public Multi<Estado> listarTodos() {
        return delegado.listarTodos();
    }

    @Override
    public Uni<Estado> crear(String nombre) {
        return delegado.crear(nombre);
    }

    @Override
    public Uni<Estado> actualizar(String codigo, String nombre) {
        return delegado.actualizar(codigo, nombre);
    }

    @Override
    public Uni<Void> eliminar(String codigo) {
        return delegado.eliminar(codigo);
    }
}
