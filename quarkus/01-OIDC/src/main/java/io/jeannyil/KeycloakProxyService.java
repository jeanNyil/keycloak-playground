package io.jeannyil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.WebClient;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Reactive proxy for Keycloak endpoints using Vert.x WebClient.
 * Preserves distributed tracing (OpenTelemetry) across the call chain.
 * Returns parsed Maps so the resource layer can pass data to Qute templates.
 */
@Singleton
public class KeycloakProxyService {

    private static final Logger LOG = Logger.getLogger(KeycloakProxyService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Inject
    Vertx vertx;

    @Inject
    ObjectMapper objectMapper;

    private WebClient webClient;

    @PostConstruct
    void initialize() {
        this.webClient = WebClient.create(vertx);
    }

    public Uni<Map<String, Object>> fetchDiscovery(String issuer) {
        String discoveryUrl = issuer + "/.well-known/openid-configuration";
        LOG.infof("GET /api/keycloak/discovery → %s", discoveryUrl);

        return webClient.getAbs(discoveryUrl)
                .send()
                .onItem().transform(response -> {
                    LOG.info("  └─ ✓ Discovery loaded successfully");
                    return parseJson(response.bodyAsString());
                })
                .onFailure().recoverWithItem(e -> {
                    LOG.errorf("  └─ ✗ Error fetching discovery: %s", e.getMessage());
                    return errorMap("Error fetching discovery: " + e.getMessage());
                });
    }

    public Uni<Map<String, Object>> exchangeToken(String tokenEndpoint, Map<String, String> params) {
        LOG.infof("POST /api/keycloak/token → %s", tokenEndpoint);
        LOG.infof("  └─ grant_type: %s", params.get("grant_type"));

        StringBuilder formData = new StringBuilder();
        params.forEach((key, value) -> {
            if (value != null && !key.equals("token_endpoint")) {
                if (formData.length() > 0) {
                    formData.append("&");
                }
                formData.append(key).append("=").append(URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        });

        return webClient.postAbs(tokenEndpoint)
                .putHeader("Content-Type", "application/x-www-form-urlencoded")
                .sendBuffer(Buffer.buffer(formData.toString()))
                .onItem().transform(response -> {
                    if (response.statusCode() == 200) {
                        LOG.info("  └─ ✓ Token exchange successful");
                    } else {
                        LOG.infof("  └─ ✗ Token exchange failed: %d", response.statusCode());
                    }
                    String body = response.bodyAsString();
                    return body != null ? parseJson(body) : errorMap("Empty response");
                })
                .onFailure().recoverWithItem(e -> {
                    LOG.errorf(e, "  └─ ✗ Error exchanging token: %s", e.getMessage());
                    return errorMap("Error exchanging token: " + e.getMessage());
                });
    }

    public Uni<Map<String, Object>> fetchUserInfo(String userinfoEndpoint, String accessToken) {
        LOG.infof("GET /api/keycloak/userinfo → %s", userinfoEndpoint);

        var request = webClient.getAbs(userinfoEndpoint);
        if (accessToken != null) {
            request.putHeader("Authorization", "Bearer " + accessToken);
        }

        return request.send()
                .onItem().transform(response -> {
                    if (response.statusCode() == 200) {
                        LOG.info("  └─ ✓ UserInfo retrieved successfully");
                    } else {
                        LOG.infof("  └─ ✗ UserInfo failed: %d", response.statusCode());
                    }
                    return parseJson(response.bodyAsString());
                })
                .onFailure().recoverWithItem(e -> {
                    LOG.errorf("  └─ ✗ Error fetching userinfo: %s", e.getMessage());
                    return errorMap("Error fetching userinfo: " + e.getMessage());
                });
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            Map<String, Object> map = new HashMap<>();
            map.put("raw", json);
            return map;
        }
    }

    private Map<String, Object> errorMap(String message) {
        Map<String, Object> map = new HashMap<>();
        map.put("error", message);
        return map;
    }

    public String toFormattedJson(Object obj) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return String.valueOf(obj);
        }
    }
}
