package pe.edu.cibertec.proyemp.web.dto;

import java.util.List;
import pe.edu.cibertec.proyemp.domain.CategoriaSuministro;
import pe.edu.cibertec.proyemp.domain.DetalleRol;

/**
 * Vista de serialización sobre {@link DetalleRol} (no lo reemplaza) con
 * los nombres de campo que espera
 * {@code ../frontend/src/types/reporte.ts#DetalleRol} -- coordinación
 * cross-track 2026-08-19 (ver {@code handoff.md} de este track y el de
 * {@code ../frontend/} para el diagnóstico campo a campo completo).
 *
 * <p>Dos diferencias reales, no solo de nombre, documentadas ahí:
 * <ul>
 * <li>{@code tipoRol} (nombres de {@link CategoriaSuministro} en
 * {@link DetalleRol#tiposConDatos()}) NO es el {@code tiporol} original
 * del legacy -- se confirmó leyendo el código fuente legacy real
 * ({@code ReporteManagedBean.java} y su respaldo) que {@code setTiporol}
 * nunca se invocaba ahí (dead code, columna "Titularidad" del legacy
 * siempre vacía), así que no hay semántica original que preservar.</li>
 * <li>{@code cantidadSuministros} no existe en el dominio -- el propio
 * dominio documenta que era dead code en el legacy. Se expone aquí con el
 * mismo valor que {@code cantidadSuministroRol} en vez de omitirse, para
 * no romper el tipo ya aprobado del frontend.</li>
 * </ul>
 */
public record DetalleRolRespuestaDto(
        String idRol,
        String rol,
        String tipo,
        String aplicacion,
        List<String> tipoRol,
        List<DetalleRespuestaDto> suministroRoles,
        List<DetalleRespuestaDto> suministroGrupos,
        List<DetalleRespuestaDto> suministroInbox,
        List<DetalleRespuestaDto> suministroDefaultinbox,
        int cantidadSuministroRol,
        int cantidadSuministros) {

    public static DetalleRolRespuestaDto de(DetalleRol detalleRol) {
        return new DetalleRolRespuestaDto(
                detalleRol.idRol(),
                detalleRol.rol(),
                detalleRol.tipo(),
                detalleRol.aplicacion(),
                detalleRol.tiposConDatos().stream().map(CategoriaSuministro::name).toList(),
                detalleRol.suministroRoles().stream().map(DetalleRespuestaDto::de).toList(),
                detalleRol.suministroGrupos().stream().map(DetalleRespuestaDto::de).toList(),
                detalleRol.suministroInbox().stream().map(DetalleRespuestaDto::de).toList(),
                detalleRol.suministroDefaultInbox().stream().map(DetalleRespuestaDto::de).toList(),
                detalleRol.cantidadSuministroRol(),
                detalleRol.cantidadSuministroRol());
    }
}
