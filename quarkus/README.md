# Quarkus OAuth 2.0 Playground

This is a Quarkus-based implementation of the OAuth 2.0 playground, providing the same functionality as the Node.js version (`nodejs/02-Oauth2`) but using Quarkus framework.

## Prerequisites

1. **Keycloak Server** - Running instance accessible via URL
2. **Keycloak Clients** - Two clients configured in your realm:
   - **Frontend**: `quarkus-oauth-playground` (public client, no secret required)
   - **Backend**: `quarkus-oauth-backend` (confidential client with secret)
3. **Client Role** - Create a `user` role in the `quarkus-oauth-backend` client
4. **Test User** - A user with the `quarkus-oauth-backend:user` role assigned

## Configuration

Before running the applications, you must configure your Keycloak settings.

### Backend Configuration

Edit `quarkus/backend/src/main/resources/application.properties`:

```properties
# Update these values with your Keycloak instance details
quarkus.oidc.auth-server-url=https://your-keycloak-server/realms/your-realm
quarkus.oidc.client-id=quarkus-oauth-backend
quarkus.oidc.credentials.secret=your-backend-client-secret
```

### Frontend Configuration

Edit `quarkus/frontend/src/main/resources/application.properties`:

```properties
# Update these values with your Keycloak instance details
quarkus.oidc.auth-server-url=https://your-keycloak-server/realms/your-realm
quarkus.oidc.client-id=quarkus-oauth-playground
# No client secret needed - this is a public client
oauth.service.url=http://localhost:8081  # Backend URL
```

**Note**: The frontend client (`quarkus-oauth-playground`) should be configured as a **public client** in Keycloak with **Client authentication: OFF**. Public clients do not use client secrets.

## Architecture

- **Frontend** (`quarkus/frontend`): Web application with REST API proxy endpoints for distributed tracing
  - Static HTML/CSS/JavaScript UI for OAuth 2.0 flow exploration
  - REST endpoints that proxy requests to Keycloak and backend service
  - OpenTelemetry instrumentation for tracing
  
- **Backend** (`quarkus/backend`): Service with secured and public endpoints
  - Public endpoint: `/public` (no authentication)
  - Secured endpoint: `/secured` (requires `quarkus-backend:user` role)
  - Token validation via Quarkus OIDC
  - OpenTelemetry instrumentation for tracing

## Prerequisites

### Keycloak Configuration

You need a Keycloak realm configured with:

1. **Frontend Client** (`quarkus-oauth-playground`):
   - Client ID: `quarkus-oauth-playground`
   - Access Type: `public`
   - Valid Redirect URIs: `http://localhost:8080/*`, `https://<your-route>/*`
   - Web Origins: `*` or specific origins

2. **Backend Client** (`quarkus-oauth-backend`):
   - Client ID: `quarkus-oauth-backend`
   - Access Type: `confidential`
   - **Client Role**: `user` (required for `/secured` endpoint)
   - Service Accounts Enabled: `true` or Bearer-only

3. **Test User**: A user with the `quarkus-oauth-backend:user` client role assigned

> **Note**: The `user` client role must be created manually in the Keycloak Admin UI as client roles cannot be imported via the UI.

## Local Development

### Backend

```bash
cd quarkus/backend

# Update application.properties with your Keycloak settings
# - quarkus.oidc.auth-server-url
# - quarkus.oidc.client-id  
# - quarkus.oidc.credentials.secret

# Run in dev mode
./mvnw quarkus:dev -Dquarkus.http.port=8081

# The backend will start on http://localhost:8081
```

Endpoints:
- `GET /public` - Public message (no auth required)
- `GET /secured` - Secret message (requires `quarkus-backend:user` role)
- `GET /q/health/live` - Liveness probe (SmallRye Health)
- `GET /q/health/ready` - Readiness probe (SmallRye Health)

### Frontend

```bash
cd quarkus/frontend

# Update application.properties with your settings
# - quarkus.oidc.auth-server-url
# - quarkus.oidc.client-id
# - quarkus.oidc.credentials.secret
# - oauth.service.url (backend URL, default: http://localhost:8081)

# Run in dev mode
./mvnw quarkus:dev

# The frontend will start on http://localhost:8080
```

Access the playground at `http://localhost:8080`

## Building Native Images

### Backend
```bash
cd quarkus/backend
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

### Frontend
```bash
cd quarkus/frontend
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

## Deploy to OpenShift

### Using Quarkus OpenShift Extension

The applications include the Quarkus OpenShift extension which can build and deploy directly to OpenShift.

**Note**: OpenShift will automatically configure liveness and readiness probes using SmallRye Health endpoints (`/q/health/live` and `/q/health/ready`). No manual probe configuration is needed.

```bash
# Login to OpenShift
oc login <your-cluster-url>

# Create or switch to your project
oc project <your-project>

# Deploy backend
cd quarkus/backend
./mvnw clean package -Dquarkus.openshift.deploy=true

# Deploy frontend
cd ../frontend
./mvnw clean package -Dquarkus.openshift.deploy=true
```

### Pre-Deployment Configuration

Before deploying to OpenShift, configure your Keycloak settings in the `src/main/kubernetes/openshift.yml` files. These files define the ConfigMaps and Secrets that will be created during deployment.

#### Backend Configuration

Edit `quarkus/backend/src/main/kubernetes/openshift.yml`:

```yaml
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: quarkus-oauth-playground-backend-config
data:
  application.properties: |
    quarkus.otel.exporter.otlp.endpoint=http://otel-collector:4317
    quarkus.oidc.auth-server-url=https://sso.apps.example.com/realms/demo
---
apiVersion: v1
kind: Secret
metadata:
  name: quarkus-oauth-playground-backend-secret
stringData:
  application.properties: |
    quarkus.oidc.credentials.secret=<your-backend-client-secret>
type: Opaque
```

#### Frontend Configuration

Edit `quarkus/frontend/src/main/kubernetes/openshift.yml`:

```yaml
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: quarkus-oauth-playground-frontend-config
data:
  application.properties: |
    quarkus.otel.exporter.otlp.endpoint=http://otel-collector:4317
    quarkus.oidc.auth-server-url=https://sso.apps.example.com/realms/demo
    oauth.service.url=http://quarkus-oauth-playground-backend:80
```

**Note**: 
- Update `quarkus.oidc.auth-server-url` with your Keycloak server URL and realm
- Update `quarkus.oidc.credentials.secret` in the backend secret with your actual client secret from Keycloak
- The frontend is a **public client** and does not require a Secret resource
- The backend service URL uses the internal Kubernetes service name: `http://quarkus-oauth-playground-backend:80`
- These ConfigMaps and Secrets are automatically deployed with the application

## OpenTelemetry Tracing

Both applications are instrumented with OpenTelemetry for distributed tracing:

- **Service names**: `quarkus-oauth-playground-frontend`, `quarkus-oauth-playground-backend`
- **Exporter**: OTLP/gRPC
- **Propagation**: W3C Trace Context

### Trace Flow

```
┌──────────┐      ┌─────────────────────┐      ┌──────────────┐
│          │      │                     │      │              │
│  Browser │─────▶│  Quarkus Frontend   │─────▶│   Keycloak   │
│          │      │  (REST + OTel)      │      │   (OTel)     │
│          │      │                     │      │              │
└──────────┘      └──────────┬──────────┘      └──────────────┘
                             │                        
                             ▼                        
                  ┌─────────────────────┐             
                  │                     │             
                  │  Quarkus Backend    │             
                  │  (REST + OTel +     │             
                  │   OIDC)             │             
                  │                     │             
                  └─────────────────────┘             
                             │
                             ▼
              ┌─────────────────────────┐
              │   OpenTelemetry         │
              │   Collector             │
              └─────────────────────────┘
```

### Local Development with LGTM Stack

Quarkus includes dev services that automatically start the Grafana LGTM stack when running in dev mode:

```bash
# Start in dev mode (LGTM stack starts automatically)
./mvnw quarkus:dev

# Access Grafana at http://localhost:3000
# Traces are automatically sent to Tempo
# Logs are automatically sent to Loki
# Metrics are automatically sent to Prometheus
```

## Features

### Frontend Features
- ✅ OIDC Discovery exploration
- ✅ OAuth 2.0 Authorization Code Flow
- ✅ Token inspection (header, payload, signature)
- ✅ Public and secured endpoint testing
- ✅ Logout functionality
- ✅ Distributed tracing via proxy endpoints

### Backend Features
- ✅ Public endpoint (no authentication)
- ✅ Secured endpoint with role-based access control
- ✅ Token validation via Quarkus OIDC
- ✅ Detailed logging with OpenTelemetry trace context
- ✅ Health checks and metrics

## Comparison with Node.js Version

| Feature | Node.js (`nodejs/02-Oauth2`) | Quarkus |
|---------|----------------------|---------|
| Framework | Express | Quarkus REST |
| Runtime | Node.js | JVM / Native |
| Startup Time (JVM) | ~1s | ~1-2s |
| Startup Time (Native) | N/A | ~0.01s |
| Memory (JVM) | ~50MB | ~100MB |
| Memory (Native) | N/A | ~20MB |
| OIDC Library | keycloak-connect | quarkus-oidc |
| OpenTelemetry | Manual setup | Built-in |
| Health Checks | Custom | Built-in (SmallRye Health) |
| Metrics | Custom | Built-in (Micrometer) |

## Health Checks and Metrics

Both applications include SmallRye Health for Kubernetes/OpenShift health probes:

### Health Endpoints

- **Liveness probe**: `GET /q/health/live`
  - Checks if the application is alive and running
  - Returns HTTP 200 if healthy, 503 if unhealthy
  
- **Readiness probe**: `GET /q/health/ready`
  - Checks if the application is ready to accept traffic
  - Includes OIDC health check (validates Keycloak connection)
  - Returns HTTP 200 if ready, 503 if not ready

### Example

```bash
# Check backend health
curl http://localhost:8081/q/health/live
curl http://localhost:8081/q/health/ready

# Check frontend health
curl http://localhost:8080/q/health/live
curl http://localhost:8080/q/health/ready
```

### Metrics

Prometheus metrics are available at:
- `GET /q/metrics` - All application and JVM metrics

OpenShift ServiceMonitor will automatically scrape these endpoints when deployed.

## Troubleshooting

### Invalid Redirect URI Error

If you see `invalid_redirect_uri` errors in Keycloak logs:

1. Ensure `/q/*` paths are excluded from authentication (for metrics/health)
2. Add your application URL to Valid Redirect URIs in Keycloak client
3. Check that `oauth.service.url` points to the correct backend URL

### 403 Forbidden on /secured Endpoint

1. Verify the user has the `quarkus-oauth-backend:user` client role
2. Check that `quarkus.oidc.roles.role-claim-path` is set correctly
3. Ensure the access token includes the `resource_access.quarkus-oauth-backend.roles` claim

### Token Validation Failed

1. Verify the backend `quarkus.oidc.auth-server-url` matches the token issuer
2. Check that the client secret is correct
3. Ensure the token is sent in the `Authorization: Bearer <token>` header

## Related Documentation

- [Quarkus OIDC](https://quarkus.io/guides/security-oidc-bearer-token-authentication)
- [Quarkus OpenTelemetry](https://quarkus.io/guides/opentelemetry)
- [Quarkus OpenShift](https://quarkus.io/guides/deploying-to-openshift)
