package pe.edu.cibertec.proyemp.domain;

/**
 * Estado activo/inactivo de un {@link Atributo}, equivalente simplificado al
 * FK legado {@code Atributo.e_estado} (el JPQL real filtra {@code e.id = 1},
 * es decir "solo activos"). Se modela como literal propio en vez de una FK
 * al catálogo {@code Estado} de la feature 3 porque ese catálogo es
 * genérico (no garantiza semántica activo/inactivo) -- decisión documentada
 * en {@code progress/current.md}.
 */
public enum EstadoAtributo {
    ACTIVO,
    INACTIVO
}
