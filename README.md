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

- **[Quarkus OAuth Playground](./quarkus)** - OAuth 2.0 playground built with Quarkus, OIDC extension, and SmallRye Health

**Key Features:**
- Native OAuth 2.0 flow implementation
- Built-in OIDC authentication
- SmallRye Health checks for Kubernetes
- Micrometer metrics integration
- OpenTelemetry tracing with LGTM stack support
- Fast startup and low memory footprint

📖 **[Quarkus Documentation](./quarkus/README.md)**

## 🚀 Quick Start

### Prerequisites

1. **Keycloak Server**
   - Local: `http://localhost:8080`
   - Remote: Any accessible Keycloak instance

2. **Realm Configuration**
   - Import client configurations from `nodejs/01-OIDC/_realm-config/` and `nodejs/02-Oauth2/_realm-config/`
   - Or manually create clients (see documentation)

### Running Locally

#### Node.js Playgrounds
```bash
# OIDC Playground
cd nodejs/01-OIDC
npm install
npm start

# OAuth 2.0 Playground (requires two terminals)
cd nodejs/02-Oauth2/backend
npm install && npm start

cd nodejs/02-Oauth2/frontend
npm install && npm start
```

#### Quarkus Playground
```bash
# Backend
cd quarkus/backend
./mvnw quarkus:dev

# Frontend (in another terminal)
cd quarkus/frontend
./mvnw quarkus:dev
```

## 📦 Deployment

Both implementations support deployment to OpenShift/Kubernetes with pre-configured manifests.

### Build Container Images
```bash
# Node.js
cd nodejs
podman build -t quay.io/<YOUR_USERNAME>/nodejs-oidc-playground:1.0.0 01-OIDC/
podman build -t quay.io/<YOUR_USERNAME>/nodejs-oauth-playground-frontend:1.0.0 02-Oauth2/frontend/
podman build -t quay.io/<YOUR_USERNAME>/nodejs-oauth-playground-backend:1.0.0 02-Oauth2/backend/

# Quarkus
cd quarkus/backend && ./mvnw clean package -Dquarkus.container-image.build=true
cd quarkus/frontend && ./mvnw clean package -Dquarkus.container-image.build=true
```

### Deploy to OpenShift
```bash
# Node.js
oc apply -k nodejs/01-OIDC/_openshift/
oc apply -k nodejs/02-Oauth2/backend/_openshift/
oc apply -k nodejs/02-Oauth2/frontend/_openshift/

# Quarkus
cd quarkus/backend && ./mvnw clean package -Dquarkus.kubernetes.deploy=true
cd quarkus/frontend && ./mvnw clean package -Dquarkus.kubernetes.deploy=true
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
| `quarkus-oauth-playground` | Public | OAuth frontend |
| `quarkus-oauth-backend` | Confidential | OAuth backend service |

**Important**: Client roles cannot be imported via Keycloak Admin UI. You must manually create the `user` role for backend clients after import.

## 📊 OpenTelemetry Tracing

All applications are instrumented with OpenTelemetry for distributed tracing:

- **Protocol**: OTLP/gRPC
- **Propagation**: W3C Trace Context
- **Backend Support**: Grafana Tempo, Jaeger, Zipkin

### Local Tracing with LGTM Stack
```bash
# Start Grafana LGTM (Loki, Grafana, Tempo, Mimir)
docker run -d --name lgtm \
  -p 3100:3000 \
  -p 4317:4317 \
  -p 4318:4318 \
  grafana/otel-lgtm:latest

# Access Grafana at http://localhost:3100
```

Service names for trace queries:
- `nodejs-oidc-playground`
- `nodejs-oauth-playground-frontend`
- `nodejs-oauth-playground-backend`
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
- **[Quarkus README](./quarkus/README.md)** - Quarkus implementation overview
- **[Quarkus Quick Start](./quarkus/QUICKSTART.md)** - Fast development setup
- **[Quarkus Implementation Summary](./quarkus/IMPLEMENTATION-SUMMARY.md)** - Technical details

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

---

**Made with ❤️ for learning Keycloak, OIDC, and OAuth 2.0**
