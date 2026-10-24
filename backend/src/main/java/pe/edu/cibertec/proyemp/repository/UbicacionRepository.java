package pe.edu.cibertec.proyemp.repository;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.edu.cibertec.proyemp.domain.TipoCatalogo;
import pe.edu.cibertec.proyemp.domain.Ubicacion;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

/**
 * Repositorio DynamoDB de {@link Ubicacion}. Ver {@link AreaRepository} para
 * el patrón de delegación compartido por los 5 catálogos simples.
 */
@ApplicationScoped
public class UbicacionRepository implements CatalogoRepositorio<Ubicacion> {

    private final CatalogoSimpleRepository<Ubicacion> delegado;

    @Inject
    public UbicacionRepository(DynamoDbAsyncClient client) {
        this.delegado = new CatalogoSimpleRepository<>(client, TipoCatalogo.UBICACION, Ubicacion::new);
    }

    @Override
    public Uni<Ubicacion> buscarPorCodigo(String codigo) {
        return delegado.buscarPorCodigo(codigo);
    }

    @Override
    public Multi<Ubicacion> listarTodos() {
        return delegado.listarTodos();
    }

    @Override
    public Uni<Ubicacion> crear(String nombre) {
        return delegado.crear(nombre);
    }

    @Override
    public Uni<Ubicacion> actualizar(String codigo, String nombre) {
        return delegado.actualizar(codigo, nombre);
    }

    @Override
    public Uni<Void> eliminar(String codigo) {
        return delegado.eliminar(codigo);
    }
}
