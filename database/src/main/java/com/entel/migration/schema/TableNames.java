package com.entel.migration.schema;

/**
 * Nombres de las tablas DynamoDB del diseno canonico (ver
 * docs/architecture.md, seccion "Arquitectura objetivo: diseno DynamoDB").
 */
public final class TableNames {

    /**
     * Tabla single-table que cubre catalogos simples (Patron 1), la
     * relacion Rol-&gt;Suministro (Patron 2b) y, via GSI1, los patrones
     * 2/3/4 sobre Atributo.
     */
    public static final String ROLE_REPOSITORY = "RoleRepository";

    /** Tabla dedicada al menu por tipo S/I (Patron 5). */
    public static final String ROLE_REPOSITORY_MENU = "RoleRepositoryMenu";

    private TableNames() {
    }
}
