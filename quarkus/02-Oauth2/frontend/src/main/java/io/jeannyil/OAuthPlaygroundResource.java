package io.jeannyil;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Path("/")
public class OAuthPlaygroundResource {

    private static final Logger LOG = Logger.getLogger(OAuthPlaygroundResource.class);
    private static final String SESSION_COOKIE = "oauth-session";

    @ConfigProperty(name = "keycloak.issuer", defaultValue = "http://localhost:8080/realms/demo")
    String defaultIssuer;

    @Inject
    OAuthSessionStore sessionStore;

    @Inject
    KeycloakProxyService keycloakProxy;

    @Inject
    @RestClient
    BackendServiceClient backendClient;

    @Inject
    Template index;

    @Inject
    Template discovery;

    @Inject
    Template authorization;

    @Inject
    Template invoke;

    @Inject
    @io.quarkus.qute.Location("fragments/discovery-result.html")
    Template discoveryResult;

    @Inject
    @io.quarkus.qute.Location("fragments/token-result.html")
    Template tokenResult;

    @Inject
    @io.quarkus.qute.Location("fragments/public-result.html")
    Template publicResult;

    @Inject
    @io.quarkus.qute.Location("fragments/secured-result.html")
    Template securedResult;

    // --- Full page ---

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response getIndex(@CookieParam(SESSION_COOKIE) String sessionId,
                             @QueryParam("step") String step,
                             @QueryParam("message") String message) {
        boolean newSession = false;
        if (sessionId == null || sessionStore.getSession(sessionId) == null) {
            sessionId = sessionStore.createSession();
            newSession = true;
        }
        OAuthFlowState state = sessionStore.getOrCreateSession(sessionId);
        if (step != null) {
            state.setCurrentStep(step);
        }

        String renderedStep = renderStepTemplate(state.getCurrentStep(), state).render();

        TemplateInstance page = index.data("currentStep", state.getCurrentStep())
                .data("defaultIssuer", defaultIssuer)
                .data("state", state)
                .data("stepContent", renderedStep)
                .data("message", message);

        Response.ResponseBuilder rb = Response.ok(page);
        if (newSession) {
            rb.cookie(new NewCookie.Builder(SESSION_COOKIE)
                    .value(sessionId)
                    .path("/")
                    .maxAge(3600)
                    .build());
        }
        return rb.build();
    }

    // --- Step navigation (HTMX partial) ---

    @GET
    @Path("/step/{name}")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance getStep(@PathParam("name") String name,
                                   @CookieParam(SESSION_COOKIE) String sessionId) {
        OAuthFlowState state = sessionStore.getOrCreateSession(sessionId);
        state.setCurrentStep(name);
        return renderStepTemplate(name, state);
    }

    // --- Discovery ---

    @POST
    @Path("/discovery")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Uni<TemplateInstance> postDiscovery(@FormParam("issuer") String issuer,
                                              @CookieParam(SESSION_COOKIE) String sessionId) {
        OAuthFlowState state = sessionStore.getOrCreateSession(sessionId);
        String resolvedIssuer = (issuer != null && !issuer.isBlank()) ? issuer : defaultIssuer;
        state.setIssuer(resolvedIssuer);

        return keycloakProxy.fetchDiscovery(resolvedIssuer)
                .onItem().transform(disc -> {
                    state.setDiscovery(disc);
                    return discoveryResult.data("discoveryJson", keycloakProxy.toFormattedJson(disc));
                });
    }

    // --- Authorization: send user to Keycloak ---

    @POST
    @Path("/auth/send")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response sendAuthorizationRequest(
            @FormParam("clientId") String clientId,
            @FormParam("scope") String scope,
            @FormParam("redirectUri") String redirectUri,
            @CookieParam(SESSION_COOKIE) String sessionId) {

        OAuthFlowState state = sessionStore.getOrCreateSession(sessionId);
        state.setClientId(clientId);
        state.setScope(scope);

        if (state.getDiscovery() == null) {
            return Response.seeOther(URI.create("/?message=" + enc("Please load discovery first")))
                    .build();
        }

        String authEndpoint = (String) state.getDiscovery().get("authorization_endpoint");
        String callbackUri = redirectUri + "auth/callback";
        state.setRedirectUri(callbackUri);

        StringBuilder url = new StringBuilder(authEndpoint);
        url.append("?client_id=").append(enc(clientId));
        url.append("&response_type=code");
        url.append("&redirect_uri=").append(enc(callbackUri));
        if (scope != null && !scope.isBlank()) {
            url.append("&scope=").append(enc(scope));
        }

        LOG.infof("POST /auth/send → Redirecting to %s", url);
        return Response.seeOther(URI.create(url.toString())).build();
    }

    // --- Auth callback: auto-exchange code for tokens ---

    @GET
    @Path("/auth/callback")
    public Response authCallback(@QueryParam("code") String code,
                                 @QueryParam("error") String error,
                                 @QueryParam("error_description") String errorDescription,
                                 @CookieParam(SESSION_COOKIE) String sessionId) {
        OAuthFlowState state = sessionStore.getOrCreateSession(sessionId);

        if (error != null) {
            LOG.infof("Auth callback error: %s - %s", error, errorDescription);
            state.setAuthError(error);
            state.setAuthErrorDescription(errorDescription);
            state.setCurrentStep("authorization");
            return Response.seeOther(URI.create("/?step=authorization")).build();
        }

        if (code != null) {
            LOG.infof("Auth callback received code: %s...", code.substring(0, Math.min(10, code.length())));
            state.setAuthorizationCode(code);

            // Auto-exchange code for tokens (matching 02-Oauth2 behavior)
            try {
                String tokenEndpoint = (String) state.getDiscovery().get("token_endpoint");
                Map<String, String> params = new LinkedHashMap<>();
                params.put("grant_type", "authorization_code");
                params.put("code", code);
                params.put("client_id", state.getClientId());
                params.put("redirect_uri", state.getRedirectUri());

                Map<String, Object> response = keycloakProxy.exchangeToken(tokenEndpoint, params)
                        .await().indefinitely();

                String responseJson = keycloakProxy.toFormattedJson(response);
                state.setLastTokenResponse(responseJson);

                Object accessTokenRaw = response.get("access_token");
                if (accessTokenRaw != null) {
                    state.setAccessToken(accessTokenRaw.toString());
                }
                Object refreshTokenRaw = response.get("refresh_token");
                if (refreshTokenRaw != null) {
                    state.setRefreshToken(refreshTokenRaw.toString());
                }
                Object idTokenRaw = response.get("id_token");
                if (idTokenRaw != null) {
                    state.setIdToken(idTokenRaw.toString());
                }
            } catch (Exception e) {
                LOG.errorf("Token exchange failed: %s", e.getMessage());
                state.setAuthError("token_exchange_failed");
                state.setAuthErrorDescription(e.getMessage());
            }

            state.setCurrentStep("authorization");
            return Response.seeOther(URI.create("/?step=authorization")).build();
        }

        return Response.seeOther(URI.create("/")).build();
    }

    // --- Invoke backend: public endpoint ---

    @POST
    @Path("/service/public")
    @Produces(MediaType.TEXT_HTML)
    public Uni<TemplateInstance> invokePublicService(@CookieParam(SESSION_COOKIE) String sessionId) {
        OAuthFlowState state = sessionStore.getOrCreateSession(sessionId);

        return backendClient.getPublic()
                .onItem().transform(response -> {
                    state.setLastPublicResponse(response);
                    return publicResult.data("statusIcon", "\u2713")
                            .data("statusCode", 200)
                            .data("responseText", response);
                })
                .onFailure(WebApplicationException.class).recoverWithItem(e -> {
                    WebApplicationException wae = (WebApplicationException) e;
                    int status = wae.getResponse().getStatus();
                    String icon = status < 400 ? "\u2713" : "\u2717";
                    String msg = wae.getMessage();
                    state.setLastPublicResponse(icon + " [" + status + "] " + msg);
                    return publicResult.data("statusIcon", icon)
                            .data("statusCode", status)
                            .data("responseText", msg);
                })
                .onFailure().recoverWithItem(e -> {
                    state.setLastPublicResponse("Error connecting to backend service");
                    return publicResult.data("statusIcon", "\u2717")
                            .data("statusCode", 500)
                            .data("responseText", "Error connecting to backend service: " + e.getMessage());
                });
    }

    // --- Invoke backend: secured endpoint ---

    @POST
    @Path("/service/secured")
    @Produces(MediaType.TEXT_HTML)
    public Uni<TemplateInstance> invokeSecuredService(@CookieParam(SESSION_COOKIE) String sessionId) {
        OAuthFlowState state = sessionStore.getOrCreateSession(sessionId);

        if (state.getAccessToken() == null) {
            return Uni.createFrom().item(
                    securedResult.data("statusIcon", "\u2717")
                            .data("statusCode", 0)
                            .data("responseText", "No access token available. Please authorize first."));
        }

        String authHeader = "Bearer " + state.getAccessToken();

        return backendClient.getSecured(authHeader)
                .onItem().transform(response -> {
                    state.setLastSecuredResponse(response);
                    return securedResult.data("statusIcon", "\u2713")
                            .data("statusCode", 200)
                            .data("responseText", response);
                })
                .onFailure(WebApplicationException.class).recoverWithItem(e -> {
                    WebApplicationException wae = (WebApplicationException) e;
                    int status = wae.getResponse().getStatus();
                    String icon = status < 400 ? "\u2713" : "\u2717";
                    String msg;
                    if (status == 401) {
                        msg = "Access denied: Invalid or missing authentication token";
                    } else if (status == 403) {
                        msg = "Access denied: User does not have the required 'user' role";
                    } else {
                        msg = wae.getMessage();
                    }
                    state.setLastSecuredResponse(icon + " [" + status + "] " + msg);
                    return securedResult.data("statusIcon", icon)
                            .data("statusCode", status)
                            .data("responseText", msg);
                })
                .onFailure().recoverWithItem(e -> {
                    state.setLastSecuredResponse("Error connecting to backend service");
                    return securedResult.data("statusIcon", "\u2717")
                            .data("statusCode", 500)
                            .data("responseText", "Error connecting to backend service: " + e.getMessage());
                });
    }

    // --- Reset ---

    @POST
    @Path("/reset")
    @Produces(MediaType.TEXT_HTML)
    public Response reset(@CookieParam(SESSION_COOKIE) String sessionId) {
        sessionStore.removeSession(sessionId);
        String newSessionId = sessionStore.createSession();

        return Response.seeOther(URI.create("/"))
                .cookie(new NewCookie.Builder(SESSION_COOKIE)
                        .value(newSessionId)
                        .path("/")
                        .maxAge(3600)
                        .build())
                .build();
    }

    // --- Logout ---

    @GET
    @Path("/logout")
    public Response logout(@CookieParam(SESSION_COOKIE) String sessionId) {
        OAuthFlowState state = sessionStore.getSession(sessionId);

        if (state == null || state.getDiscovery() == null
                || state.getDiscovery().get("end_session_endpoint") == null) {
            return Response.seeOther(URI.create("/?message="
                    + enc("Please load discovery first to enable logout")))
                    .build();
        }

        if (state.getIdToken() == null) {
            sessionStore.removeSession(sessionId);
            return Response.seeOther(URI.create("/?message="
                    + enc("Logout requires an ID token (id_token_hint), which is only issued when "
                    + "authenticating with the 'openid' scope (OIDC). "
                    + "If you authenticated without the 'openid' scope (plain OAuth 2.0), "
                    + "Keycloak does not issue an ID token and server-side logout is not available. "
                    + "Local session state has been cleared.")))
                    .cookie(new NewCookie.Builder(SESSION_COOKIE)
                            .value("")
                            .path("/")
                            .maxAge(0)
                            .build())
                    .build();
        }

        String endSessionEndpoint = (String) state.getDiscovery().get("end_session_endpoint");
        String idTokenHint = state.getIdToken();
        String redirectUri = state.getRedirectUri();
        String postLogoutUri = redirectUri != null
                ? redirectUri.replace("auth/callback", "")
                : "/";

        LOG.infof("GET /logout → %s", endSessionEndpoint);

        String logoutUrl = endSessionEndpoint
                + "?post_logout_redirect_uri=" + enc(postLogoutUri)
                + "&id_token_hint=" + enc(idTokenHint);

        sessionStore.removeSession(sessionId);

        return Response.seeOther(URI.create(logoutUrl))
                .cookie(new NewCookie.Builder(SESSION_COOKIE)
                        .value("")
                        .path("/")
                        .maxAge(0)
                        .build())
                .build();
    }

    // --- Helpers ---

    private TemplateInstance renderStepTemplate(String stepName, OAuthFlowState state) {
        return switch (stepName) {
            case "discovery" -> discovery
                    .data("defaultIssuer", defaultIssuer)
                    .data("state", state)
                    .data("discoveryJson", state.getDiscovery() != null
                            ? keycloakProxy.toFormattedJson(state.getDiscovery()) : null);
            case "authorization" -> {
                String accessTokenHdr = null, accessTokenPld = null, accessTokenSig = null;
                String accessTokenEncoded = state.getAccessToken();
                if (accessTokenEncoded != null) {
                    String[] parts = accessTokenEncoded.split("\\.");
                    if (parts.length == 3) {
                        accessTokenHdr = prettyJson(base64UrlDecode(parts[0]));
                        accessTokenPld = prettyJson(base64UrlDecode(parts[1]));
                        accessTokenSig = parts[2];
                    }
                }
                yield authorization
                        .data("state", state)
                        .data("accessTokenHeader", accessTokenHdr)
                        .data("accessTokenPayload", accessTokenPld)
                        .data("accessTokenSignature", accessTokenSig)
                        .data("accessTokenEncoded", accessTokenEncoded);
            }
            case "invoke" -> invoke
                    .data("state", state);
            default -> discovery
                    .data("defaultIssuer", defaultIssuer)
                    .data("state", state)
                    .data("discoveryJson", null);
        };
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String base64UrlDecode(String input) {
        String padded = switch (input.length() % 4) {
            case 2 -> input + "==";
            case 3 -> input + "=";
            default -> input;
        };
        byte[] decoded = Base64.getUrlDecoder().decode(padded);
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private String prettyJson(String json) {
        try {
            Object obj = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Object.class);
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            return json;
        }
    }
}
