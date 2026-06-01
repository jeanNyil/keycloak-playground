package io.jeannyil;

import java.time.Instant;
import java.util.Map;

import io.quarkus.qute.TemplateData;

/**
 * Holds the OAuth 2.0 flow state for a single browser session.
 * Replaces the localStorage-based state from the vanilla JS version.
 */
@TemplateData
public class OAuthFlowState {

    private String currentStep = "discovery";
    private String issuer;
    private Map<String, Object> discovery;

    // Authorization parameters
    private String clientId;
    private String scope;
    private String redirectUri;

    // Token state
    private String authorizationCode;
    private String accessToken;
    private String refreshToken;
    private String idToken;

    // Last responses (stored as formatted strings for display)
    private String lastTokenResponse;
    private String lastPublicResponse;
    private String lastSecuredResponse;

    // Auth error from callback
    private String authError;
    private String authErrorDescription;

    private final Instant createdAt = Instant.now();

    public String getCurrentStep() { return currentStep; }
    public void setCurrentStep(String currentStep) { this.currentStep = currentStep; }

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }

    public Map<String, Object> getDiscovery() { return discovery; }
    public void setDiscovery(Map<String, Object> discovery) { this.discovery = discovery; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }

    public String getAuthorizationCode() { return authorizationCode; }
    public void setAuthorizationCode(String authorizationCode) { this.authorizationCode = authorizationCode; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public String getIdToken() { return idToken; }
    public void setIdToken(String idToken) { this.idToken = idToken; }

    public String getLastTokenResponse() { return lastTokenResponse; }
    public void setLastTokenResponse(String lastTokenResponse) { this.lastTokenResponse = lastTokenResponse; }

    public String getLastPublicResponse() { return lastPublicResponse; }
    public void setLastPublicResponse(String lastPublicResponse) { this.lastPublicResponse = lastPublicResponse; }

    public String getLastSecuredResponse() { return lastSecuredResponse; }
    public void setLastSecuredResponse(String lastSecuredResponse) { this.lastSecuredResponse = lastSecuredResponse; }

    public String getAuthError() { return authError; }
    public void setAuthError(String authError) { this.authError = authError; }

    public String getAuthErrorDescription() { return authErrorDescription; }
    public void setAuthErrorDescription(String authErrorDescription) { this.authErrorDescription = authErrorDescription; }

    public Instant getCreatedAt() { return createdAt; }
}
