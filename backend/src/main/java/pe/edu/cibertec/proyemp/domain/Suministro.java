package pe.edu.cibertec.proyemp.domain;

/**
 * Suministro asociado a un {@link Rol} (paridad con {@code model.Suministro}
 * del legacy). Persistido bajo {@code PK=ROL#<rolId>},
 * {@code SK=SUMINISTRO#<id>} -- Patrón 2b del esquema canónico
 * ({@code SuministroRepository.findSuministro(idRol)}).
 */
public record Suministro(String id, String rolId, String nombre, String prioridad) {
}
