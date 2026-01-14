var express = require('express');
var app = express();
var stringReplace = require('string-replace-middleware');

var KC_URL = process.env.KC_URL || "http://localhost:8080/";
var INPUT_ISSUER = process.env.INPUT_ISSUER || "http://localhost:8080/realms/demo";

// Helper function for consistent timestamped logging
function log(message, ...args) {
    const timestamp = new Date().toISOString();
    console.log(`[${timestamp}] ${message}`, ...args);
}

function logError(message, ...args) {
    const timestamp = new Date().toISOString();
    console.error(`[${timestamp}] ${message}`, ...args);
}

log('Configuration loaded:');
console.log('  └─ KC_URL:', KC_URL);
console.log('  └─ INPUT_ISSUER:', INPUT_ISSUER);

// Parse JSON bodies
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// String replacement for static files
app.use(stringReplace({
   'KC_URL': KC_URL,
   'INPUT_ISSUER': INPUT_ISSUER
}));

// Proxy endpoint for Keycloak discovery - enables distributed tracing
app.get('/api/keycloak/discovery', async function(req, res) {
    const issuer = req.query.issuer || INPUT_ISSUER;
    const discoveryUrl = issuer + '/.well-known/openid-configuration';
    log('GET /api/keycloak/discovery → ' + discoveryUrl);
    try {
        const response = await fetch(discoveryUrl);
        const data = await response.json();
        log('  └─ ✓ Discovery loaded successfully');
        res.json(data);
    } catch (error) {
        logError('  └─ ✗ Error fetching discovery:', error.message);
        res.status(500).json({ error: 'Error fetching discovery' });
    }
});

// Proxy endpoint for Keycloak token exchange - enables distributed tracing
app.post('/api/keycloak/token', async function(req, res) {
    const tokenEndpoint = req.body.token_endpoint;
    const grantType = req.body.grant_type;
    log(`POST /api/keycloak/token → ${tokenEndpoint}`);
    log(`  └─ grant_type: ${grantType}`);
    try {
        const params = new URLSearchParams();
        params.append('grant_type', grantType);
        if (req.body.code) params.append('code', req.body.code);
        if (req.body.refresh_token) params.append('refresh_token', req.body.refresh_token);
        params.append('client_id', req.body.client_id);
        if (req.body.redirect_uri) params.append('redirect_uri', req.body.redirect_uri);
        if (req.body.scope) params.append('scope', req.body.scope);

        const response = await fetch(tokenEndpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: params
        });
        const data = await response.json();
        if (response.ok) {
            log(`  └─ ✓ Token exchange successful`);
        } else {
            log(`  └─ ✗ Token exchange failed: ${response.status}`);
        }
        res.status(response.status).json(data);
    } catch (error) {
        logError('  └─ ✗ Error exchanging token:', error.message);
        res.status(500).json({ error: 'Error exchanging token' });
    }
});

// Proxy endpoint for Keycloak userinfo - enables distributed tracing
app.get('/api/keycloak/userinfo', async function(req, res) {
    const userinfoEndpoint = req.query.endpoint;
    const authHeader = req.headers['authorization'];
    log(`GET /api/keycloak/userinfo → ${userinfoEndpoint}`);
    try {
        const response = await fetch(userinfoEndpoint, {
            method: 'GET',
            headers: authHeader ? { 'Authorization': authHeader } : {}
        });
        const data = await response.json();
        if (response.ok) {
            log(`  └─ ✓ UserInfo retrieved for: ${data.preferred_username || data.sub || 'unknown'}`);
        } else {
            log(`  └─ ✗ UserInfo failed: ${response.status}`);
        }
        res.status(response.status).json(data);
    } catch (error) {
        logError('  └─ ✗ Error fetching userinfo:', error.message);
        res.status(500).json({ error: 'Error fetching userinfo' });
    }
});

// Proxy endpoint for Keycloak logout - enables distributed tracing
app.get('/api/keycloak/logout', async function(req, res) {
    const endSessionEndpoint = req.query.end_session_endpoint;
    const postLogoutRedirectUri = req.query.post_logout_redirect_uri;
    const idTokenHint = req.query.id_token_hint;

    log(`GET /api/keycloak/logout → ${endSessionEndpoint}`);
    log(`  └─ id_token_hint: ${idTokenHint ? 'present' : 'missing'}`);

    // Build Keycloak logout URL
    let logoutUrl = endSessionEndpoint + '?post_logout_redirect_uri=' + encodeURIComponent(postLogoutRedirectUri);
    if (idTokenHint) {
        logoutUrl += '&id_token_hint=' + encodeURIComponent(idTokenHint);
    }

    log(`  └─ Redirecting to Keycloak logout`);
    // Redirect to Keycloak logout
    res.redirect(logoutUrl);
});

app.use(express.static('.'))

app.get('/', function(req, res) {
    res.render('index.html');
});

app.listen(8000, function () {
    log('Started OIDC Playground on port 8000');
    console.log('Endpoints:');
    console.log('  - GET  /api/keycloak/discovery (OIDC discovery)');
    console.log('  - POST /api/keycloak/token (token exchange)');
    console.log('  - GET  /api/keycloak/userinfo (user info)');
    console.log('  - GET  /api/keycloak/logout (end session)');
});
