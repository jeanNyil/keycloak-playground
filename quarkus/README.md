# Keycloak Playground - Quarkus Implementation

Cloud-native implementations of OIDC and OAuth 2.0 playgrounds using Quarkus framework.

- [OpenID Connect Playground](./01-OIDC/README.md)
- [OAuth 2.0 Playground](./02-Oauth2/README.md)

---

## Prerequisites

Before running the playgrounds, you need:

### Keycloak Server

A running instance of Keycloak (or Red Hat build of Keycloak) - either:
- **Local**: Keycloak running on `http://localhost:8080`
- **Remote**: Keycloak accessible via URL (e.g., `https://sso.apps.example.com`)

### Realm Configuration

A configured realm with the following:
- **Realm name**: e.g., `demo` (or your custom realm)
- **OIDC Client**: For the OIDC Playground
  - Client ID: `quarkus-oidc-playground`
  - Client authentication: `OFF` (public client)
  - Standard flow enabled: `ON`
  - Valid Redirect URIs: 
    - `http://localhost:8080/*` (local dev)
    - `https://<your-openshift-route>/*` (OpenShift)
  - Web Origins: `*` or specific origins
- **OAuth Frontend Client**: For the OAuth Playground frontend
  - Client ID: `quarkus-oauth-playground`
  - Client authentication: `OFF` (public client)
  - Standard flow enabled: `ON`
  - Valid Redirect URIs: 
    - `http://localhost:8080/*` (local dev)
    - `https://<your-openshift-route>/*` (OpenShift)
  - Web Origins: `*` or specific origins
- **OAuth Backend Client**: For the OAuth Playground backend
  - Client ID: `quarkus-oauth-backend`
  - Client authentication: `ON` (confidential client with secret)
  - **Client Role**: `user` (required for secured endpoint access)
- **Test User**: A user with the `quarkus-oauth-backend:user` client role assigned

> **Important**: After creating the `quarkus-oauth-backend` client, you must manually create the `user` client role in the Keycloak Admin UI:
> 1. Navigate to **Clients** → **quarkus-oauth-backend** → **Roles** tab
> 2. Click **Create Role**
> 3. Set **Role Name** to `user`
> 4. Save the role
> 5. Assign this role to your test user via **Users** → *[select user]* → **Role Mapping** → **Client Roles** → **quarkus-oauth-backend**

---

## Local Development

### OIDC Playground

```bash
cd 01-OIDC

# Update application.properties with your Keycloak settings
# - keycloak.url
# - keycloak.issuer

# Run in dev mode (with LGTM observability stack)
./mvnw quarkus:dev

# Access the playground at http://localhost:8080
# Quarkus Dev UI at http://localhost:8080/q/dev-ui
```

### OAuth 2.0 Playground

Requires two terminals - backend and frontend:

```bash
# Terminal 1: Backend (port 8081)
cd 02-Oauth2/backend

# Update application.properties with your Keycloak settings
# - quarkus.oidc.auth-server-url
# - quarkus.oidc.client-id
# - quarkus.oidc.credentials.secret

./mvnw quarkus:dev -Dquarkus.http.port=8081
```

```bash
# Terminal 2: Frontend (port 8080)
cd 02-Oauth2/frontend

# Update application.properties with your Keycloak settings
# - quarkus.oidc.auth-server-url
# - quarkus.oidc.client-id
# - oauth.service.url (backend URL, default: http://localhost:8081)

./mvnw quarkus:dev

# Access the playground at http://localhost:8080
```

### LGTM Dev Service

Quarkus automatically starts the Grafana LGTM stack (Loki, Grafana, Tempo, Mimir) when running in dev mode:

- **Grafana**: Available through Quarkus Dev UI → Observability
- **Traces**: Automatically sent to Tempo
- **Logs**: Automatically sent to Loki  
- **Metrics**: Automatically sent to Prometheus/Mimir

No need to manually run Docker containers!

---

## Building Native Images

### OIDC Playground

```bash
cd 01-OIDC
./mvnw package -Pnative -Dquarkus.native.native-image-xmx=7g
```

### OAuth 2.0 Backend

```bash
cd 02-Oauth2/backend
./mvnw package -Pnative -Dquarkus.native.native-image-xmx=7g
```

### OAuth 2.0 Frontend

```bash
cd 02-Oauth2/frontend
./mvnw package -Pnative -Dquarkus.native.native-image-xmx=7g
```

> **Note**: Native builds use container runtime (`quarkus.native.container-build=true`). Adjust `quarkus.native.native-image-xmx` based on available memory.

> **SSL Support**: 
> - `01-OIDC`: Explicitly enabled via `quarkus.ssl.native=true` (uses Vert.x WebClient)
> - `02-Oauth2` (frontend/backend): Automatically enabled by `quarkus-oidc` extension

---

## Build and Push Container Images

### Using Quarkus Container Image Extension

```bash
# Login to your container registry
podman login quay.io

# OIDC Playground
cd 01-OIDC
./mvnw clean package \
  -Dquarkus.container-image.build=true \
  -Dquarkus.container-image.push=true \
  -Dquarkus.container-image.group=<YOUR_USERNAME> \
  -Dquarkus.container-image.registry=quay.io

# OAuth Backend
cd ../02-Oauth2/backend
./mvnw clean package \
  -Dquarkus.container-image.build=true \
  -Dquarkus.container-image.push=true \
  -Dquarkus.container-image.group=<YOUR_USERNAME> \
  -Dquarkus.container-image.registry=quay.io

# OAuth Frontend
cd ../frontend
./mvnw clean package \
  -Dquarkus.container-image.build=true \
  -Dquarkus.container-image.push=true \
  -Dquarkus.container-image.group=<YOUR_USERNAME> \
  -Dquarkus.container-image.registry=quay.io
```

---

## Deploy to OpenShift

### Pre-Deployment Configuration

Before deploying, configure your Keycloak settings in the `src/main/kubernetes/openshift.yml` files:

#### OIDC Playground

Edit `01-OIDC/src/main/kubernetes/openshift.yml`:

```yaml
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: quarkus-oidc-playground-config
data:
  application.properties: |
    quarkus.otel.exporter.otlp.endpoint=http://otel-collector:4317
    keycloak.issuer=https://sso.apps.example.com/realms/demo
```

#### OAuth Backend

Edit `02-Oauth2/backend/src/main/kubernetes/openshift.yml`:

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

#### OAuth Frontend

Edit `02-Oauth2/frontend/src/main/kubernetes/openshift.yml`:

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

### Deploy Using Quarkus OpenShift Extension

```bash
# Login to OpenShift
oc login <your-cluster-url>

# Create or switch to your project
oc project <your-project>

# Deploy OIDC Playground
cd 01-OIDC
./mvnw clean package -Dquarkus.openshift.deploy=true

# Deploy OAuth Backend (deploy first - frontend depends on its service)
cd ../02-Oauth2/backend
./mvnw clean package -Dquarkus.openshift.deploy=true

# Deploy OAuth Frontend
cd ../frontend
./mvnw clean package -Dquarkus.openshift.deploy=true
```

> **Note**: The Quarkus OpenShift extension automatically:
> - Creates ConfigMaps and Secrets from `src/main/kubernetes/openshift.yml`
> - Creates Deployments with SmallRye Health liveness/readiness probes
> - Exposes Services and Routes
> - Applies proper labels and annotations

---

## Verify Deployment

```bash
# Check deployments
oc get deployments

# Check pods
oc get pods

# Get route URLs
oc get routes

# View logs
oc logs -f deployment/quarkus-oidc-playground
oc logs -f deployment/quarkus-oauth-playground-frontend
oc logs -f deployment/quarkus-oauth-playground-backend
```

---

## Access Applications

Get your application URLs:

```bash
echo "OIDC Playground: https://$(oc get route quarkus-oidc-playground -o jsonpath='{.spec.host}')"
echo "OAuth Frontend:  https://$(oc get route quarkus-oauth-playground-frontend -o jsonpath='{.spec.host}')"
# Note: OAuth Backend has no external route - accessed via frontend proxy
```

---

## OpenTelemetry Instrumentation

All Quarkus applications have built-in OpenTelemetry instrumentation with automatic trace propagation:

- **OTLP/gRPC exporter** for traces, metrics, and logs
- **W3C Trace Context propagation** for distributed tracing
- **Automatic instrumentation** for REST, OIDC, and HTTP clients

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT` | OTLP collector endpoint (gRPC) | `http://localhost:4317` |
| `QUARKUS_APPLICATION_NAME` | Service name in traces | App-specific default |
| `QUARKUS_OTEL_SDK_DISABLED` | Disable OpenTelemetry | `false` |

### Trace Flow Diagrams

#### OIDC Playground

All Keycloak interactions use Vert.x WebClient with automatic trace propagation:

```
┌──────────┐      ┌─────────────────────┐      ┌──────────────┐
│          │      │                     │      │              │
│  Browser │─────▶│  OIDC Frontend      │─────▶│   Keycloak   │
│          │      │  (Quarkus + Vert.x  │      │   (OTel)     │
│          │      │   WebClient)        │      │              │
└──────────┘      └─────────────────────┘      └──────────────┘
     │                     │                          │
     │  /api/keycloak/*    │   /.well-known/*         │
     │  ─────────────────▶ │   /protocol/openid/*     │
     │                     │   ──────────────────────▶│
     │                     │                          │
     └─────────────────────┴──────────────────────────┘
                           │
                           ▼
              ┌─────────────────────────┐
              │   OpenTelemetry         │
              │   Collector             │
              └─────────────────────────┘
```

**Traced Operations:**
- `GET /api/config` → Configuration endpoint
- `GET /api/keycloak/discovery` → Keycloak OIDC Discovery
- `POST /api/keycloak/token` → Token Exchange
- `GET /api/keycloak/userinfo` → UserInfo Endpoint
- `GET /api/keycloak/logout` → Logout Redirect

#### OAuth Playground

Complete trace propagation across frontend, backend, and Keycloak:

```
┌──────────┐      ┌─────────────────────┐      ┌──────────────┐
│          │      │                     │      │              │
│  Browser │─────▶│  Quarkus Frontend   │─────▶│   Keycloak   │
│          │      │  (OIDC Web-App +    │      │   (OIDC)     │
│          │      │   Vert.x WebClient) │      │              │
└──────────┘      └──────────┬──────────┘      └──────────────┘
                             │                        ▲
                             │                        │
                             ▼                        │
                  ┌─────────────────────┐             │
                  │                     │  Token      │
                  │  Quarkus Backend    │  Validation │
                  │  (OIDC Service)     │─────────────┘
                  │                     │
                  └─────────────────────┘
                             │
                             ▼
              ┌─────────────────────────┐
              │   OpenTelemetry         │
              │   Collector             │
              └─────────────────────────┘
```

**Complete Trace Flow with REST Client:**

The frontend uses Quarkus REST Client with automatic trace propagation to call the backend:

```
┌──────────────────────────────────────────────────────────────────────┐
│                         Single Distributed Trace                     │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Browser Request                                                     │
│  ════════════════                                                    │
│       │                                                              │
│       ▼                                                              │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │ quarkus-oauth-playground-frontend                              │  │
│  │ ┌────────────────────────────────────────────────────────────┐ │  │
│  │ │ GET /api/keycloak/discovery (Vert.x WebClient)             │ │  │
│  │ │    └──▶ GET keycloak/.well-known/openid-configuration      │ │  │
│  │ └────────────────────────────────────────────────────────────┘ │  │
│  │ ┌────────────────────────────────────────────────────────────┐ │  │
│  │ │ POST /api/keycloak/token (Vert.x WebClient)                │ │  │
│  │ │    └──▶ POST keycloak/protocol/openid-connect/token        │ │  │
│  │ └────────────────────────────────────────────────────────────┘ │  │
│  │ ┌────────────────────────────────────────────────────────────┐ │  │
│  │ │ GET /api/service/secured (REST Client)                     │ │  │
│  │ │    └──▶ ┌────────────────────────────────────────────────┐ │ │  │
│  │ │         │ quarkus-oauth-playground-backend               │ │ │  │
│  │ │         │ ┌────────────────────────────────────────────┐ │ │ │  │
│  │ │         │ │ GET /secured                               │ │ │ │  │
│  │ │         │ │    └──▶ Keycloak (OIDC token validation)   │ │ │ │  │
│  │ │         │ └────────────────────────────────────────────┘ │ │ │  │
│  │ │         └────────────────────────────────────────────────┘ │ │  │
│  │ └────────────────────────────────────────────────────────────┘ │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### Viewing Traces

Traces can be viewed in any OpenTelemetry-compatible backend:

- **Grafana Tempo** - Query by service name or trace ID
- **Jaeger** - Search by service, operation, or tags
- **Zipkin** - Compatible with W3C trace context

Example trace search:
```bash
# Find traces by service name
service.name = "quarkus-oidc-playground"
service.name = "quarkus-oauth-playground-frontend"
service.name = "quarkus-oauth-playground-backend"
```

---

## Health Checks

All applications include SmallRye Health checks for Kubernetes/OpenShift:

### Health Endpoints

- **Liveness probe**: `GET /q/health/live`
  - Checks if the application is alive and running
  - Returns HTTP 200 if healthy, 503 if unhealthy

- **Readiness probe**: `GET /q/health/ready`
  - Checks if the application is ready to accept traffic
  - Includes OIDC health check (validates Keycloak connection for OAuth apps)
  - Returns HTTP 200 if ready, 503 if not ready

### Metrics

Prometheus metrics are available at:
- `GET /q/metrics` - All application and JVM metrics

OpenShift ServiceMonitor will automatically scrape these endpoints when deployed.

---

## Key Features

### Quarkus Advantages

- **Fast Startup**: JVM mode ~1-2s, Native mode ~0.01s
- **Low Memory**: Native images use ~20-30MB RAM
- **Developer Joy**: Live coding, Dev UI, automatic dev services
- **Cloud Native**: Built-in health checks, metrics, OpenTelemetry
- **Kubernetes Ready**: Automatic OpenShift manifest generation

### Reactive Programming

- **01-OIDC**: Uses Vert.x Mutiny WebClient for reactive HTTP calls
- **02-Oauth2 Frontend**: Uses Quarkus REST Client (reactive) + Vert.x WebClient
- **02-Oauth2 Backend**: Uses OIDC bearer token authentication

### Automatic Trace Propagation

Unlike Node.js which requires manual instrumentation:
- **REST Client**: Automatic W3C trace context propagation
- **Vert.x WebClient**: Automatic trace propagation when used in Quarkus
- **OIDC Extension**: Automatic tracing for token validation

---

## Package Structure

All Quarkus applications use the `io.jeannyil` package:

```
quarkus/
├── 01-OIDC/
│   └── src/main/java/io/jeannyil/
│       └── OIDCProxyResource.java
└── 02-Oauth2/
    ├── backend/src/main/java/io/jeannyil/
    │   └── OAuthServiceResource.java
    └── frontend/src/main/java/io/jeannyil/
        ├── BackendServiceClient.java
        └── OAuthProxyResource.java
```

---

## Comparison: Node.js vs Quarkus

| Feature | Node.js | Quarkus (JVM) | Quarkus (Native) |
|---------|---------|---------------|------------------|
| Startup Time | ~1s | ~1-2s | ~0.01s |
| Memory Usage | ~50MB | ~100MB | ~20-30MB |
| OIDC Library | keycloak-connect | quarkus-oidc | quarkus-oidc |
| OpenTelemetry | Manual | Automatic | Automatic |
| Health Checks | Custom | Built-in | Built-in |
| Metrics | Custom | Built-in | Built-in |
| Trace Propagation | Manual | Automatic | Automatic |
| Dev Experience | nodemon | Live Reload + Dev UI | Live Reload + Dev UI |
| Container Build | Dockerfile | Jib / S2I / Docker | Native Image |

---

## Troubleshooting

### Invalid Redirect URI

If you see `invalid_redirect_uri` errors:
1. **Verify you're configuring the client in the correct realm** (check the issuer URL)
2. Add your application URL to Valid Redirect URIs in the Keycloak client
3. Ensure the URL matches exactly (including trailing slash behavior)
4. Check Keycloak server logs for the exact error

### Native Image: URL Protocol Not Enabled

If you see `MalformedURLException: Accessing a URL protocol that was not enabled`:
- **01-OIDC**: Uses `quarkus.ssl.native=true` (Vert.x WebClient requires explicit SSL)
- **02-Oauth2**: SSL is automatic (quarkus-oidc extension enables it)

For more details, see the [Quarkus Native and SSL guide](https://quarkus.io/version/3.27/guides/native-and-ssl).

### 403 Forbidden on /secured Endpoint

1. Verify the user has the `quarkus-oauth-backend:user` client role
2. Check that the access token includes the `resource_access.quarkus-oauth-backend.roles` claim
3. Ensure `quarkus.oidc.roles.role-claim-path` is set correctly in backend

### Token Validation Failed

1. Verify the backend `quarkus.oidc.auth-server-url` matches the token issuer
2. Check that the client secret is correct (for confidential clients)
3. Ensure strict audience validation: backend must have matching `quarkus.oidc.client-id`

---

## Documentation

- **[01-OIDC README](./01-OIDC/README.md)** - OIDC Playground documentation
- **[02-Oauth2 README](./02-Oauth2/README.md)** - OAuth 2.0 Playground documentation
- **[Quarkus OIDC Guide](https://quarkus.io/guides/security-oidc-code-flow-authentication)** - Official Quarkus OIDC documentation
- **[Quarkus OpenTelemetry](https://quarkus.io/guides/opentelemetry)** - OpenTelemetry integration guide
- **[Quarkus OpenShift](https://quarkus.io/guides/deploying-to-openshift)** - OpenShift deployment guide
