# Quarkus OAuth 2.0 Playground

This is a Quarkus-based implementation of the OAuth 2.0 playground, providing the same functionality as the Node.js version (`nodejs/02-Oauth2`) but using Quarkus framework.

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
./mvnw quarkus:dev

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
./mvnw clean package -Dquarkus.kubernetes.deploy=true

# Deploy frontend
cd ../frontend
./mvnw clean package -Dquarkus.kubernetes.deploy=true
```

### Post-Deployment Configuration

Update the environment variables in OpenShift:

```bash
# Get the backend service URL (internal K8s service)
BACKEND_URL="http://quarkus-backend:8080"

# Update backend with your Keycloak settings
oc set env deployment/quarkus-backend \
  QUARKUS_OIDC_AUTH_SERVER_URL="https://sso.apps.example.com/realms/demo" \
  QUARKUS_OIDC_CLIENT_ID="quarkus-oauth-backend" \
  QUARKUS_OIDC_CREDENTIALS_SECRET="<your-backend-secret>" \
  QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT="http://otel-collector:4317"

# Update frontend with your Keycloak settings and backend URL
oc set env deployment/quarkus-frontend \
  QUARKUS_OIDC_AUTH_SERVER_URL="https://sso.apps.example.com/realms/demo" \
  QUARKUS_OIDC_CLIENT_ID="quarkus-oauth-playground" \
  QUARKUS_OIDC_CREDENTIALS_SECRET="<your-frontend-secret>" \
  OAUTH_SERVICE_URL="${BACKEND_URL}" \
  QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT="http://otel-collector:4317"
```

## OpenTelemetry Tracing

Both applications are instrumented with OpenTelemetry for distributed tracing:

- **Service names**: `quarkus-frontend`, `quarkus-backend`
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
