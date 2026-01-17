package org.keycloak;

import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Path("/api")
public class OAuthProxyResource {

    private static final Logger LOG = Logger.getLogger(OAuthProxyResource.class);

    @ConfigProperty(name = "oauth.service.url", defaultValue = "http://localhost:8081")
    String serviceUrl;

    @ConfigProperty(name = "quarkus.oidc.auth-server-url")
    String keycloakAuthServerUrl;

    @Inject
    @RestClient
    BackendServiceClient backendClient;

    @Inject
    Vertx vertx;

    private WebClient webClient;

    @PostConstruct
    void initialize() {
        this.webClient = WebClient.create(vertx);
    }

    // Proxy endpoint for Keycloak discovery - enables distributed tracing with WebClient
    @GET
    @Path("/keycloak/discovery")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> getDiscovery(@QueryParam("issuer") String issuer) {
        String discoveryUrl = (issuer != null ? issuer : keycloakAuthServerUrl) + "/.well-known/openid-configuration";
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

    // Proxy endpoint for Keycloak token exchange - enables distributed tracing with WebClient
    @POST
    @Path("/keycloak/token")
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> exchangeToken(Map<String, String> params) {
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
        
        String formData = String.format("grant_type=%s&code=%s&client_id=%s&redirect_uri=%s",
                URLEncoder.encode(grantType, StandardCharsets.UTF_8),
                URLEncoder.encode(code, StandardCharsets.UTF_8),
                URLEncoder.encode(clientId, StandardCharsets.UTF_8),
                URLEncoder.encode(redirectUri, StandardCharsets.UTF_8));
        
        LOG.infof("  └─ Form data prepared (length: %d)", formData.length());
        
        return webClient.postAbs(tokenEndpoint)
                .putHeader("Content-Type", "application/x-www-form-urlencoded")
                .sendBuffer(Buffer.buffer(formData))
                .onItem().transform(response -> {
                    LOG.infof("  └─ Response status: %d", response.statusCode());
                    LOG.infof("  └─ Response body length: %d", response.bodyAsString() != null ? response.bodyAsString().length() : 0);
                    
                    if (response.statusCode() == 200) {
                        LOG.info("  └─ ✓ Token exchange successful");
                    } else {
                        LOG.infof("  └─ ✗ Token exchange failed: %d", response.statusCode());
                        LOG.infof("  └─ Response body: %s", response.bodyAsString());
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
                            .entity("{\"error\": \"Error exchanging token\", \"message\": \"" + e.getMessage() + "\"}")
                            .build();
                });
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

    // Proxy endpoint for backend public service - enables distributed tracing with Reactive REST Client
    @GET
    @Path("/service/public")
    @PermitAll
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<Response> invokePublicService() {
        LOG.info("GET /api/service/public → Proxying to backend /public");
        
        return backendClient.getPublic()
                .onItem().transform(response -> {
                    LOG.info("  └─ ✓ Backend responded: 200");
                    return Response.ok(response).build();
                })
                .onFailure(WebApplicationException.class).recoverWithItem(e -> {
                    WebApplicationException wae = (WebApplicationException) e;
                    int status = wae.getResponse().getStatus();
                    String statusIcon = status < 400 ? "✓" : "✗";
                    LOG.infof("  └─ %s Backend responded: %d", statusIcon, status);
                    return Response.status(status)
                            .entity(wae.getMessage())
                            .build();
                })
                .onFailure().recoverWithItem(e -> {
                    LOG.errorf("  └─ ✗ Error proxying to backend: %s", e.getMessage());
                    return Response.status(500)
                            .entity("Error connecting to backend service")
                            .build();
                });
    }

    // Proxy endpoint for backend secured service - enables distributed tracing with Reactive REST Client
    @GET
    @Path("/service/secured")
    @PermitAll
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<Response> invokeSecuredService(@Context HttpServerRequest serverRequest) {
        String authHeader = serverRequest.getHeader("Authorization");
        LOG.info("GET /api/service/secured → Proxying to backend /secured");
        LOG.infof("  └─ Authorization: %s", authHeader != null ? "Bearer token present" : "missing");
        
        return backendClient.getSecured(authHeader)
                .onItem().transform(response -> {
                    LOG.info("  └─ ✓ AUTHORIZED: 200 - Access granted (user has required 'user' role)");
                    return Response.ok(response).build();
                })
                .onFailure(WebApplicationException.class).recoverWithItem(e -> {
                    WebApplicationException wae = (WebApplicationException) e;
                    int status = wae.getResponse().getStatus();
                    String statusIcon = status < 400 ? "✓" : "✗";
                    
                    // Provide user-friendly error messages
                    String userMessage;
                    String statusLabel;
                    
                    if (status == 401) {
                        statusLabel = "UNAUTHORIZED - Invalid or missing token";
                        userMessage = "Access denied: Invalid or missing authentication token";
                    } else if (status == 403) {
                        statusLabel = "FORBIDDEN - User lacks required 'user' role";
                        userMessage = "Access denied: User does not have the required 'user' role";
                    } else if (status >= 500) {
                        statusLabel = "SERVER ERROR";
                        userMessage = "Backend service error";
                    } else {
                        statusLabel = status < 400 ? "AUTHORIZED" : "DENIED";
                        userMessage = "Access denied";
                    }
                    
                    LOG.infof("  └─ %s %s: %d", statusIcon, statusLabel, status);
                    
                    return Response.status(status)
                            .entity(userMessage)
                            .build();
                })
                .onFailure().recoverWithItem(e -> {
                    LOG.errorf("  └─ ✗ Error proxying to backend: %s", e.getMessage());
                    return Response.status(500)
                            .entity("Error connecting to backend service")
                            .build();
                });
    }
}
