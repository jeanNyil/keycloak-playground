package io.jeannyil;

import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.WebClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Path("/api")
public class OIDCProxyResource {

    private static final Logger LOG = Logger.getLogger(OIDCProxyResource.class);

    @ConfigProperty(name = "keycloak.issuer", defaultValue = "http://localhost:8080/realms/demo")
    String keycloakIssuer;

    @Inject
    Vertx vertx;

    private WebClient webClient;

    @PostConstruct
    void initialize() {
        this.webClient = WebClient.create(vertx);
    }

    // Config endpoint to provide default issuer to UI
    @GET
    @Path("/config")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConfig() {
        return Response.ok(Map.of("issuer", keycloakIssuer)).build();
    }

    // Proxy endpoint for Keycloak discovery - enables distributed tracing
    @GET
    @Path("/keycloak/discovery")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> getDiscovery(@QueryParam("issuer") String issuer) {
        String discoveryUrl = (issuer != null ? issuer : keycloakIssuer) + "/.well-known/openid-configuration";
        LOG.infof("GET /api/keycloak/discovery → %s", discoveryUrl);
        
        return webClient.getAbs(discoveryUrl)
                .send()
                .onItem().transform(response -> {
                    LOG.info("  └─ ✓ Discovery loaded successfully");
                    return Response.status(response.statusCode())
                            .entity(response.bodyAsString())
                            .build();
                })
                .onFailure().recoverWithItem(e -> {
                    LOG.errorf("  └─ ✗ Error fetching discovery: %s", e.getMessage());
                    return Response.status(500)
                            .entity("{\"error\": \"Error fetching discovery\"}")
                            .build();
                });
    }

    // Proxy endpoint for Keycloak token exchange - enables distributed tracing
    @POST
    @Path("/keycloak/token")
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> exchangeToken(Map<String, String> params) {
        String tokenEndpoint = params.get("token_endpoint");
        String grantType = params.get("grant_type");
        
        LOG.infof("POST /api/keycloak/token → %s", tokenEndpoint);
        LOG.infof("  └─ grant_type: %s", grantType);
        
        StringBuilder formData = new StringBuilder();
        formData.append("grant_type=").append(URLEncoder.encode(grantType, StandardCharsets.UTF_8));
        
        if (params.containsKey("code")) {
            formData.append("&code=").append(URLEncoder.encode(params.get("code"), StandardCharsets.UTF_8));
        }
        if (params.containsKey("refresh_token")) {
            formData.append("&refresh_token=").append(URLEncoder.encode(params.get("refresh_token"), StandardCharsets.UTF_8));
        }
        if (params.containsKey("client_id")) {
            formData.append("&client_id=").append(URLEncoder.encode(params.get("client_id"), StandardCharsets.UTF_8));
        }
        if (params.containsKey("redirect_uri")) {
            formData.append("&redirect_uri=").append(URLEncoder.encode(params.get("redirect_uri"), StandardCharsets.UTF_8));
        }
        if (params.containsKey("scope")) {
            formData.append("&scope=").append(URLEncoder.encode(params.get("scope"), StandardCharsets.UTF_8));
        }
        
        return webClient.postAbs(tokenEndpoint)
                .putHeader("Content-Type", "application/x-www-form-urlencoded")
                .sendBuffer(Buffer.buffer(formData.toString()))
                .onItem().transform(response -> {
                    if (response.statusCode() == 200) {
                        LOG.info("  └─ ✓ Token exchange successful");
                    } else {
                        LOG.infof("  └─ ✗ Token exchange failed: %d", response.statusCode());
                    }
                    
                    return Response.status(response.statusCode())
                            .header("Content-Type", "application/json")
                            .entity(response.bodyAsString() != null ? response.bodyAsString() : "{}")
                            .build();
                })
                .onFailure().recoverWithItem(e -> {
                    LOG.errorf(e, "  └─ ✗ Error exchanging token: %s", e.getMessage());
                    return Response.status(500)
                            .header("Content-Type", "application/json")
                            .entity("{\"error\": \"Error exchanging token\"}")
                            .build();
                });
    }

    // Proxy endpoint for Keycloak userinfo - enables distributed tracing
    @GET
    @Path("/keycloak/userinfo")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> getUserInfo(
            @QueryParam("endpoint") String userinfoEndpoint,
            @Context HttpServerRequest serverRequest) {
        
        String authHeader = serverRequest.getHeader("Authorization");
        LOG.infof("GET /api/keycloak/userinfo → %s", userinfoEndpoint);
        
        var request = webClient.getAbs(userinfoEndpoint);
        if (authHeader != null) {
            request.putHeader("Authorization", authHeader);
        }
        
        return request.send()
                .onItem().transform(response -> {
                    if (response.statusCode() == 200) {
                        LOG.info("  └─ ✓ UserInfo retrieved successfully");
                    } else {
                        LOG.infof("  └─ ✗ UserInfo failed: %d", response.statusCode());
                    }
                    
                    return Response.status(response.statusCode())
                            .entity(response.bodyAsString())
                            .build();
                })
                .onFailure().recoverWithItem(e -> {
                    LOG.errorf("  └─ ✗ Error fetching userinfo: %s", e.getMessage());
                    return Response.status(500)
                            .entity("{\"error\": \"Error fetching userinfo\"}")
                            .build();
                });
    }

    // Proxy endpoint for Keycloak logout - redirect to end session
    @GET
    @Path("/keycloak/logout")
    @PermitAll
    public Response logout(
            @QueryParam("end_session_endpoint") String endSessionEndpoint,
            @QueryParam("post_logout_redirect_uri") String postLogoutRedirectUri,
            @QueryParam("id_token_hint") String idTokenHint) {
        
        LOG.infof("GET /api/keycloak/logout → %s", endSessionEndpoint);
        LOG.infof("  └─ id_token_hint: %s", idTokenHint != null ? "present" : "missing");
        
        String logoutUrl = endSessionEndpoint + "?post_logout_redirect_uri=" + 
                URLEncoder.encode(postLogoutRedirectUri, StandardCharsets.UTF_8);
        if (idTokenHint != null) {
            logoutUrl += "&id_token_hint=" + URLEncoder.encode(idTokenHint, StandardCharsets.UTF_8);
        }
        
        LOG.info("  └─ Redirecting to Keycloak logout");
        return Response.seeOther(URI.create(logoutUrl)).build();
    }
}
