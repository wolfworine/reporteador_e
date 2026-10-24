package pe.edu.cibertec.proyemp.domain;

/**
 * Fila combinada Atributo+Detalle (paridad conceptual con
 * {@code model.Atributo} + {@code model.Detalle} del legacy, fusionados en
 * un solo ítem porque DynamoDB no hace joins). Persistido bajo
 * {@code PK=ATRIBUTO#<id>}, {@code SK=DETALLE#<idDetalleAtributo>#<categoria>},
 * con {@code GSI1PK=APLICACION#<aplicacionCodigo>#AREA#<areaCodigo>} /
 * {@code GSI1SK=CARGO#<cargoCodigo>#ROL#<rolId>} -- Patrones 2, 3 y 4 del
 * esquema canónico.
 *
 * <p>{@code areaCodigo}, {@code cargoCodigo}, {@code rolTipo} y
 * {@code rolNombre}/{@code cargoNombre} están denormalizados desde el
 * {@link Rol} y {@link pe.edu.cibertec.proyemp.domain.Cargo} referenciados
 * -- el documento canónico ya pide denormalizar {@code rolTipo} para
 * ordenar sin lectura adicional (paridad con
 * {@code order by r.c_tipo, d.d_parametro desc}); esta feature extiende el
 * mismo criterio a {@code rolNombre}/{@code cargoNombre} para poder armar
 * la respuesta agrupada de {@code GET /api/roles/cargos} con una sola
 * Query, sin N+1 lecturas.
 */
public record Atributo(
        String id,
        String idDetalleAtributo,
        CategoriaSuministro categoria,
        String aplicacionCodigo,
        String areaCodigo,
        String cargoCodigo,
        String cargoNombre,
        String rolId,
        String rolTipo,
        String rolNombre,
        String suministroId,
        String atributoCodigo,
        String atributoNombre,
        EstadoAtributo estado) {
}
