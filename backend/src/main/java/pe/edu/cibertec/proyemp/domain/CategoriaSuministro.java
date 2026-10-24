package pe.edu.cibertec.proyemp.domain;

/**
 * Las 4 sub-vistas de Detalle del legacy (paginas/&#42;/grupos.xhtml,
 * inbox.xhtml, defaultinbox.xhtml, y la vista base de "roles"), confirmadas
 * en {@code ReporteManagedBean.java} como los valores literales reales de
 * {@code Detalle.d_parametro} -- plurales, no {@code ROL}/{@code GRUPO}
 * como abrevia el texto de {@code feature_list.json}. Es también el valor
 * literal de {@code categoria} en la SK {@code DETALLE#<id>#<categoria>}
 * del esquema canónico.
 */
public enum CategoriaSuministro {
    ROLES,
    GRUPOS,
    INBOX,
    DEFAULTINBOX
}
