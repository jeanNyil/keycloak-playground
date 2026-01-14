# Keycloak PlayGround

- [OpenID Connect Playground](./01-OIDC/README.md)
- [OAuth 2 Playground](./02-Oauth2/README.md)

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
  - Client ID: `oidc-playground`
  - Access Type: `public`
  - Valid Redirect URIs: `http://localhost:8000/*` (or your deployment URL)
  - Web Origins: `*` or specific origins
- **OAuth Client**: For the OAuth Playground frontend
  - Client ID: `oauth-playground`
  - Access Type: `public`
  - Valid Redirect URIs: `http://localhost:8000/*` (or your deployment URL)
  - Web Origins: `*` or specific origins
- **OAuth Backend Client**: For the OAuth Playground backend
  - Client ID: `oauth-backend`
  - Access Type: `public` or `confidential`
  - **Client Role**: `user` (required for secured endpoint access)
- **Test User**: A user with the `oauth-backend:user` client role assigned

> **Note**: Sample Keycloak client configurations are available in:
> - `01-OIDC/_realm-config/` - OIDC client configuration
> - `02-Oauth2/_realm-config/` - OAuth client configurations
>
> **Important**: After importing the `oauth-backend` client configuration, you must manually create the `user` client role in the Keycloak Admin UI, as client roles cannot be imported via the UI:
> 1. Navigate to **Clients** → **oauth-backend** → **Roles** tab
> 2. Click **Create Role**
> 3. Set **Role Name** to `user`
> 4. Save the role
> 5. Assign this role to your test user via **Users** → *[select user]* → **Role Mapping** → **Client Roles** → **oauth-backend**

---

## Build and Push Container Images

> **Note**: Pre-built images are available at `quay.io/jnyilimbibi` and can be used directly for deployment. If you want to build your own images, replace `<YOUR_QUAY_USERNAME>` with your Quay.io username or container registry organization name.

```bash
# Login to Quay.io
podman login quay.io

# OIDC Playground
cd 01-OIDC
podman build -t quay.io/<YOUR_QUAY_USERNAME>/oidc-playground:1.0.0 .
podman push quay.io/<YOUR_QUAY_USERNAME>/oidc-playground:1.0.0

# OAuth Frontend
cd ../02-Oauth2/frontend
podman build -t quay.io/<YOUR_QUAY_USERNAME>/oauth-playground-frontend:1.0.0 .
podman push quay.io/<YOUR_QUAY_USERNAME>/oauth-playground-frontend:1.0.0

# OAuth Backend
cd ../backend
podman build -t quay.io/<YOUR_QUAY_USERNAME>/oauth-playground-backend:1.0.0 .
podman push quay.io/<YOUR_QUAY_USERNAME>/oauth-playground-backend:1.0.0
```

> **Important**: If you built custom images with your own Quay username, you must update the image references in the OpenShift deployment manifests before deploying:
> - `01-OIDC/_openshift/deployment.yaml` - Line with `image: quay.io/jnyilimbibi/oidc-playground:1.0.0`
> - `02-Oauth2/frontend/_openshift/deployment.yaml` - Line with `image: quay.io/jnyilimbibi/oauth-playground-frontend:1.0.0`
> - `02-Oauth2/backend/_openshift/deployment.yaml` - Line with `image: quay.io/jnyilimbibi/oauth-playground-backend:1.0.0`

---

## Deploy to OpenShift

```bash
# Login to OpenShift
oc login <your-cluster-url>

# Create or switch to your project
oc project <your-project>

# Deploy OIDC Playground
oc apply -k 01-OIDC/_openshift/

# Deploy OAuth Backend (deploy first - frontend depends on its route)
oc apply -k 02-Oauth2/backend/_openshift/

# Deploy OAuth Frontend
oc apply -k 02-Oauth2/frontend/_openshift/
```

---

## Post-Deployment Configuration

Update environment variables and ConfigMap to match your environment:

```bash
# Set your environment-specific values
KC_REALM="<YOUR_REALM>"                             # e.g., demo
KC_URL="https://<YOUR_KEYCLOAK_HOST>/"              # e.g., https://sso.apps.example.com/
INPUT_ISSUER="${KC_URL}realms/${KC_REALM}"          # e.g., https://sso.apps.example.com/realms/demo
OTEL_ENDPOINT="http://<YOUR_OTEL_COLLECTOR>"        # e.g., http://otel-collector.observability.svc:4317

# Update OAuth Backend ConfigMap first (keycloak.json)
oc patch configmap oauth-playground-backend-config --type merge -p "{
  \"data\": {
    \"keycloak.json\": \"{\n  \\\"realm\\\": \\\"${KC_REALM}\\\",\n  \\\"auth-server-url\\\": \\\"${KC_URL}\\\",\n  \\\"ssl-required\\\": \\\"all\\\",\n  \\\"resource\\\": \\\"oauth-backend\\\",\n  \\\"bearer-only\\\": true,\n  \\\"verify-token-audience\\\": true,\n  \\\"credentials\\\": {},\n  \\\"use-resource-role-mappings\\\": true,\n  \\\"confidential-port\\\": 0\n}\"
  }
}"

# Update OIDC Playground
oc set env deployment/oidc-playground \
  KC_URL="${KC_URL}" \
  INPUT_ISSUER="${INPUT_ISSUER}" \
  OTEL_EXPORTER_OTLP_ENDPOINT="${OTEL_ENDPOINT}"

# Update OAuth Frontend (uses internal K8s service for backend - no external route needed)
oc set env deployment/oauth-playground-frontend \
  KC_URL="${KC_URL}" \
  INPUT_ISSUER="${INPUT_ISSUER}" \
  OTEL_EXPORTER_OTLP_ENDPOINT="${OTEL_ENDPOINT}"

# Update OAuth Backend (triggers rollout, picks up updated ConfigMap)
oc set env deployment/oauth-playground-backend \
  OTEL_EXPORTER_OTLP_ENDPOINT="${OTEL_ENDPOINT}"
```

**Example with actual values:**

```bash
# Example: Using a Keycloak instance at sso.apps.ocp4.jnyilimb.eu
KC_REALM="demo"
KC_URL="https://sso.apps.ocp4.jnyilimb.eu/"
INPUT_ISSUER="${KC_URL}realms/${KC_REALM}"
OTEL_ENDPOINT="http://otel-collector.observability.svc:4317"

# Then run the commands above...
```

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
oc logs -f deployment/oidc-playground
oc logs -f deployment/oauth-playground-frontend
oc logs -f deployment/oauth-playground-backend
```

---

## Access Applications

Get your application URLs:

```bash
echo "OIDC Playground: https://$(oc get route oidc-playground -o jsonpath='{.spec.host}')"
echo "OAuth Frontend:  https://$(oc get route oauth-playground-frontend -o jsonpath='{.spec.host}')"
# Note: OAuth Backend has no external route - accessed via frontend proxy
```

---

## OpenTelemetry Instrumentation

Both playground applications are instrumented with OpenTelemetry for distributed tracing. The instrumentation uses:

- **OTLP/gRPC exporter** to send traces to an OpenTelemetry Collector
- **W3C Trace Context propagation** for correlating traces across services
- **Auto-instrumentation** for Express and HTTP modules

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OTLP collector endpoint (gRPC) | `http://localhost:4317` |
| `OTEL_SERVICE_NAME` | Service name in traces | App-specific default |
| `OTEL_SERVICE_VERSION` | Service version | `1.0.0` |
| `OTEL_LOG_LEVEL` | Logging level (`info`, `debug`) | `info` |

### Trace Flow Diagrams

#### OIDC Playground

All Keycloak interactions are proxied through the frontend server for full trace correlation:

```
┌──────────┐      ┌─────────────────────┐      ┌──────────────┐
│          │      │                     │      │              │
│  Browser │─────▶│  OIDC Frontend      │─────▶│   Keycloak   │
│          │      │  (Express + OTel)   │      │   (OTel)     │
│          │      │                     │      │              │
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
              │   (Tempo/Jaeger/etc.)   │
              └─────────────────────────┘
```

**Traced Operations:**
- `GET /api/keycloak/discovery` → Keycloak OIDC Discovery
- `POST /api/keycloak/token` → Token Exchange
- `POST /api/keycloak/refresh` → Token Refresh
- `GET /api/keycloak/userinfo` → UserInfo Endpoint

#### OAuth Playground

The OAuth flow involves three services with full trace propagation:

```
┌──────────┐      ┌─────────────────────┐      ┌──────────────┐
│          │      │                     │      │              │
│  Browser │─────▶│  OAuth Frontend     │─────▶│   Keycloak   │
│          │      │  (Express + OTel)   │      │   (OTel)     │
│          │      │                     │      │              │
└──────────┘      └──────────┬──────────┘      └──────────────┘
                             │                        ▲
                             │                        │
                             ▼                        │
                  ┌─────────────────────┐             │
                  │                     │  Token      │
                  │  OAuth Backend      │  Validation │
                  │  (Express + OTel +  │─────────────┘
                  │   keycloak-connect) │
                  │                     │
                  └─────────────────────┘
                             │
                             ▼
              ┌─────────────────────────┐
              │   OpenTelemetry         │
              │   Collector             │
              └─────────────────────────┘
```

**Complete Trace Flow:**

```
┌────────────────────────────────────────────────────────────────────────┐
│                         Single Distributed Trace                       │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│  Browser Request                                                       │
│  ════════════════                                                      │
│       │                                                                │
│       ▼                                                                │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ oauth-playground-frontend                                       │   │
│  │ ┌─────────────────────────────────────────────────────────────┐ │   │
│  │ │ GET /api/keycloak/discovery                                 │ │   │
│  │ │    └──▶ GET keycloak/.well-known/openid-configuration       │ │   │
│  │ └─────────────────────────────────────────────────────────────┘ │   │
│  │ ┌─────────────────────────────────────────────────────────────┐ │   │
│  │ │ POST /api/keycloak/token                                    │ │   │
│  │ │    └──▶ POST keycloak/protocol/openid-connect/token         │ │   │
│  │ └─────────────────────────────────────────────────────────────┘ │   │
│  │ ┌─────────────────────────────────────────────────────────────┐ │   │
│  │ │ GET /api/service                                            │ │   │
│  │ │    └──▶ ┌────────────────────────────────────────────────┐  │ │   │
│  │ │         │ oauth-playground-backend                       │  │ │   │
│  │ │         │ ┌────────────────────────────────────────────┐ │  │ │   │
│  │ │         │ │ GET /secured                               │ │  │ │   │
│  │ │         │ │    └──▶ keycloak (token validation)        │ │  │ │   │
│  │ │         │ └────────────────────────────────────────────┘ │  │ │   │
│  │ │         └────────────────────────────────────────────────┘  │ │   │
│  │ └─────────────────────────────────────────────────────────────┘ │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘
```

### Viewing Traces

Traces can be viewed in any OpenTelemetry-compatible backend:

- **Grafana Tempo** - Query by service name or trace ID
- **Jaeger** - Search by service, operation, or tags
- **Zipkin** - Compatible with W3C trace context

Example trace search:
```bash
# Find traces by service name
service.name = "oidc-playground"
service.name = "oauth-playground-frontend"
service.name = "oauth-playground-backend"
```

### Local Development with Tracing

To run with tracing locally using the Grafana LGTM stack (Loki, Grafana, Tempo, Mimir):

```bash
# Start the LGTM stack (all-in-one observability)
docker run -d --name lgtm \
  -p 3100:3000 \
  -p 4317:4317 \
  -p 4318:4318 \
  grafana/otel-lgtm:latest

# Run the application with tracing
cd 01-OIDC
npm start

# Or without tracing
npm run start:no-tracing
```

**LGTM Stack Ports:**
| Port | Service | Description |
|------|---------|-------------|
| 3100 | Grafana | UI for exploring traces, logs, and metrics |
| 4317 | OTLP gRPC | OpenTelemetry traces/metrics/logs (gRPC) |
| 4318 | OTLP HTTP | OpenTelemetry traces/metrics/logs (HTTP) |

Access Grafana at `http://localhost:3100` and navigate to **Explore → Tempo** to view traces.
