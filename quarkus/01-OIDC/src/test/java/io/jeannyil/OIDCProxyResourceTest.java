package io.jeannyil;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
public class OIDCProxyResourceTest {

    @Test
    public void testIndexEndpoint() {
        given()
          .when().get("/")
          .then()
             .statusCode(200)
             .body(containsString("OpenID Connect Playground"));
    }
}
