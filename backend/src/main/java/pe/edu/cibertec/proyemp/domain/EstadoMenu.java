package pe.edu.cibertec.proyemp.domain;

/**
 * Estado activo/inactivo de un {@link MenuItem} (legacy
 * {@code model.Menu.estado}, {@code Integer}). Se modela como enum propio en
 * vez de {@code Integer} o una FK al catálogo {@code Estado} genérico de la
 * feature 3 -- mismo criterio y misma razón que {@link EstadoAtributo}: ese
 * catálogo no garantiza semántica activo/inactivo.
 */
public enum EstadoMenu {
    ACTIVO,
    INACTIVO
}
