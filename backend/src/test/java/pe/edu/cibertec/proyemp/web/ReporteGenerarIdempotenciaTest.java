package pe.edu.cibertec.proyemp.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Idempotencia de {@code POST /api/reportes/generar}: peticiones idénticas
 * dentro de la ventana devuelven el mismo reporte en vez de generar otro.
 * Usa códigos de app/área únicos (no necesitan existir: solo se comprueba el
 * id devuelto, no el resultado del worker).
 */
@QuarkusTest
class ReporteGenerarIdempotenciaTest {

    private static String generar(String app, String area, String formato) {
        return given().contentType("application/json")
                .body("{\"idAplicacion\":\"" + app + "\",\"idArea\":\"" + area + "\",\"formato\":\"" + formato + "\"}")
                .when().post("/api/reportes/generar")
                .then().statusCode(202).extract().path("id").toString();
    }

    @Test
    void should_returnSameId_when_samePeticionRepeatedInsideWindow() {
        String app = "IDEM-APP-" + UUID.randomUUID();
        String area = "IDEM-AREA-" + UUID.randomUUID();

        String primero = generar(app, area, "pdf");
        String segundo = generar(app, area, "pdf");
        String tercero = generar(app, area, "pdf");

        assertEquals(primero, segundo);
        assertEquals(primero, tercero);
    }

    @Test
    void should_returnDifferentId_when_formatoOrAreaDiffers() {
        String app = "IDEM-APP-" + UUID.randomUUID();
        String area = "IDEM-AREA-" + UUID.randomUUID();

        String pdf = generar(app, area, "pdf");
        String excel = generar(app, area, "excel");
        String otraArea = generar(app, area + "-X", "pdf");

        assertNotEquals(pdf, excel);
        assertNotEquals(pdf, otraArea);
    }
}
