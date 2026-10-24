package pe.edu.cibertec.proyemp.domain;

/**
 * Equivalente a {@code model.Usuario.e_tipo} del legacy (1 carácter,
 * {@code "A"} = Administrador, cualquier otro valor = Operador -- ver
 * {@code LoginManagedBean.NombreTipo(String)} y
 * {@code UsuarioManagedBean.NombreTipo(String)}, idénticos en ambos managed
 * beans). Modelado como enum propio en vez de {@code String} de 1 char,
 * mismo criterio que {@link EstadoAtributo}/{@link CategoriaSuministro} de
 * la feature 4.
 */
public enum TipoUsuario {
    ADMINISTRADOR,
    OPERADOR
}
