package io.jeannyil;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Path("/")
public class OidcPlaygroundResource {

    private static final Logger LOG = Logger.getLogger(OidcPlaygroundResource.class);
    private static final String SESSION_COOKIE = "oidc-session";

    @ConfigProperty(name = "keycloak.issuer", defaultValue = "http://localhost:8080/realms/demo")
    String defaultIssuer;

    @Inject
    OidcSessionStore sessionStore;

    @Inject
    KeycloakProxyService keycloakProxy;

    @Inject
    Template index;

    @Inject
    Template discovery;

    @Inject
    Template authentication;

    @Inject
    Template token;

    @Inject
    Template refresh;

    @Inject
    Template userinfo;

    @Inject
    @io.quarkus.qute.Location("fragments/discovery-result.html")
    Template discoveryResult;

    @Inject
    @io.quarkus.qute.Location("fragments/auth-request.html")
    Template authRequest;

    @Inject
    @io.quarkus.qute.Location("fragments/token-result.html")
    Template tokenResult;

    @Inject
    @io.quarkus.qute.Location("fragments/refresh-result.html")
    Template refreshResult;

    @Inject
    @io.quarkus.qute.Location("fragments/userinfo-result.html")
    Template userinfoResult;

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
        OidcFlowState state = sessionStore.getOrCreateSession(sessionId);
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
        OidcFlowState state = sessionStore.getOrCreateSession(sessionId);
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
        OidcFlowState state = sessionStore.getOrCreateSession(sessionId);
        String resolvedIssuer = (issuer != null && !issuer.isBlank()) ? issuer : defaultIssuer;
        state.setIssuer(resolvedIssuer);

        return keycloakProxy.fetchDiscovery(resolvedIssuer)
                .onItem().transform(disc -> {
                    state.setDiscovery(disc);
                    return discoveryResult.data("discoveryJson", keycloakProxy.toFormattedJson(disc));
                });
    }

    // --- Authentication ---

    @POST
    @Path("/auth/generate")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance generateAuthRequest(
            @FormParam("clientId") String clientId,
            @FormParam("scope") String scope,
            @FormParam("prompt") String prompt,
            @FormParam("maxAge") String maxAge,
            @FormParam("loginHint") String loginHint,
            @FormParam("redirectUri") String redirectUri,
            @CookieParam(SESSION_COOKIE) String sessionId) {

        OidcFlowState state = sessionStore.getOrCreateSession(sessionId);
        state.setClientId(clientId);
        state.setScope(scope);
        state.setPrompt(prompt);
        state.setMaxAge(maxAge);
        state.setLoginHint(loginHint);

        if (state.getDiscovery() == null) {
            return authRequest.data("error", "Please load discovery first");
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
        if (prompt != null && !prompt.isBlank()) {
            url.append("&prompt=").append(enc(prompt));
        }
        if (maxAge != null && !maxAge.isBlank()) {
            url.append("&max_age=").append(enc(maxAge));
        }
        if (loginHint != null && !loginHint.isBlank()) {
            url.append("&login_hint=").append(enc(loginHint));
        }

        String authUrl = url.toString();
        state.setAuthenticationRequestUrl(authUrl);

        String formatted = authUrl.replace("?", "\n\n").replace("&", "\n");

        return authRequest.data("authUrl", authUrl)
                .data("authRequestFormatted", formatted)
                .data("error", null);
    }

    // --- Auth callback (Keycloak redirects back here) ---

    @GET
    @Path("/auth/callback")
    public Response authCallback(@QueryParam("code") String code,
                                 @QueryParam("error") String error,
                                 @QueryParam("error_description") String errorDescription,
                                 @CookieParam(SESSION_COOKIE) String sessionId) {
        OidcFlowState state = sessionStore.getOrCreateSession(sessionId);

        if (error != null) {
            LOG.infof("Auth callback error: %s - %s", error, errorDescription);
            state.setAuthError(error);
            state.setAuthErrorDescription(errorDescription);
            state.setCurrentStep("authentication");
            return Response.seeOther(URI.create("/?step=authentication")).build();
        }

        if (code != null) {
            LOG.infof("Auth callback received code: %s...", code.substring(0, Math.min(10, code.length())));
            state.setAuthorizationCode(code);
            state.setCurrentStep("token");
            return Response.seeOther(URI.create("/?step=token")).build();
        }

        return Response.seeOther(URI.create("/")).build();
    }

    // --- Token exchange ---

    @POST
    @Path("/token")
    @Produces(MediaType.TEXT_HTML)
    public Uni<TemplateInstance> exchangeToken(@CookieParam(SESSION_COOKIE) String sessionId) {
        OidcFlowState state = sessionStore.getOrCreateSession(sessionId);

        if (state.getDiscovery() == null || state.getAuthorizationCode() == null) {
            return Uni.createFrom().item(
                    tokenResult.data("error", "Missing discovery or authorization code")
                            .data("tokenResponseJson", null)
                            .data("tokenRequestFormatted", null)
                            .data("idTokenHeader", null)
                            .data("idTokenPayload", null)
                            .data("idTokenSignature", null));
        }

        String tokenEndpoint = (String) state.getDiscovery().get("token_endpoint");
        Map<String, String> params = new LinkedHashMap<>();
        params.put("grant_type", "authorization_code");
        params.put("code", state.getAuthorizationCode());
        params.put("client_id", state.getClientId());
        params.put("redirect_uri", state.getRedirectUri());

        String requestFormatted = tokenEndpoint + "\n\n"
                + "grant_type=authorization_code\n"
                + "code=" + state.getAuthorizationCode() + "\n"
                + "client_id=" + state.getClientId() + "\n"
                + "redirect_uri=" + state.getRedirectUri();

        return keycloakProxy.exchangeToken(tokenEndpoint, params)
                .onItem().transform(response -> {
                    String responseJson = keycloakProxy.toFormattedJson(response);
                    state.setLastTokenResponse(responseJson);

                    String idTokenHdr = null, idTokenPld = null, idTokenSig = null;
                    Object idTokenRaw = response.get("id_token");
                    if (idTokenRaw != null) {
                        String idTokenStr = idTokenRaw.toString();
                        state.setIdToken(idTokenStr);
                        String[] parts = idTokenStr.split("\\.");
                        if (parts.length == 3) {
                            idTokenHdr = prettyJson(base64UrlDecode(parts[0]));
                            idTokenPld = prettyJson(base64UrlDecode(parts[1]));
                            idTokenSig = parts[2];
                        }
                    }

                    Object refreshTokenRaw = response.get("refresh_token");
                    if (refreshTokenRaw != null) {
                        state.setRefreshToken(refreshTokenRaw.toString());
                    }
                    Object accessTokenRaw = response.get("access_token");
                    if (accessTokenRaw != null) {
                        state.setAccessToken(accessTokenRaw.toString());
                    }

                    return tokenResult.data("tokenResponseJson", responseJson)
                            .data("tokenRequestFormatted", requestFormatted)
                            .data("idTokenHeader", idTokenHdr)
                            .data("idTokenPayload", idTokenPld)
                            .data("idTokenSignature", idTokenSig)
                            .data("error", null);
                });
    }

    // --- Refresh ---

    @POST
    @Path("/refresh")
    @Produces(MediaType.TEXT_HTML)
    public Uni<TemplateInstance> refreshToken(@CookieParam(SESSION_COOKIE) String sessionId) {
        OidcFlowState state = sessionStore.getOrCreateSession(sessionId);

        if (state.getDiscovery() == null || state.getRefreshToken() == null) {
            return Uni.createFrom().item(
                    refreshResult.data("error", "Missing discovery or refresh token")
                            .data("refreshResponseJson", null)
                            .data("refreshRequestFormatted", null)
                            .data("idTokenRefreshed", null));
        }

        String tokenEndpoint = (String) state.getDiscovery().get("token_endpoint");
        Map<String, String> params = new LinkedHashMap<>();
        params.put("grant_type", "refresh_token");
        params.put("refresh_token", state.getRefreshToken());
        params.put("client_id", state.getClientId());
        params.put("scope", "openid");

        String requestFormatted = tokenEndpoint + "\n\n"
                + "grant_type=refresh_token\n"
                + "refresh_token=" + state.getRefreshToken() + "\n"
                + "client_id=" + state.getClientId() + "\n"
                + "scope=openid";

        return keycloakProxy.exchangeToken(tokenEndpoint, params)
                .onItem().transform(response -> {
                    String responseJson = keycloakProxy.toFormattedJson(response);
                    state.setLastRefreshResponse(responseJson);

                    String refreshedIdToken = null;
                    Object idTokenRaw = response.get("id_token");
                    if (idTokenRaw != null) {
                        String idTokenStr = idTokenRaw.toString();
                        String[] parts = idTokenStr.split("\\.");
                        if (parts.length >= 2) {
                            refreshedIdToken = prettyJson(base64UrlDecode(parts[1]));
                        }
                    }

                    Object refreshTokenRaw = response.get("refresh_token");
                    if (refreshTokenRaw != null) {
                        state.setRefreshToken(refreshTokenRaw.toString());
                    }

                    return refreshResult.data("refreshResponseJson", responseJson)
                            .data("refreshRequestFormatted", requestFormatted)
                            .data("idTokenRefreshed", refreshedIdToken)
                            .data("error", null);
                });
    }

    // --- UserInfo ---

    @POST
    @Path("/userinfo")
    @Produces(MediaType.TEXT_HTML)
    public Uni<TemplateInstance> getUserInfo(@CookieParam(SESSION_COOKIE) String sessionId) {
        OidcFlowState state = sessionStore.getOrCreateSession(sessionId);

        if (state.getDiscovery() == null || state.getAccessToken() == null) {
            return Uni.createFrom().item(
                    userinfoResult.data("error", "Missing discovery or access token")
                            .data("userInfoResponseJson", null)
                            .data("userInfoRequestFormatted", null));
        }

        String userinfoEndpoint = (String) state.getDiscovery().get("userinfo_endpoint");

        String requestFormatted = userinfoEndpoint + "\n\n"
                + "Authorization: Bearer " + state.getAccessToken();

        return keycloakProxy.fetchUserInfo(userinfoEndpoint, state.getAccessToken())
                .onItem().transform(response -> {
                    String responseJson = keycloakProxy.toFormattedJson(response);
                    state.setLastUserInfoResponse(responseJson);

                    return userinfoResult.data("userInfoResponseJson", responseJson)
                            .data("userInfoRequestFormatted", requestFormatted)
                            .data("error", null);
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
        OidcFlowState state = sessionStore.getSession(sessionId);

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

    private TemplateInstance renderStepTemplate(String stepName, OidcFlowState state) {
        return switch (stepName) {
            case "discovery" -> discovery
                    .data("defaultIssuer", defaultIssuer)
                    .data("state", state)
                    .data("discoveryJson", state.getDiscovery() != null
                            ? keycloakProxy.toFormattedJson(state.getDiscovery()) : null);
            case "authentication" -> authentication
                    .data("state", state)
                    .data("authUrl", state.getAuthenticationRequestUrl())
                    .data("authRequestFormatted", state.getAuthenticationRequestUrl() != null
                            ? state.getAuthenticationRequestUrl().replace("?", "\n\n").replace("&", "\n") : null);
            case "token" -> token
                    .data("state", state)
                    .data("tokenResponseJson", state.getLastTokenResponse());
            case "refresh" -> refresh
                    .data("state", state)
                    .data("refreshResponseJson", state.getLastRefreshResponse());
            case "userinfo" -> userinfo
                    .data("state", state)
                    .data("userInfoResponseJson", state.getLastUserInfoResponse());
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
