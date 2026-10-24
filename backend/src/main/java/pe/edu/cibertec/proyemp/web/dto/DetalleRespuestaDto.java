package pe.edu.cibertec.proyemp.web.dto;

import pe.edu.cibertec.proyemp.domain.Atributo;

/**
 * Fila de una de las 4 categorías de suministro dentro de
 * {@link DetalleRolRespuestaDto}. Vista de serialización sobre
 * {@link Atributo} (no lo reemplaza) con los nombres de campo que espera
 * {@code ../frontend/src/types/detalle.ts#Detalle} -- coordinación
 * cross-track 2026-08-19 (ver {@code handoff.md} de este track y el de
 * {@code ../frontend/}). Deliberadamente NO incluye {@code parametro}: el
 * frontend confirmó (leyendo el código fuente legacy real) que ese campo
 * era el discriminador de categoría, ya representado estructuralmente por
 * en cuál de las 4 listas paralelas aparece cada ítem -- no hay un valor
 * de {@link Atributo} que corresponda, y fabricar uno sería peor que
 * omitirlo.
 */
public record DetalleRespuestaDto(
        String id,
        String atributoCodigo,
        String atributoNombre) {

    public static DetalleRespuestaDto de(Atributo atributo) {
        return new DetalleRespuestaDto(atributo.id(), atributo.atributoCodigo(), atributo.atributoNombre());
    }
}
