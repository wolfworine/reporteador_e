package pe.edu.cibertec.proyemp.web.dto;

import pe.edu.cibertec.proyemp.domain.Rol;

/** Cuerpo de request de {@code POST}/{@code PUT} de {@code /api/roles}. */
public record RolRequest(
        String ubicacionCodigo,
        String areaCodigo,
        String cargoCodigo,
        String nombre,
        String descripcion,
        String tipo,
        String reglaNegocio,
        String ambito,
        String propietarioRol,
        String propietarioPersona,
        String situacion,
        String rolBase) {

    /** Convierte a {@link Rol} de dominio con {@code id} nulo (se asigna al crear/actualizar). */
    public Rol aDominio() {
        return new Rol(null, ubicacionCodigo, areaCodigo, cargoCodigo, nombre, descripcion, tipo, reglaNegocio,
                ambito, propietarioRol, propietarioPersona, situacion, rolBase);
    }
}
