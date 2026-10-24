package pe.edu.cibertec.proyemp.domain;

import java.util.List;

/**
 * Forma de respuesta elegida para el endpoint que replica
 * {@code CargoRepository.findCargo(aplicacion, area)}: en vez de una lista
 * plana de Cargos (como el legacy, que además deduplicaba con
 * {@code distinct} en el JPQL), se agrupan los Roles bajo su Cargo -- forma
 * más útil para un cliente REST/JSON y evita el patrón de deduplicación
 * manual con {@code HashSet} que tenía el legacy en otros puntos (ver
 * {@code docs/migration-plan.md} §3.5). Decisión documentada en
 * {@code progress/current.md}.
 */
public record CargoConRoles(String cargoCodigo, String cargoNombre, List<RolResumen> roles) {
}
