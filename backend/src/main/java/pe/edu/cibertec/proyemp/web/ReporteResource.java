package pe.edu.cibertec.proyemp.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestStreamElementType;
import pe.edu.cibertec.proyemp.domain.EstadoReporte;
import pe.edu.cibertec.proyemp.domain.Reporte;
import pe.edu.cibertec.proyemp.domain.ReporteEjecucion;
import pe.edu.cibertec.proyemp.domain.ReporteNoListoException;
import pe.edu.cibertec.proyemp.messaging.ReporteEvento;
import pe.edu.cibertec.proyemp.messaging.ReporteEventoBroadcaster;
import pe.edu.cibertec.proyemp.report.ReportGeneratorPort;
import pe.edu.cibertec.proyemp.repository.ReporteEjecucionRepository;
import pe.edu.cibertec.proyemp.web.dto.ReporteRespuestaDto;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static pe.edu.cibertec.proyemp.web.UniFallosInfraestructura.manejarFallosInfraestructura;

/**
 * Endpoints de consulta/notificación/descarga de la feature 10
 * ({@code reportes_notificacion_sse_y_descarga}) -- coexiste con
 * {@code web/ReporteGenerarResource} (mismo {@code @Path("/api/reportes")}
 * base, feature 9, solo expone {@code POST /generar}) sin colisión de rutas.
 *
 * <p>Ninguno de los tres endpoints toca DynamoDB ni Kafka directamente más
 * allá de reutilizar {@link ReporteEjecucionRepository} (feature 9, ya
 * aprobado) -- {@code web/} no accede a DynamoDB directamente
 * (docs/architecture.md, regla dura).
 */
@Path("/api/reportes")
public class ReporteResource {

    private static final Logger LOG = Logger.getLogger(ReporteResource.class);

    private static final Set<String> FORMATOS_VALIDOS = Set.of("pdf", "excel");

    private final ReporteEjecucionRepository repository;
    private final ReporteEventoBroadcaster eventoBroadcaster;
    private final ObjectMapper objectMapper;
    private final ReportGeneratorPort pdfAdapter;
    private final ReportGeneratorPort excelAdapter;

    @Inject
    public ReporteResource(ReporteEjecucionRepository repository, ReporteEventoBroadcaster eventoBroadcaster,
            ObjectMapper objectMapper, @Named("pdf") ReportGeneratorPort pdfAdapter,
            @Named("excel") ReportGeneratorPort excelAdapter) {
        this.repository = repository;
        this.eventoBroadcaster = eventoBroadcaster;
        this.objectMapper = objectMapper;
        this.pdfAdapter = pdfAdapter;
        this.excelAdapter = excelAdapter;
    }

    /**
     * SSE (acceptance 1): mantiene la conexión abierta y multicast del
     * mismo {@link Multi} a todos los clientes conectados. NO consume Kafka
     * por request -- expone directo el puente interno alimentado por
     * {@code messaging/ReporteRequestWorker} (docs/kafka-reactive-guidance.md
     * §"SSE — relación con Kafka").
     *
     * <p>Feature 12 ({@code reactive_resilience_observability}), acceptance
     * 4: cuando el cliente cierra la conexión (navegador cerrado, red
     * caída, {@code EventSource.close()}), RESTEasy Reactive cancela la
     * suscripción a este {@link Multi} -- {@code onCancellation()} maneja
     * ese caso explícitamente (log en {@code DEBUG}, sin relanzar nada) para
     * que la desconexión cierre limpio en vez de dejar que una excepción no
     * controlada se propague. {@code onFailure()} es defensivo: hoy
     * {@code ReporteEventoBroadcaster} nunca falla (ver su Javadoc), pero si
     * en el futuro emitiera un fallo, queda logueado en vez de perderse
     * silenciosamente (docs/conventions.md prohíbe tragar excepciones sin
     * dejar rastro).
     */
    @GET
    @Path("/eventos")
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<ReporteEvento> eventos() {
        return eventoBroadcaster.stream()
                .onCancellation().invoke(() -> LOG.debug("Cliente SSE desconectado de /api/reportes/eventos -- cierre limpio"))
                .onFailure().invoke(failure -> LOG.warn("Fallo en el stream SSE /api/reportes/eventos", failure));
    }

    /**
     * Acceptance 3: devuelve el árbol {@code List<Reporte>}
     * (Cargo→Rol→4 categorías) ya persistido por el worker (feature 9),
     * deserializado tal cual desde {@code reporteJson} -- sin volver a
     * ensamblar ni regenerar nada. 404 si el id no existe o si el estado
     * todavía no es {@code LISTO} (ver {@code web/exception/ReporteExceptionMappers}).
     *
     * <p>Serializado a través de {@link ReporteRespuestaDto} (no el
     * {@link Reporte} de dominio crudo) -- capa de mapeo de respuesta
     * agregada tras coordinación cross-track explícita con
     * {@code ../frontend/} (2026-08-19, ver {@code handoff.md} de este
     * track y el de {@code ../frontend/}, y el diagnóstico campo a campo
     * en el {@code progress/review.md} de la feature 10). El dominio
     * (`Reporte`/`DetalleRol`, features 7/9, ya aprobado) no se toca --
     * esta clase solo traduce nombres de campo/tipos en el borde de
     * serialización.
     */
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> obtener(@PathParam("id") String id) {
        Uni<Response> resultado = repository.buscarPorId(id)
                .onItem().transform(ejecucion -> {
                    List<Reporte> arbol = deserializarArbol(exigirListo(ejecucion));
                    return Response.ok(ReporteRespuestaDto.de(arbol)).build();
                });
        // Manejo explícito de fallos de Uni (feature 12, acceptance 3) --
        // aplicado ANTES de que exigirListo/deserializarArbol puedan lanzar
        // sus propias excepciones de dominio (ReporteNoListoException /
        // ReporteEjecucionNoEncontradaException), que no matchean el
        // predicado de infraestructura y siguen su propio 404 sin pasar por
        // acá (ver UniFallosInfraestructura).
        return manejarFallosInfraestructura(resultado, "GET /api/reportes/" + id);
    }

    /**
     * Acceptance 2: sirve el binario del reporte con {@code
     * Content-Disposition: attachment}. 404 si el id no existe o si el
     * estado todavía no es {@code LISTO}; 400 si {@code formato} viene y
     * no es {@code pdf}/{@code excel}.
     *
     * <p><b>Formato bajo demanda (agregado 2026-08-25):</b> el query param
     * {@code formato} existía desde el diseño original pero se ignoraba
     * por completo -- siempre se servía el único binario ya generado
     * (`ejecucion.formato()`), sin importar qué formato pidiera el
     * cliente. El usuario reportó esto como un problema real de UX: el
     * frontend obligaba a elegir PDF o Excel ANTES de generar (para poder
     * mandar el `formato` que exige `POST /generar`), y solo mostraba el
     * botón de exportar del formato ya generado -- exportar el otro
     * formato exigía generar el reporte de nuevo desde cero, dos
     * consultas para lo que el usuario esperaba que fuera una ("ya venia
     * uno solo a nivel web y dos botones pdf y excel").
     *
     * <p>Ahora, si {@code formato} coincide con el ya generado, se sirve
     * el binario ya persistido (camino rápido, sin trabajo extra). Si
     * pide el OTRO formato, se regenera bajo demanda reutilizando el
     * árbol {@code List<Reporte>} ya persistido en {@code reporteJson}
     * (el mismo que usa {@link #obtener(String)}) -- no vuelve a tocar
     * DynamoDB para reensamblar el reporte, solo corre el adaptador
     * {@link ReportGeneratorPort} del formato pedido sobre datos que ya
     * están en memoria. El binario generado al vuelo NO se persiste de
     * vuelta (se sirve directo) -- mantiene el modelo de datos simple
     * (un solo {@code binario}/{@code formato} por ejecución) a costa de
     * regenerar en cada descarga del formato "no cacheado", aceptable
     * dado que un reporte es información acotada (Cargo→Rol→4 categorías
     * de un Área/Aplicación), no un archivo pesado.
     */
    @GET
    @Path("/{id}/descargar")
    public Uni<Response> descargar(@PathParam("id") String id, @QueryParam("formato") String formatoSolicitado) {
        if (formatoSolicitado != null && !FORMATOS_VALIDOS.contains(formatoSolicitado.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "formato debe ser 'pdf' o 'excel', recibido: '" + formatoSolicitado + "'");
        }
        Uni<Response> resultado = repository.buscarPorId(id)
                .onItem().transformToUni(ejecucionSinValidar -> {
                    ReporteEjecucion ejecucion = exigirListo(ejecucionSinValidar);
                    String formato = formatoSolicitado == null
                            ? ejecucion.formato()
                            : formatoSolicitado.toLowerCase(Locale.ROOT);
                    if (formato.equalsIgnoreCase(ejecucion.formato())) {
                        return Uni.createFrom().item(respuestaBinario(id, formato, ejecucion.binario()));
                    }
                    return regenerarBajoDemanda(id, formato, ejecucion);
                });
        // Manejo explícito de fallos de Uni (feature 12, acceptance 3) --
        // ver comentario equivalente en obtener().
        return manejarFallosInfraestructura(resultado, "GET /api/reportes/" + id + "/descargar");
    }

    private Uni<Response> regenerarBajoDemanda(String id, String formato, ReporteEjecucion ejecucion) {
        List<Reporte> arbol = deserializarArbol(ejecucion);
        ReportGeneratorPort adaptador = "excel".equalsIgnoreCase(formato) ? excelAdapter : pdfAdapter;
        return adaptador.generar(arbol)
                .onItem().transform(binario -> respuestaBinario(id, formato, binario));
    }

    private Response respuestaBinario(String id, String formato, byte[] binario) {
        boolean esExcel = "excel".equalsIgnoreCase(formato);
        String contentType = esExcel
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : "application/pdf";
        String extension = esExcel ? "xlsx" : "pdf";
        return Response.ok(binario)
                .type(contentType)
                .header("Content-Disposition", "attachment; filename=\"reporte-" + id + "." + extension + "\"")
                .build();
    }

    private ReporteEjecucion exigirListo(ReporteEjecucion ejecucion) {
        if (ejecucion.estado() != EstadoReporte.LISTO) {
            throw new ReporteNoListoException(ejecucion.id(), ejecucion.estado());
        }
        return ejecucion;
    }

    private List<Reporte> deserializarArbol(ReporteEjecucion ejecucion) {
        try {
            return objectMapper.readValue(ejecucion.reporteJson(), new TypeReference<List<Reporte>>() {
            });
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Error deserializando el arbol de reporte persistido para id '" + ejecucion.id() + "'", ex);
        }
    }
}
