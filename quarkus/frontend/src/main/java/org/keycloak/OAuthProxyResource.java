package org.keycloak;

import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Path("/api")
public class OAuthProxyResource {

    private static final Logger LOG = Logger.getLogger(OAuthProxyResource.class);

    @ConfigProperty(name = "oauth.service.url", defaultValue = "http://localhost:8081")
    String serviceUrl;

    @ConfigProperty(name = "quarkus.oidc.auth-server-url")
    String keycloakAuthServerUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // Proxy endpoint for Keycloak discovery - enables distributed tracing
    @GET
    @Path("/keycloak/discovery")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDiscovery(@QueryParam("issuer") String issuer) {
        String discoveryUrl = (issuer != null ? issuer : keycloakAuthServerUrl) + "/.well-known/openid-configuration";
        LOG.infof("GET /api/keycloak/discovery → %s", discoveryUrl);
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(discoveryUrl))
                    .GET()
                    .build();
                    
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            LOG.info("  └─ ✓ Discovery loaded successfully");
            return Response.status(response.statusCode())
                    .entity(response.body())
                    .build();
        } catch (Exception e) {
            LOG.errorf("  └─ ✗ Error fetching discovery: %s", e.getMessage());
            return Response.status(500)
                    .entity("{\"error\": \"Error fetching discovery\"}")
                    .build();
        }
    }

    // Proxy endpoint for Keycloak token exchange - enables distributed tracing
    @POST
    @Path("/keycloak/token")
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response exchangeToken(Map<String, String> params) {
        String tokenEndpoint = params.get("token_endpoint");
        String grantType = params.get("grant_type");
        String code = params.get("code");
        String clientId = params.get("client_id");
        String redirectUri = params.get("redirect_uri");
        
        LOG.infof("POST /api/keycloak/token → %s", tokenEndpoint);
        LOG.infof("  └─ grant_type: %s", grantType);
        LOG.infof("  └─ client_id: %s", clientId);
        LOG.infof("  └─ redirect_uri: %s", redirectUri);
        LOG.infof("  └─ code: %s", code != null ? code.substring(0, Math.min(10, code.length())) + "..." : "null");
        
        try {
            String formData = String.format("grant_type=%s&code=%s&client_id=%s&redirect_uri=%s",
                    URLEncoder.encode(grantType, StandardCharsets.UTF_8),
                    URLEncoder.encode(code, StandardCharsets.UTF_8),
                    URLEncoder.encode(clientId, StandardCharsets.UTF_8),
                    URLEncoder.encode(redirectUri, StandardCharsets.UTF_8));
            
            LOG.infof("  └─ Form data: %s", formData.replace(code, code.substring(0, Math.min(10, code.length())) + "..."));
                    
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenEndpoint))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formData))
                    .build();
                    
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            LOG.infof("  └─ Response status: %d", response.statusCode());
            LOG.infof("  └─ Response body length: %d", response.body() != null ? response.body().length() : 0);
            
            if (response.statusCode() == 200) {
                LOG.info("  └─ ✓ Token exchange successful");
            } else {
                LOG.infof("  └─ ✗ Token exchange failed: %d", response.statusCode());
                LOG.infof("  └─ Response body: %s", response.body());
            }
            
            // Return response with explicit content-type header
            return Response.status(response.statusCode())
                    .header("Content-Type", "application/json")
                    .entity(response.body() != null ? response.body() : "{}")
                    .build();
        } catch (Exception e) {
            LOG.errorf(e, "  └─ ✗ Error exchanging token: %s", e.getMessage());
            return Response.status(500)
                    .header("Content-Type", "application/json")
                    .entity("{\"error\": \"Error exchanging token\", \"message\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }

    // Proxy endpoint for Keycloak logout - enables distributed tracing
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

    // Proxy endpoint for backend public service - enables distributed tracing
    @GET
    @Path("/service/public")
    @PermitAll
    @Produces(MediaType.TEXT_PLAIN)
    public Response invokePublicService() {
        String publicUrl = serviceUrl + "/public";
        LOG.infof("GET /api/service/public → %s", publicUrl);
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(publicUrl))
                    .GET()
                    .build();
                    
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String statusIcon = response.statusCode() < 400 ? "✓" : "✗";
            LOG.infof("  └─ %s Response: %d", statusIcon, response.statusCode());
            
            return Response.status(response.statusCode())
                    .entity(response.body())
                    .build();
        } catch (Exception e) {
            LOG.errorf("  └─ ✗ Error proxying to backend: %s", e.getMessage());
            return Response.status(500)
                    .entity("Error connecting to backend service")
                    .build();
        }
    }

    // Proxy endpoint for backend secured service - enables distributed tracing
    @GET
    @Path("/service/secured")
    @PermitAll
    @Produces(MediaType.TEXT_PLAIN)
    public Response invokeSecuredService(@Context HttpServerRequest serverRequest) {
        String securedUrl = serviceUrl + "/secured";
        String authHeader = serverRequest.getHeader("Authorization");
        LOG.infof("GET /api/service/secured → %s", securedUrl);
        LOG.infof("  └─ Authorization: %s", authHeader != null ? "Bearer token present" : "missing");
        
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(securedUrl))
                    .GET();
                    
            if (authHeader != null) {
                requestBuilder.header("Authorization", authHeader);
            }
                    
            HttpResponse<String> response = httpClient.send(requestBuilder.build(), 
                    HttpResponse.BodyHandlers.ofString());
            String statusIcon = response.statusCode() < 400 ? "✓" : "✗";
            String statusLabel = response.statusCode() < 400 ? "AUTHORIZED" : "DENIED";
            LOG.infof("  └─ %s %s: %d", statusIcon, statusLabel, response.statusCode());
            
            return Response.status(response.statusCode())
                    .entity(response.body())
                    .build();
        } catch (Exception e) {
            LOG.errorf("  └─ ✗ Error proxying to backend: %s", e.getMessage());
            return Response.status(500)
                    .entity("Error connecting to backend service")
                    .build();
        }
    }
}
