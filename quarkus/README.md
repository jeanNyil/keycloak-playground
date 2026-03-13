# Keycloak Playground - Quarkus Implementation

Cloud-native implementations of OIDC and OAuth 2.0 playgrounds using Quarkus framework.

- [OpenID Connect Playground](./01-OIDC/README.md)
- [OAuth 2.0 Playground](./02-Oauth2/README.md)

---

## Prerequisites

A running Keycloak (or RHBK) instance and a configured realm. See the [root README](../README.md#-quick-start) for general setup and the individual project READMEs for client-specific details:

- [OIDC Playground prerequisites](./01-OIDC/README.md)
- [OAuth 2.0 Playground prerequisites](./02-Oauth2/README.md)

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

Native image compilation is supported for all Quarkus playgrounds. See each project's README for specific instructions:

- [OIDC native build](./01-OIDC/README.md)
- [OAuth 2.0 native build](./02-Oauth2/README.md)

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

## Reset vs Logout

Both playgrounds provide **Reset** and **Logout** buttons. Reset clears local browser state only (the Keycloak SSO session stays active), while Logout also terminates the Keycloak session. For full details, see each project's README:

- [OIDC Playground – Reset vs Logout](./01-OIDC/README.md#reset-vs-logout)
- [OAuth 2.0 Playground – Reset vs Logout](./02-Oauth2/README.md#reset-vs-logout)

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

See each project's README for troubleshooting guides:

- [OIDC Playground troubleshooting](./01-OIDC/README.md#troubleshooting)
- [OAuth 2.0 Playground troubleshooting](./02-Oauth2/README.md#troubleshooting)

---

## Documentation

- **[01-OIDC README](./01-OIDC/README.md)** - OIDC Playground documentation
- **[02-Oauth2 README](./02-Oauth2/README.md)** - OAuth 2.0 Playground documentation
- **[Quarkus OIDC Guide](https://quarkus.io/guides/security-oidc-code-flow-authentication)** - Official Quarkus OIDC documentation
- **[Quarkus OpenTelemetry](https://quarkus.io/guides/opentelemetry)** - OpenTelemetry integration guide
- **[Quarkus OpenShift](https://quarkus.io/guides/deploying-to-openshift)** - OpenShift deployment guide
