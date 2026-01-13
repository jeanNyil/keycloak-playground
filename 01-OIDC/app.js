var express = require('express');
var app = express();
var stringReplace = require('string-replace-middleware');

var KC_URL = process.env.KC_URL || "http://localhost:8080/";
var INPUT_ISSUER = process.env.INPUT_ISSUER || "http://localhost:8080/realms/demo";

console.log('env KC_URL:', KC_URL);
console.log('env INPUT_ISSUER:', INPUT_ISSUER);

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
    console.log('Proxying discovery request to:', discoveryUrl);
    try {
        const response = await fetch(discoveryUrl);
        const data = await response.json();
        res.json(data);
    } catch (error) {
        console.error('Error fetching discovery:', error);
        res.status(500).json({ error: 'Error fetching discovery' });
    }
});

// Proxy endpoint for Keycloak token exchange - enables distributed tracing
app.post('/api/keycloak/token', async function(req, res) {
    const tokenEndpoint = req.body.token_endpoint;
    console.log('Proxying token request to:', tokenEndpoint);
    try {
        const params = new URLSearchParams();
        params.append('grant_type', req.body.grant_type);
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
        res.status(response.status).json(data);
    } catch (error) {
        console.error('Error exchanging token:', error);
        res.status(500).json({ error: 'Error exchanging token' });
    }
});

// Proxy endpoint for Keycloak userinfo - enables distributed tracing
app.get('/api/keycloak/userinfo', async function(req, res) {
    const userinfoEndpoint = req.query.endpoint;
    const authHeader = req.headers['authorization'];
    console.log('Proxying userinfo request to:', userinfoEndpoint);
    try {
        const response = await fetch(userinfoEndpoint, {
            method: 'GET',
            headers: authHeader ? { 'Authorization': authHeader } : {}
        });
        const data = await response.json();
        res.status(response.status).json(data);
    } catch (error) {
        console.error('Error fetching userinfo:', error);
        res.status(500).json({ error: 'Error fetching userinfo' });
    }
});

app.use(express.static('.'))

app.get('/', function(req, res) {
    res.render('index.html');
});

app.listen(8000, function () {
    console.log('Started OIDC Playground on port 8000');
});
