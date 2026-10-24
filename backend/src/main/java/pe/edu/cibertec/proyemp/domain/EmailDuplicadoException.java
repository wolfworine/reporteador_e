package pe.edu.cibertec.proyemp.domain;

/**
 * Excepción de dominio: ya existe un {@link Usuario} con ese {@code email}.
 * Equivalente al {@code unique=true} de {@code Usuario.c_email} en el
 * legacy (ahí se detectaba tarde, con un {@code catch (Exception e)} genérico
 * de EclipseLink y el mensaje hardcodeado "Email Duplicado" en
 * {@code UsuarioManagedBean.registrar()}/{@code modificar()}). Aquí se hace
 * cumplir de forma atómica con {@code TransactWriteItems} sobre la fila
 * puntero {@code EMAIL#<email>} -- ver
 * {@code repository/UsuarioRepository.java}. Mapeada a HTTP 409 en
 * {@code web/exception/UsuarioExceptionMappers.java}.
 */
public class EmailDuplicadoException extends RuntimeException {

    private final String email;

    public EmailDuplicadoException(String email) {
        super("Ya existe un usuario con el email '" + email + "'");
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
