# Keycloak Playground

Interactive playgrounds for exploring OpenID Connect (OIDC) and OAuth 2.0 authentication flows with Keycloak/RHBK (Red Hat build of Keycloak).

This repository provides hands-on learning tools to understand how OIDC and OAuth 2.0 work by visualizing each step of the authentication flow, from discovery to token exchange and API calls.

## 🎯 What's Included

### Node.js Implementation
Traditional Node.js/Express implementations with OpenTelemetry tracing:

- **[OIDC Playground](./nodejs/01-OIDC)** - Interactive OpenID Connect flow demonstration
- **[OAuth 2.0 Playground](./nodejs/02-Oauth2)** - OAuth 2.0 authorization code flow with frontend/backend separation

**Key Features:**
- Step-by-step visualization of authentication flows
- Token inspection (header, payload, signature)
- Public and secured endpoint testing
- Role-based access control demonstration
- OpenTelemetry distributed tracing

📖 **[Node.js Documentation](./nodejs/README.md)**

### Quarkus Implementation
Modern cloud-native implementation using Quarkus framework:

- **[Quarkus OIDC Playground](./quarkus/01-OIDC)** - Interactive OpenID Connect flow demonstration with reactive Vert.x WebClient
- **[Quarkus OAuth 2.0 Playground](./quarkus/02-Oauth2)** - OAuth 2.0 playground with frontend/backend separation using OIDC extension

**Key Features:**
- Native OIDC and OAuth 2.0 flow implementations
- Built-in OIDC authentication and token validation
- SmallRye Health checks for Kubernetes/OpenShift
- Micrometer metrics integration
- OpenTelemetry tracing with LGTM stack support
- Fast startup and low memory footprint
- Native image compilation support

📖 **[Quarkus Documentation](./quarkus/README.md)** - Complete Quarkus implementation guide
📖 **[Quarkus 01-OIDC README](./quarkus/01-OIDC/README.md)** - OIDC playground details
📖 **[Quarkus 02-OAuth2 README](./quarkus/02-Oauth2/README.md)** - OAuth 2.0 playground details

## 🚀 Quick Start

### Prerequisites

1. **Keycloak Server**
   - Local: `http://localhost:8080`
   - Remote: Any accessible Keycloak instance

2. **Realm Configuration**
   - Import client configurations from `nodejs/01-OIDC/_realm-config/` and `nodejs/02-Oauth2/_realm-config/`
   - Or manually create clients (see documentation)

### Running Locally

Before starting any playground, configure the environment variables or properties to point at your Keycloak instance. See the [Environment Configuration](#environment-configuration) section below for the full list.

#### Node.js Playgrounds
```bash
# OIDC Playground (port 8000)
export KC_URL=https://your-keycloak-server/                             # default: http://localhost:8080/
export INPUT_ISSUER=https://your-keycloak-server/realms/your-realm      # default: http://localhost:8080/realms/demo
export OTEL_EXPORTER_OTLP_ENDPOINT=<YOUR_OTEL_COLLECTOR_GRPC_ENDPOINT>  # optional, default: http://localhost:4317
cd nodejs/01-OIDC
npm install && npm start

# OAuth 2.0 Playground (requires two terminals)
# Terminal 1 - Backend (port 8001)
export KC_URL=https://your-keycloak-server/                             # default: http://localhost:8080/
export KC_REALM=your-realm                                              # default: demo
export OTEL_EXPORTER_OTLP_ENDPOINT=<YOUR_OTEL_COLLECTOR_GRPC_ENDPOINT>  # optional, default: http://localhost:4317
cd nodejs/02-Oauth2/backend
npm install && npm start

# Terminal 2 - Frontend (port 8000)
export KC_URL=https://your-keycloak-server/                             # default: http://localhost:8080/
export INPUT_ISSUER=https://your-keycloak-server/realms/your-realm      # default: http://localhost:8080/realms/demo
export OTEL_EXPORTER_OTLP_ENDPOINT=<YOUR_OTEL_COLLECTOR_GRPC_ENDPOINT>  # optional, default: http://localhost:4317
cd nodejs/02-Oauth2/frontend
npm install && npm start
```

#### Quarkus Playgrounds

Update `application.properties` in each project before starting (see [Environment Configuration](#environment-configuration)).

```bash
# OIDC Playground (on port 8080)
cd quarkus/01-OIDC
./mvnw quarkus:dev

# OAuth 2.0 Playground (requires two terminals)
# Terminal 1 - Backend (on port 8081)
cd quarkus/02-Oauth2/backend
./mvnw quarkus:dev -Dquarkus.http.port=8081

# Terminal 2 - Frontend (on port 8080)
cd quarkus/02-Oauth2/frontend
./mvnw quarkus:dev
```

## 📦 Deployment

Both implementations support deployment to OpenShift/Kubernetes with pre-configured manifests.

### Build Container Images

**Node.js:**
```bash
cd nodejs
podman build -t quay.io/<YOUR_USERNAME>/nodejs-oidc-playground:1.0.0 01-OIDC/
podman push quay.io/<YOUR_USERNAME>/nodejs-oidc-playground:1.0.0

podman build -t quay.io/<YOUR_USERNAME>/nodejs-oauth-playground-frontend:1.0.0 02-Oauth2/frontend/
podman push quay.io/<YOUR_USERNAME>/nodejs-oauth-playground-frontend:1.0.0

podman build -t quay.io/<YOUR_USERNAME>/nodejs-oauth-playground-backend:1.0.0 02-Oauth2/backend/
podman push quay.io/<YOUR_USERNAME>/nodejs-oauth-playground-backend:1.0.0
```

**Quarkus:** The OpenShift deploy command (below) builds and pushes images automatically. For standalone image builds, see the [Quarkus README](./quarkus/README.md#build-and-push-container-images).

### Deploy to OpenShift

**Node.js:** The deployment manifests reference pre-built images at `quay.io/jnyilimbibi`. If you built your own images, update the `image` field in each `_openshift/deployment.yaml` before deploying. You must also update the `KC_URL`, `INPUT_ISSUER`, and `OTEL_EXPORTER_OTLP_ENDPOINT` environment variables in the deployment manifests to match your environment.

```bash
oc apply -k nodejs/01-OIDC/_openshift/
oc apply -k nodejs/02-Oauth2/backend/_openshift/
oc apply -k nodejs/02-Oauth2/frontend/_openshift/
```

**Quarkus:** Before deploying, customize the `src/main/kubernetes/openshift.yml` in each project to set your Keycloak URLs and OTel endpoint. See the [Quarkus README](./quarkus/README.md#pre-deployment-configuration) for details.

```bash
cd quarkus/01-OIDC && ./mvnw clean package -Dquarkus.openshift.deploy=true
cd quarkus/02-Oauth2/backend && ./mvnw clean package -Dquarkus.openshift.deploy=true
cd quarkus/02-Oauth2/frontend && ./mvnw clean package -Dquarkus.openshift.deploy=true
```

## 🔐 Keycloak Client Configuration

### Node.js Clients
| Client ID | Type | Purpose |
|-----------|------|---------|
| `nodejs-oidc-playground` | Public | OIDC flow demonstration |
| `nodejs-oauth-playground` | Public | OAuth frontend |
| `nodejs-oauth-backend` | Confidential | OAuth backend service |

### Quarkus Clients
| Client ID | Type | Purpose |
|-----------|------|---------|
| `quarkus-oidc-playground` | Public | OIDC flow demonstration |
| `quarkus-oauth-playground` | Public | OAuth frontend |
| `quarkus-oauth-backend` | Confidential | OAuth backend service |

**Important**: Client roles cannot be imported via Keycloak Admin UI. You must manually create the `user` role for backend clients after import.

## ⚙️ Environment Configuration

All playgrounds can be customized to point at your own Keycloak instance. The configuration mechanism differs between Node.js (environment variables) and Quarkus (`application.properties`).

### Node.js Environment Variables

Set these before running `npm start`:

| Variable | Description | Default | Used By |
|----------|-------------|---------|---------|
| `KC_URL` | Keycloak server root URL | `http://localhost:8080/` | All |
| `INPUT_ISSUER` | Keycloak issuer realm URL | `http://localhost:8080/realms/demo` | OIDC, OAuth frontend |
| `KC_REALM` | Keycloak realm name | `demo` | OAuth backend only |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OpenTelemetry collector gRPC endpoint | `http://localhost:4317` | All (optional) |

### Quarkus Configuration Properties

Edit `src/main/resources/application.properties` in each project:

| Property | Description | Used By |
|----------|-------------|---------|
| `keycloak.url` | Keycloak server root URL | OIDC playground |
| `keycloak.issuer` | Keycloak issuer realm URL (auto-loaded by UI) | OIDC playground |
| `quarkus.oidc.auth-server-url` | Keycloak auth server URL | OAuth frontend and backend |
| `quarkus.oidc.credentials.secret` | Backend client secret | OAuth backend (required) |
| `oauth.service.url` | Backend service URL | OAuth frontend |

Quarkus properties can also be overridden via environment variables at runtime (e.g., `KEYCLOAK_ISSUER`, `QUARKUS_OIDC_AUTH_SERVER_URL`). See each project's README for details.

### OpenTelemetry (Both Platforms)

| Variable / Property | Description | Default |
|---------------------|-------------|---------|
| `OTEL_EXPORTER_OTLP_ENDPOINT` (Node.js) | OTel collector gRPC endpoint | `http://localhost:4317` |
| `QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT` (Quarkus) | OTel collector gRPC endpoint | `http://localhost:4317` |

> **Note**: Quarkus applications automatically start a Grafana LGTM dev service in dev mode. Node.js applications require a manually started collector (see [OpenTelemetry Tracing](#opentelemetry-tracing)).

## 🔄 Reset vs Logout

All playgrounds provide **Reset** and **Logout** buttons:

- **Reset** clears local browser state only. The Keycloak SSO session remains active, so re-authenticating will skip the login page.
- **Logout** clears local state **and** terminates the Keycloak SSO session via the `end_session_endpoint`, so re-authenticating will prompt for credentials.

> **Note**: Logout requires an `id_token_hint`, which is only issued when authenticating with the `openid` scope (OIDC). If no ID token is available (e.g., plain OAuth 2.0 without `openid` scope), the playground will show a warning, clear local state, and skip the Keycloak logout call. See each project's README for details.

## 📊 OpenTelemetry Tracing

All applications are instrumented with OpenTelemetry for distributed tracing:

- **Protocol**: OTLP/gRPC
- **Propagation**: W3C Trace Context
- **Backend Support**: Grafana Tempo, Jaeger, Zipkin

### Local Tracing with LGTM Stack

**For Node.js applications:**

```bash
# Start Grafana LGTM (Loki, Grafana, Tempo, Mimir)
podman run -d --name lgtm \
  -p 3100:3000 \
  -p 4317:4317 \
  -p 4318:4318 \
  grafana/otel-lgtm:latest

# Access Grafana at http://localhost:3100
```

**For Quarkus applications:**

Quarkus apps automatically start the LGTM stack as a dev service when running in dev mode (`./mvnw quarkus:dev`). No need to run the Docker image manually!

```bash
# Start Quarkus app - LGTM starts automatically
cd quarkus/02-Oauth2/frontend  # or quarkus/02-Oauth2/backend
./mvnw quarkus:dev

# Access Quarkus Dev UI at http://localhost:8080/q/dev-ui
# The Grafana URL can be discovered from the Dev UI under "Observability"
```

Service names for trace queries:
- `nodejs-oidc-playground`
- `nodejs-oauth-playground-frontend`
- `nodejs-oauth-playground-backend`
- `quarkus-oidc-playground`
- `quarkus-oauth-playground-frontend`
- `quarkus-oauth-playground-backend`

## 🏗️ Architecture

### Node.js OAuth 2.0 Flow
```
┌──────────┐      ┌─────────────────────┐      ┌──────────────┐
│          │      │   OAuth Frontend    │      │              │
│  Browser │─────▶│   (Express + OTel)  │─────▶│   Keycloak   │
│          │      │                     │      │              │
└──────────┘      └──────────┬──────────┘      └──────────────┘
                             │
                             ▼
                  ┌─────────────────────┐
                  │   OAuth Backend     │
                  │ (Express + Keycloak │
                  │     Adapter)        │
                  └─────────────────────┘
```

### Quarkus OAuth 2.0 Flow
```
┌──────────┐      ┌─────────────────────┐      ┌──────────────┐
│          │      │   Quarkus Frontend  │      │              │
│  Browser │─────▶│   (OIDC Web-App)    │─────▶│   Keycloak   │
│          │      │                     │      │              │
└──────────┘      └──────────┬──────────┘      └──────────────┘
                             │
                             ▼
                  ┌─────────────────────┐
                  │   Quarkus Backend   │
                  │   (OIDC Service)    │
                  └─────────────────────┘
```

## 📚 Documentation

- **[Node.js README](./nodejs/README.md)** - Complete Node.js implementation guide
- **[Quarkus README](./quarkus/README.md)** - Complete Quarkus implementation guide
- **[Quarkus 01-OIDC README](./quarkus/01-OIDC/README.md)** - Quarkus OIDC playground guide
- **[Quarkus 02-OAuth2 README](./quarkus/02-Oauth2/README.md)** - Quarkus OAuth 2.0 implementation overview

## 🛠️ Technology Stack

### Node.js
- Express.js
- Keycloak Connect Adapter
- OpenTelemetry Node SDK
- OTLP/gRPC Exporter

### Quarkus
- Quarkus 3.x
- OIDC Extension
- SmallRye Health
- Micrometer
- OpenTelemetry Extension
- RESTEasy Reactive

## 🤝 Contributing

This is a learning and demonstration repository. Feel free to:
- Report issues
- Suggest improvements
- Submit pull requests
- Use as reference for your own projects

## 📝 License

See [LICENSE](./LICENSE) file for details.

## 🎓 Learning Resources

- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [OpenID Connect Specification](https://openid.net/specs/openid-connect-core-1_0.html)
- [OAuth 2.0 RFC 6749](https://datatracker.ietf.org/doc/html/rfc6749)
- [Quarkus OIDC Guide](https://quarkus.io/guides/security-oidc-code-flow-authentication)
- [OpenTelemetry Documentation](https://opentelemetry.io/docs/)
