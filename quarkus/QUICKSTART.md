# Quarkus OAuth2 Playground - Quick Start

## 🚀 5-Minute Setup

### Prerequisites
- Java 21+
- Maven 3.9+
- Keycloak server running
- Two terminals

---

## Step 1: Configure Keycloak (One-Time Setup)

### Create Backend Client
1. Go to Keycloak Admin Console → Clients → Create
2. **Client ID**: `quarkus-oauth-backend`
3. **Client authentication**: ON (confidential)
4. **Save** and copy the **client secret**
5. Go to **Roles** tab → Create Role → Name: `user`

### Create Frontend Client
1. Create another client
2. **Client ID**: `quarkus-oauth-playground`
3. **Client authentication**: OFF (public)
4. **Valid Redirect URIs**: `http://localhost:8080/*`
5. **Web Origins**: `*`

### Assign Role to User
1. Go to **Users** → Select your test user
2. **Role Mapping** → **Client Roles** → `quarkus-oauth-backend` → Assign `user` role

---

## Step 2: Configure Backend

```bash
cd quarkus/backend
```

Edit `src/main/resources/application.properties`:

```properties
quarkus.oidc.auth-server-url=https://YOUR_KEYCLOAK_URL/realms/YOUR_REALM
quarkus.oidc.client-id=quarkus-oauth-backend
quarkus.oidc.credentials.secret=YOUR_BACKEND_CLIENT_SECRET
```

Start the backend:

```bash
./mvnw quarkus:dev -Dquarkus.http.port=8081
```

✅ Backend running on http://localhost:8081

---

## Step 3: Configure Frontend

Open a new terminal:

```bash
cd quarkus/frontend
```

Edit `src/main/resources/application.properties`:

```properties
quarkus.oidc.auth-server-url=https://YOUR_KEYCLOAK_URL/realms/YOUR_REALM
quarkus.oidc.client-id=quarkus-oauth-playground
quarkus.oidc.credentials.secret=YOUR_FRONTEND_CLIENT_SECRET
oauth.service.url=http://localhost:8081
```

Start the frontend:

```bash
./mvnw quarkus:dev
```

✅ Frontend running on http://localhost:8080

---

## Step 4: Test the Playground

1. Open your browser to **http://localhost:8080**

2. **Step 1 - Discovery**
   - Click "1 - Discovery"
   - Click "Load OAuth 2.0 Provider Configuration"
   - ✅ You should see the OIDC configuration

3. **Step 2 - Authorization**
   - Click "2 - Authorization"
   - Client ID should be `quarkus-oauth-playground`
   - Click "Send Authorization Request"
   - Login with your Keycloak user
   - ✅ You should see the access token decoded

4. **Step 3 - Invoke Service**
   - Click "3 - Invoke Service"
   - Click "Invoke /public"
   - ✅ Should return: `✓ [200] Public message!`
   - Click "Invoke /secured"
   - ✅ Should return: `✓ [200] Secret message!`

---

## Troubleshooting

### ❌ 403 Forbidden on /secured
**Problem**: User doesn't have the required role

**Solution**:
1. Go to Keycloak Admin → Users → Your User
2. Role Mapping → Client Roles → quarkus-oauth-backend
3. Assign the `user` role

### ❌ Invalid Redirect URI
**Problem**: Frontend URL not in Keycloak client config

**Solution**:
1. Go to Keycloak Admin → Clients → quarkus-oauth-playground
2. Valid Redirect URIs → Add `http://localhost:8080/*`
3. Save

### ❌ Connection Refused to Backend
**Problem**: Backend not running or wrong URL

**Solution**:
```bash
# Check backend is running on 8081
curl http://localhost:8081/public

# If not, start backend in terminal 1:
cd quarkus/backend
./mvnw quarkus:dev -Dquarkus.http.port=8081
```

### ❌ Token Validation Failed
**Problem**: Client secret or auth-server-url incorrect

**Solution**:
1. Verify `auth-server-url` matches Keycloak URL exactly
2. Copy client secret from Keycloak Admin → Clients → Credentials tab
3. Restart the application

---

## Verify Everything is Working

### Backend Health Check
```bash
curl http://localhost:8081/public
# Should return: Public message!

curl http://localhost:8081/q/health/live
# Should return: {"status":"UP",...}

curl http://localhost:8081/q/health/ready
# Should return: {"status":"UP",...}
```

### Frontend Health Check
```bash
curl http://localhost:8080/
# Should return HTML playground UI

curl http://localhost:8080/api/service/public
# Should return: Public message!
```

---

## With OpenTelemetry Tracing

Both apps automatically start with the Grafana LGTM stack in dev mode:

1. Start backend: `./mvnw quarkus:dev -Dquarkus.http.port=8081`
2. Start frontend: `./mvnw quarkus:dev`
3. Open Grafana: **http://localhost:3000**
4. Navigate to: **Explore → Tempo**
5. Query: `{service.name="quarkus-frontend"}`
6. 🎉 See distributed traces!

---

## Example Configuration

### Keycloak at sso.example.com, realm: demo

**Backend** `application.properties`:
```properties
quarkus.oidc.auth-server-url=https://sso.example.com/realms/demo
quarkus.oidc.client-id=quarkus-backend
quarkus.oidc.credentials.secret=abc123-your-secret-here
```

**Frontend** `application.properties`:
```properties
quarkus.oidc.auth-server-url=https://sso.example.com/realms/demo
quarkus.oidc.client-id=quarkus-web-app
quarkus.oidc.credentials.secret=xyz789-your-secret-here
oauth.service.url=http://localhost:8081
```

---

## 🎉 You're Done!

You now have a fully functional OAuth 2.0 playground running with:
- ✅ Authorization Code Flow
- ✅ Token inspection
- ✅ Role-based access control
- ✅ Distributed tracing
- ✅ Health checks and metrics

## Next Steps

- Check out the full [README.md](README.md) for deployment options
- Review [IMPLEMENTATION-SUMMARY.md](IMPLEMENTATION-SUMMARY.md) for technical details
- Deploy to OpenShift using the Quarkus extension
