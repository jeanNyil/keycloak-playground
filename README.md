# RHBK PlayGround

- [OpenID Connect Playground](./01-OIDC/README.md)
- [OAuth 2 Playground](./02-Oauth2/README.md)

---

## Build and Push Container Images

```bash
# Login to Quay.io
podman login quay.io

# OIDC Playground
cd 01-OIDC
podman build -t quay.io/jnyilimbibi/oidc-playground:1.0.0 .
podman push quay.io/jnyilimbibi/oidc-playground:1.0.0

# OAuth Frontend
cd ../02-Oauth2/frontend
podman build -t quay.io/jnyilimbibi/oauth-playground-frontend:1.0.0 .
podman push quay.io/jnyilimbibi/oauth-playground-frontend:1.0.0

# OAuth Backend
cd ../backend
podman build -t quay.io/jnyilimbibi/oauth-playground-backend:1.0.0 .
podman push quay.io/jnyilimbibi/oauth-playground-backend:1.0.0
```

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
# Update OAuth Backend ConfigMap first (keycloak.json)
oc patch configmap oauth-playground-backend-config --type merge -p '
{
  "data": {
    "keycloak.json": "{\n  \"realm\": \"demo\",\n  \"auth-server-url\": \"https://sso.apps.ocp4.jnyilimb.eu/\",\n  \"ssl-required\": \"all\",\n  \"resource\": \"oauth-backend\",\n  \"verify-token-audience\": true,\n  \"credentials\": {},\n  \"use-resource-role-mappings\": true,\n  \"confidential-port\": 0\n}"
  }
}'

# Update OIDC Playground
oc set env deployment/oidc-playground \
  KC_URL=https://sso.apps.ocp4.jnyilimb.eu/ \
  INPUT_ISSUER=https://sso.apps.ocp4.jnyilimb.eu/realms/demo \
  OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector.observability.svc:4317

# Update OAuth Frontend (uses internal K8s service for backend - no external route needed)
oc set env deployment/oauth-playground-frontend \
  KC_URL=https://sso.apps.ocp4.jnyilimb.eu/ \
  INPUT_ISSUER=https://sso.apps.ocp4.jnyilimb.eu/realms/demo \
  OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector.observability.svc:4317

# Update OAuth Backend (triggers rollout, picks up updated ConfigMap)
oc set env deployment/oauth-playground-backend \
  OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector.observability.svc:4317
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
echo "OAuth Backend:   https://$(oc get route oauth-playground-backend -o jsonpath='{.spec.host}')"
```
