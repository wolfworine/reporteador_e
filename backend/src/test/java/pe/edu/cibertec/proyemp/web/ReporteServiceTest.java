package pe.edu.cibertec.proyemp.web;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import pe.edu.cibertec.proyemp.domain.Atributo;
import pe.edu.cibertec.proyemp.domain.CategoriaSuministro;
import pe.edu.cibertec.proyemp.domain.DetalleRol;
import pe.edu.cibertec.proyemp.domain.Reporte;
import pe.edu.cibertec.proyemp.service.ReporteService;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de integración de {@link ReporteService} contra el LocalStack real de
 * Quarkus Dev Services (feature 7, {@code reportes_dominio_y_query_sin_duplicados}).
 *
 * <p>Sin endpoint HTTP propio todavía (decisión documentada en
 * {@code progress/current.md}): el escenario se arma vía los endpoints REST
 * ya existentes de las features 3/4 (mismos helpers estáticos que
 * {@code RolResourceTest}/{@code AtributoResourceTest}) y el service se
 * inyecta e invoca directamente, suscribiendo el {@code Uni} con
 * {@code .await().atMost(...)} (patrón ya usado en
 * {@code web/UsuarioResourceTest}).
 *
 * <p>Vive en el paquete {@code web} (no {@code service}) porque reusa los
 * helpers estáticos package-private de {@link RolResourceTest}
 * (mismo patrón ya usado por {@code AtributoResourceTest} y
 * {@code SuministroResourceTest} -- ver {@code progress/history.md},
 * sección "Feature 4").
 */
@QuarkusTest
class ReporteServiceTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Inject
    ReporteService reporteService;

    @Test
    void should_assembleCargoRolFourCategoriesStructure_withRealDataFromFeatures3And4() {
        Escenario e = crearEscenario("APP-ESTRUCTURA");
        RolConSuministro rol = crearRolConSuministro(e.ubicacion, e.area, e.cargo1, "ROL-ESTRUCTURA");

        crearAtributo(e.aplicacion, rol, "ROLES", "R1", "Atributo Roles");
        crearAtributo(e.aplicacion, rol, "GRUPOS", "G1", "Atributo Grupos");
        crearAtributo(e.aplicacion, rol, "INBOX", "I1", "Atributo Inbox");
        crearAtributo(e.aplicacion, rol, "DEFAULTINBOX", "D1", "Atributo DefaultInbox");

        List<Reporte> reportes = reporteService.generar(e.aplicacion, e.area).await().atMost(TIMEOUT);

        assertEquals(1, reportes.size());
        Reporte reporte = reportes.get(0);
        assertEquals(e.area, reporte.areaCodigo());
        assertEquals(e.cargo1, reporte.cargoCodigo());
        assertEquals(1, reporte.cantidadRol());
        assertEquals(4, reporte.cantidadSuministro());
        assertEquals(1, reporte.detalleRol().size());

        DetalleRol detalleRol = reporte.detalleRol().get(0);
        assertEquals(rol.rolId(), detalleRol.idRol());
        assertEquals(4, detalleRol.cantidadSuministroRol());
        assertEquals(
                Set.of(CategoriaSuministro.ROLES, CategoriaSuministro.GRUPOS, CategoriaSuministro.INBOX,
                        CategoriaSuministro.DEFAULTINBOX),
                Set.copyOf(detalleRol.tiposConDatos()));

        assertEquals(1, detalleRol.suministroRoles().size());
        assertEquals("R1", detalleRol.suministroRoles().get(0).atributoCodigo());
        assertEquals(1, detalleRol.suministroGrupos().size());
        assertEquals("G1", detalleRol.suministroGrupos().get(0).atributoCodigo());
        assertEquals(1, detalleRol.suministroInbox().size());
        assertEquals("I1", detalleRol.suministroInbox().get(0).atributoCodigo());
        assertEquals(1, detalleRol.suministroDefaultInbox().size());
        assertEquals("D1", detalleRol.suministroDefaultInbox().get(0).atributoCodigo());
    }

    /**
     * Acceptance 2: mismo escenario que en el legacy forzaba el
     * {@code HashSet} de {@code ReporteManagedBean.reporte()} (un Cargo con
     * varios Roles, cada uno con varios Atributos en varias categorías --
     * el join Cargo→Rol→Atributo del JPQL de origen habría devuelto una
     * fila de Cargo por cada Atributo asociado). La corrección está en
     * cómo se arma la consulta (agrupado por {@code cargoCodigo}/{@code rolId}
     * en {@code AtributoService.listarCargosConRoles}), no en limpiar
     * duplicados con un {@code Set} después -- este test no dedupea nada,
     * solo verifica el tamaño y contenido exactos del resultado.
     */
    @Test
    void should_notContainDuplicateCargosOrRoles_withSameScenarioThatForcedLegacyHashSet() {
        Escenario e = crearEscenario("APP-DUPLICADOS");

        RolConSuministro rol1 = crearRolConSuministro(e.ubicacion, e.area, e.cargo1, "ROL1-" + e.cargo1);
        RolConSuministro rol2 = crearRolConSuministro(e.ubicacion, e.area, e.cargo1, "ROL2-" + e.cargo1);
        RolConSuministro rol3 = crearRolConSuministro(e.ubicacion, e.area, e.cargo2, "ROL3-" + e.cargo2);

        // rol1: varios atributos en varias categorias -- en el legacy, cada
        // fila de Atributo hacia que el join trajera el Cargo repetido.
        crearAtributo(e.aplicacion, rol1, "ROLES", "C1-R1-A", "Atributo A");
        crearAtributo(e.aplicacion, rol1, "ROLES", "C1-R1-B", "Atributo B");
        crearAtributo(e.aplicacion, rol1, "GRUPOS", "C1-R1-C", "Atributo C");

        // rol2: mismo cargo, otro rol, tambien con varios atributos.
        crearAtributo(e.aplicacion, rol2, "INBOX", "C1-R2-A", "Atributo D");
        crearAtributo(e.aplicacion, rol2, "INBOX", "C1-R2-B", "Atributo E");

        // rol3: otro cargo.
        crearAtributo(e.aplicacion, rol3, "DEFAULTINBOX", "C2-R3-A", "Atributo F");

        List<Reporte> reportes = reporteService.generar(e.aplicacion, e.area).await().atMost(TIMEOUT);

        // Exactamente 2 Reporte (uno por Cargo), sin duplicados -- no 6 (uno
        // por Atributo creado) como habria producido el bug del legacy.
        assertEquals(2, reportes.size());
        assertEquals(Set.of(e.cargo1, e.cargo2),
                reportes.stream().map(Reporte::cargoCodigo).collect(Collectors.toSet()));

        Reporte reporteCargo1 = reportes.stream().filter(r -> r.cargoCodigo().equals(e.cargo1)).findFirst()
                .orElseThrow();
        // Exactamente 2 Roles bajo cargo1, sin duplicados -- no 3 (uno por
        // Atributo de rol1) ni 5 (uno por Atributo total de cargo1).
        assertEquals(2, reporteCargo1.detalleRol().size());
        assertEquals(Set.of(rol1.rolId(), rol2.rolId()),
                reporteCargo1.detalleRol().stream().map(DetalleRol::idRol).collect(Collectors.toSet()));

        DetalleRol detalleRol1 = reporteCargo1.detalleRol().stream().filter(d -> d.idRol().equals(rol1.rolId()))
                .findFirst().orElseThrow();
        assertEquals(3, detalleRol1.cantidadSuministroRol());
        assertEquals(2, detalleRol1.suministroRoles().size());
        assertEquals(1, detalleRol1.suministroGrupos().size());
        assertTrue(detalleRol1.suministroInbox().isEmpty());
        assertTrue(detalleRol1.suministroDefaultInbox().isEmpty());
        assertNoDuplicateAtributoIds(detalleRol1.suministroRoles());

        DetalleRol detalleRol2 = reporteCargo1.detalleRol().stream().filter(d -> d.idRol().equals(rol2.rolId()))
                .findFirst().orElseThrow();
        assertEquals(2, detalleRol2.cantidadSuministroRol());
        assertEquals(2, detalleRol2.suministroInbox().size());
        assertNoDuplicateAtributoIds(detalleRol2.suministroInbox());

        Reporte reporteCargo2 = reportes.stream().filter(r -> r.cargoCodigo().equals(e.cargo2)).findFirst()
                .orElseThrow();
        assertEquals(1, reporteCargo2.detalleRol().size());
        assertEquals(rol3.rolId(), reporteCargo2.detalleRol().get(0).idRol());
        assertEquals(1, reporteCargo2.detalleRol().get(0).cantidadSuministroRol());
    }

    @Test
    void should_returnEmptyList_whenAreaHasNoCargosWithActiveAtributos() {
        Escenario e = crearEscenario("APP-VACIO");

        List<Reporte> reportes = reporteService.generar(e.aplicacion, e.area).await().atMost(TIMEOUT);

        assertTrue(reportes.isEmpty());
    }

    private static void assertNoDuplicateAtributoIds(List<Atributo> atributos) {
        Set<String> ids = atributos.stream().map(Atributo::id).collect(Collectors.toSet());
        assertEquals(atributos.size(), ids.size(), "no deberia haber ids de Atributo repetidos: " + atributos);
    }

    private record Escenario(String aplicacion, String ubicacion, String area, String cargo1, String cargo2) {
    }

    private record RolConSuministro(String rolId, String suministroId) {
    }

    private Escenario crearEscenario(String prefijo) {
        String aplicacion = RolResourceTest.crearAplicacion("APLICACION-" + prefijo);
        String ubicacion = RolResourceTest.crearUbicacion("UBIC-" + prefijo);
        String area = RolResourceTest.crearArea("AREA-" + prefijo);
        String cargo1 = RolResourceTest.crearCargo("CARGO1-" + prefijo);
        String cargo2 = RolResourceTest.crearCargo("CARGO2-" + prefijo);
        return new Escenario(aplicacion, ubicacion, area, cargo1, cargo2);
    }

    private static RolConSuministro crearRolConSuministro(String ubicacion, String area, String cargo,
            String nombreRol) {
        String rol = given().contentType("application/json")
                .body(RolResourceTest.rolJson(ubicacion, area, cargo, nombreRol))
                .when().post("/api/roles").then().statusCode(201).extract().path("id").toString();
        String suministro = given().contentType("application/json")
                .body("{\"nombre\":\"SUM-" + nombreRol + "\",\"prioridad\":\"ALTA\"}")
                .when().post("/api/roles/" + rol + "/suministros").then().statusCode(201)
                .extract().path("id").toString();
        return new RolConSuministro(rol, suministro);
    }

    private static void crearAtributo(String aplicacion, RolConSuministro rol, String categoria,
            String atributoCodigo, String atributoNombre) {
        given().contentType("application/json")
                .body("{"
                        + "\"aplicacionCodigo\":\"" + aplicacion + "\","
                        + "\"rolId\":\"" + rol.rolId() + "\","
                        + "\"suministroId\":\"" + rol.suministroId() + "\","
                        + "\"categoria\":\"" + categoria + "\","
                        + "\"atributoCodigo\":\"" + atributoCodigo + "\","
                        + "\"atributoNombre\":\"" + atributoNombre + "\","
                        + "\"estado\":\"ACTIVO\"}")
                .when().post("/api/atributos")
                .then().statusCode(201);
    }
}
