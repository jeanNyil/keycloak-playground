package io.jeannyil;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
public class OidcPlaygroundResourceTest {

    @Test
    public void testIndexEndpoint() {
        given()
          .when().get("/")
          .then()
             .statusCode(200)
             .contentType("text/html")
             .body(containsString("OpenID Connect Playground"));
    }

    @Test
    public void testStepNavigation() {
        given()
          .when().get("/step/discovery")
          .then()
             .statusCode(200)
             .body(containsString("Discovery"));
    }
}
