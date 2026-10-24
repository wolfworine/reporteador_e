package pe.edu.cibertec.proyemp.repository;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.edu.cibertec.proyemp.domain.Area;
import pe.edu.cibertec.proyemp.domain.TipoCatalogo;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

/**
 * Repositorio DynamoDB de {@link Area} -- capa única con acceso directo a
 * {@link DynamoDbAsyncClient}, delegando en {@link CatalogoSimpleRepository}
 * para no duplicar la lógica compartida por los 5 catálogos simples.
 */
@ApplicationScoped
public class AreaRepository implements CatalogoRepositorio<Area> {

    private final CatalogoSimpleRepository<Area> delegado;

    @Inject
    public AreaRepository(DynamoDbAsyncClient client) {
        this.delegado = new CatalogoSimpleRepository<>(client, TipoCatalogo.AREA, Area::new);
    }

    @Override
    public Uni<Area> buscarPorCodigo(String codigo) {
        return delegado.buscarPorCodigo(codigo);
    }

    @Override
    public Multi<Area> listarTodos() {
        return delegado.listarTodos();
    }

    @Override
    public Uni<Area> crear(String nombre) {
        return delegado.crear(nombre);
    }

    @Override
    public Uni<Area> actualizar(String codigo, String nombre) {
        return delegado.actualizar(codigo, nombre);
    }

    @Override
    public Uni<Void> eliminar(String codigo) {
        return delegado.eliminar(codigo);
    }
}
