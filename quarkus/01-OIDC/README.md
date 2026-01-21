# Quarkus OIDC Playground

Interactive playground for exploring OpenID Connect authentication flows with Keycloak/RHBK, built with Quarkus.

This is a Quarkus-based implementation that provides the same functionality as the Node.js version (`nodejs/01-OIDC`), featuring step-by-step visualization of the OIDC authentication flow.

## Features

- ✅ OIDC Discovery endpoint exploration
- ✅ Authentication Request generation
- ✅ Token inspection (ID Token, Access Token, Refresh Token)
- ✅ Token refresh flow
- ✅ UserInfo endpoint testing
- ✅ Logout functionality
- ✅ Dynamic issuer configuration from backend
- ✅ Distributed tracing with OpenTelemetry
- ✅ SmallRye Health checks
- ✅ Native image support

## Prerequisites

1. **Keycloak Server** - Running instance accessible via URL
2. **Keycloak Client** - Public client configured in your realm:
   - Client ID: `quarkus-oidc-playground`
   - Client authentication: `OFF` (public client)
   - Standard flow enabled: `ON`
   - Valid Redirect URIs:
     - For local dev: `http://localhost:8080/*`
     - For OpenShift: `https://<your-openshift-route>/*`
     - Example: `https://quarkus-oidc-playground-rhbk-playground.apps.ocp4.jnyilimb.eu/*`
   - Web Origins: `*` or specific origins (e.g., `https://<your-openshift-route>`)

## Configuration

The application uses `application.properties` for configuration. The issuer URL configured here is automatically populated in the UI on page load.

Edit `src/main/resources/application.properties`:

```properties
keycloak.url=https://your-keycloak-server
keycloak.issuer=https://your-keycloak-server/realms/your-realm
```

### Configuration Properties

- `keycloak.url`: Base URL of your Keycloak server (used for reference)
- `keycloak.issuer`: Full issuer URL (realm-specific) - **automatically loaded by the UI as the default issuer**

The UI fetches the default issuer from the backend via `GET /api/config`, ensuring a single source of truth for environment-specific configuration.

## Local Development

```bash
cd quarkus/01-OIDC

# Update application.properties with your Keycloak URL and issuer
# Run in dev mode
./mvnw quarkus:dev

# The playground will start on http://localhost:8080
```

Access the playground at `http://localhost:8080`

### With LGTM Dev Service

Quarkus automatically starts the Grafana LGTM stack when running in dev mode:

```bash
./mvnw quarkus:dev

# Access Quarkus Dev UI at http://localhost:8080/q/dev-ui
# The Grafana URL can be discovered from the Dev UI under "Observability"
```

## Building Native Images

```bash
./mvnw package -Pnative -Dquarkus.native.native-image-xmx=7g
```

>**NOTE**: The project is configured to use a container runtime for native builds. See `quarkus.native.container-build=true` in `application.properties`. Adjust the `quarkus.native.native-image-xmx` value according to your container runtime available memory resources.

You can then execute your native executable with: `./target/quarkus-oidc-playground-1.0.0-SNAPSHOT-runner`

>**NOTE**: If you're on Apple Silicon and built the native image inside a Linux container, the result is a Linux ELF binary. macOS can't execute Linux binaries, so you'll get "exec format error". Build and run the container image instead:

```bash
podman build -f src/main/docker/Dockerfile.native -t quarkus-oidc-playground .
podman run --rm --name quarkus-oidc-playground \
  -p 8080:8080 \
  -e KEYCLOAK_URL=https://sso.apps.example.com \
  -e KEYCLOAK_ISSUER=https://sso.apps.example.com/realms/demo \
  -e QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT=http://host.containers.internal:4317 \
  quarkus-oidc-playground
```

## Deploy to OpenShift

### Pre-Deployment Configuration

Edit `src/main/kubernetes/openshift.yml`:

```yaml
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: quarkus-oidc-playground-config
data:
  application.properties: |
    quarkus.otel.exporter.otlp.endpoint=http://otel-collector:4317
    keycloak.url=https://sso.apps.example.com
    keycloak.issuer=https://sso.apps.example.com/realms/demo
```

### Deploy Using Quarkus OpenShift Extension

```bash
# Login to OpenShift
oc login <your-cluster-url>

# Create or switch to your project
oc project <your-project>

# Deploy
cd quarkus/01-OIDC
./mvnw clean package -Dquarkus.openshift.deploy=true

# Get the route URL
oc get route quarkus-oidc-playground -o jsonpath='{.spec.host}'
```

**Important**: After deployment, update your Keycloak client's Valid Redirect URIs to include:
```
https://<route-from-above>/*
```

For example:
```
https://quarkus-oidc-playground-rhbk-playground.apps.ocp4.jnyilimb.eu/*
```

## How to Use

### Step 1: Discovery
1. The issuer URL is automatically populated from the backend configuration (`keycloak.issuer` property)
2. You can override it by entering a different Keycloak issuer URL (e.g., `https://sso.example.com/realms/demo`)
3. Click "Load OpenID Provider Configuration"
4. View the OIDC discovery document with available endpoints

### Step 2: Authentication
1. Enter your client_id (e.g., `quarkus-oidc-playground`)
2. Configure scope, prompt, max_age, and login_hint as needed
3. Click "Generate Authentication Request"
4. Click the generated link to authenticate with Keycloak

### Step 3: Token Exchange
1. After authentication, the authorization code is automatically captured
2. Click "Load Tokens" to exchange the code for tokens
3. View the ID Token (header, payload, signature)

### Step 4: Refresh Token
1. Click "Refresh Token" to use the refresh token
2. View the new tokens received

### Step 5: UserInfo
1. Click "Load UserInfo" to retrieve user information
2. View the user profile data from Keycloak

## Comparison with Node.js Version

| Feature | Node.js (`nodejs/01-OIDC`) | Quarkus |
|---------|---------------------------|---------|
| Framework | Express | Quarkus REST |
| Runtime | Node.js | JVM / Native |
| Startup Time (JVM) | ~1s | ~1-2s |
| Startup Time (Native) | N/A | ~0.01s |
| Memory (JVM) | ~50MB | ~100MB |
| Memory (Native) | N/A | ~20MB |
| OpenTelemetry | Manual setup | Built-in |
| Health Checks | Custom | Built-in (SmallRye Health) |
| Metrics | Custom | Built-in (Micrometer) |

## Health Checks

### Endpoints

- **Liveness probe**: `GET /q/health/live`
  - Checks if the application is alive and running
  
- **Readiness probe**: `GET /q/health/ready`
  - Checks if the application is ready to accept traffic

### Example

```bash
curl http://localhost:8080/q/health/live
curl http://localhost:8080/q/health/ready
```

## OpenTelemetry Tracing

The application is instrumented with OpenTelemetry for distributed tracing:

- **Service name**: `quarkus-oidc-playground`
- **Exporter**: OTLP/gRPC
- **Propagation**: W3C Trace Context

All proxy endpoints (`/api/keycloak/*` and `/api/config`) are automatically traced, enabling end-to-end visibility of OIDC flows.

## Troubleshooting

### Invalid Redirect URI Error

If you see `invalid_redirect_uri` errors:
1. **Verify you're configuring the client in the correct realm** (check the issuer URL - e.g., `/realms/demo`)
2. Add your application URL to Valid Redirect URIs in the Keycloak client
3. Ensure the URL matches exactly (including trailing slash behavior)
4. Check Keycloak server logs for the exact error:
   ```bash
   oc logs -f <keycloak-pod> | grep -i 'invalid.*redirect'
   ```

### CORS Errors

If you see CORS errors in the browser:
1. Add your application origin to Web Origins in the Keycloak client
2. Or set Web Origins to `*` for testing

## Related Documentation

- [Quarkus OpenTelemetry](https://quarkus.io/guides/opentelemetry)
- [Quarkus OpenShift](https://quarkus.io/guides/deploying-to-openshift)
- [OpenID Connect Specification](https://openid.net/specs/openid-connect-core-1_0.html)
