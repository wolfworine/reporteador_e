package pe.edu.cibertec.proyemp.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class StatusResourceTest {

    @Test
    void should_return200WithUpStatus_whenStatusEndpointIsCalled() {
        given()
          .when().get("/api/status")
          .then()
             .statusCode(200)
             .body("status", is("UP"))
             .body("service", is("entel-role-repository-backend"));
    }
}
