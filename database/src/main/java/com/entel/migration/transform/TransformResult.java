package com.entel.migration.transform;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Map;

/**
 * Resultado completo de transformar un {@link com.entel.migration.dump.ParsedDump}
 * (feature 3) a items DynamoDB del diseno canonico de docs/architecture.md
 * (feature 2), listo para escribir con {@code PutItem}/{@code BatchWriteItem}.
 *
 * <p>Pura estructura de datos - no hace ningun acceso a red. Ver
 * {@link RoleRepositoryTransformer} para la logica de transformacion.
 *
 * @param roleRepositoryItems     items de la tabla {@code RoleRepository}
 *                                (catalogos Patron 1 + Patron 2b + Patrones
 *                                2/3/4 sobre Atributo).
 * @param roleRepositoryMenuItems items de la tabla {@code RoleRepositoryMenu}
 *                                (Patron 5). Siempre vacio para dumps que no
 *                                traen la tabla {@code menu} (ver
 *                                {@link RoleRepositoryTransformer} y
 *                                docs/architecture.md - no es un bug, es el
 *                                resultado correcto para ese dataset).
 * @param stats                   conteos y advertencias de la transformacion,
 *                                para que el subcomando {@code migrate}
 *                                imprima un resumen util.
 */
public record TransformResult(
        List<Map<String, AttributeValue>> roleRepositoryItems,
        List<Map<String, AttributeValue>> roleRepositoryMenuItems,
        Stats stats) {

    /**
     * @param catalogItems              items Patron 1 (area + cargo + ubicacion + aplicacion).
     * @param rolItems                  items {@code ROL#&lt;id&gt;/METADATA} (uno por fila de
     *                                  {@code rol}) -- agregado 2026-08-19, coordinacion
     *                                  cross-track: el backend real (RolRepository.java) tiene
     *                                  su propio repositorio de Rol que esta fila alimenta.
     * @param suministroItems           items Patron 2b (uno por fila de {@code suministro}).
     * @param atributoItems             items Patrones 2/3/4 (uno por fila de {@code atributo}).
     * @param menuItems                 items Patron 5 (uno por fila de {@code menu}, si la tabla existe en el dump).
     * @param unresolvedAplicacionNames cuantos items {@code ATRIBUTO#} no encontraron
     *                                  un catalogo {@code aplicacion} cuyo nombre
     *                                  coincida con {@code atributo.aplicacion}
     *                                  (ver {@link RoleRepositoryTransformer#UNRESOLVED_NAME_PREFIX}).
     * @param unresolvedAreaNames       idem para {@code rol.area} vs {@code area.area} --
     *                                  cuenta tanto items {@code ROL#} como {@code ATRIBUTO#}
     *                                  afectados (2026-08-19: antes solo contaba Atributo).
     * @param unresolvedCargoNames      idem para {@code rol.cargo} vs {@code cargo.nombre}.
     * @param unresolvedRolReferences   cuantos items {@code ATRIBUTO#} referencian
     *                                  un {@code id_rol} que no existe en la tabla
     *                                  {@code rol} de este dump (0 en el dump real
     *                                  de referencia, verificado).
     */
    public record Stats(
            int catalogItems,
            int rolItems,
            int suministroItems,
            int atributoItems,
            int menuItems,
            int unresolvedAplicacionNames,
            int unresolvedAreaNames,
            int unresolvedCargoNames,
            int unresolvedRolReferences) {
    }
}
