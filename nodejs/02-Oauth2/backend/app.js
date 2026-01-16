var express = require('express');
var session = require('express-session');
var Keycloak = require('keycloak-connect');
var cors = require('cors');
var fs = require('fs');
var path = require('path');

var app = express();

// Helper function for consistent timestamped logging
function log(message, ...args) {
    const timestamp = new Date().toISOString();
    console.log(`[${timestamp}] ${message}`, ...args);
}

function logError(message, ...args) {
    const timestamp = new Date().toISOString();
    console.error(`[${timestamp}] ${message}`, ...args);
}

// Helper function to parse env var substitution from keycloak.json
function parseEnvVar(value) {
  if (typeof value !== 'string') return value;
  
  // Match pattern: ${env.VAR_NAME:default_value}
  const match = value.match(/^\$\{env\.([^:]+):(.+)\}$/);
  if (match) {
    const envVarName = match[1];
    const defaultValue = match[2];
    return process.env[envVarName] || defaultValue;
  }
  return value;
}

// Load Keycloak configuration to get expected issuer
var keycloakConfig = JSON.parse(fs.readFileSync(path.join(__dirname, 'keycloak.json'), 'utf8'));
var KC_URL = parseEnvVar(keycloakConfig['auth-server-url']);
var REALM = parseEnvVar(keycloakConfig.realm);
var EXPECTED_ISSUER = `${KC_URL}realms/${REALM}`.replace(/\/+$/, ''); // Remove trailing slash

log('Configuration loaded:');
console.log(`  └─ Keycloak URL: ${KC_URL}`);
console.log(`  └─ Realm: ${REALM}`);
console.log(`  └─ Expected Issuer: ${EXPECTED_ISSUER}`);

app.use(cors());

var memoryStore = new session.MemoryStore();

app.use(session({
  secret: 'some secret',
  resave: false,
  saveUninitialized: true,
  store: memoryStore
}));

var keycloak = new Keycloak({ store: memoryStore });

app.use(keycloak.middleware());

// Logging middleware for all requests
app.use(function(req, res, next) {
  // Skip logging for root path (used by health probes)
  if (req.path === '/') {
    return next();
  }
  
  const authHeader = req.headers['authorization'];
  const hasToken = authHeader && authHeader.startsWith('Bearer ');
  const isPublicEndpoint = req.path === '/public';
  
  // Differentiate log message for public vs secured endpoints
  if (isPublicEndpoint) {
    log(`${req.method} ${req.path} - Token: ${hasToken ? 'present (not required)' : 'not required'}`);
  } else {
    log(`${req.method} ${req.path} - Token: ${hasToken ? 'present' : 'missing'}`);
  }
  
  // Log token claims if present (for debugging)
  if (hasToken) {
    try {
      const token = authHeader.split(' ')[1];
      const tokenParts = token.split('.');
      
      // Validate JWT structure (must have 3 parts: header.payload.signature)
      if (tokenParts.length !== 3) {
        console.log(`  └─ ⚠️  Invalid JWT format (expected 3 parts, got ${tokenParts.length})`);
        console.log(`  └─ Token preview: ${token.substring(0, 50)}...`);
      } else {
        const payload = JSON.parse(Buffer.from(tokenParts[1], 'base64').toString());
        const now = Math.floor(Date.now() / 1000);
        const isExpired = payload.exp && payload.exp < now;
        
        console.log(`  └─ Issuer: ${payload.iss || 'N/A'}`);
        console.log(`  └─ Subject: ${payload.sub || 'N/A'}`);
        console.log(`  └─ Username: ${payload.preferred_username || 'N/A'}`);
        console.log(`  └─ Audience: ${JSON.stringify(payload.aud) || 'N/A'}`);
        console.log(`  └─ Expires: ${payload.exp ? new Date(payload.exp * 1000).toISOString() : 'N/A'}`);
        
        // Check issuer
        if (payload.iss && payload.iss !== EXPECTED_ISSUER) {
          console.log(`  └─ ⚠️  Issuer mismatch (expected: ${EXPECTED_ISSUER}, got: ${payload.iss})`);
        }
        
        if (isExpired) {
          console.log(`  └─ ⚠️  Token EXPIRED (expired ${Math.floor((now - payload.exp) / 60)} minutes ago)`);
        }
        
        // Check if audience matches
        const audiences = Array.isArray(payload.aud) ? payload.aud : [payload.aud];
        const hasCorrectAudience = audiences.includes('nodejs-oauth-backend');
        if (!hasCorrectAudience) {
          console.log(`  └─ ⚠️  Token audience mismatch (expected: nodejs-oauth-backend, got: ${JSON.stringify(audiences)})`);
        }
        
        // Check if user has required role
        const clientRoles = payload.resource_access?.['nodejs-oauth-backend']?.roles || [];
        const hasRequiredRole = clientRoles.includes('user');
        if (!hasRequiredRole) {
          console.log(`  └─ ⚠️  Missing required role 'user' (has: ${JSON.stringify(clientRoles)})`);
        }
      }
      
    } catch (e) {
      console.log(`  └─ ⚠️  Could not decode token: ${e.message}`);
    }
  }
  
  // Capture response status for logging with detailed reason
  res.on('finish', function() {
    const statusEmoji = res.statusCode < 400 ? '✓' : '✗';
    
    // Log for secured endpoint
    if (req.path === '/secured') {
      const level = res.statusCode < 400 ? 'AUTHORIZED' : 'DENIED';
      let reason = '';
      
      if (res.statusCode === 401) {
        if (!hasToken) {
          reason = '(No token provided)';
        } else {
          reason = '(Invalid or expired token)';
        }
      } else if (res.statusCode === 403) {
        reason = '(Insufficient permissions)';
      }
      
      log(`${statusEmoji} ${level} - ${req.method} ${req.path} → ${res.statusCode} ${reason}`);
    }
    
    // Log for public endpoint
    if (isPublicEndpoint && req.path === '/public') {
      if (res.statusCode < 400) {
        log(`${statusEmoji} Public endpoint accessed - ${req.method} ${req.path} → ${res.statusCode}`);
      } else {
        log(`${statusEmoji} Public endpoint error - ${req.method} ${req.path} → ${res.statusCode}`);
      }
    }
  });
  
  next();
});

app.get('/secured', keycloak.protect('nodejs-oauth-backend:user'), function (req, res) {
  // Log successful authorization with user info
  const grant = req.kauth && req.kauth.grant;
  if (grant) {
    const token = grant.access_token;
    const username = token.content.preferred_username || token.content.sub;
    const roles = token.content.resource_access?.['nodejs-oauth-backend']?.roles || [];
    
    console.log(`  └─ ✓ Access GRANTED to user: ${username}`);
    console.log(`  └─ Client roles: ${JSON.stringify(roles)}`);
  }
  
  res.setHeader('content-type', 'text/plain');
  res.send('Secret message!');
});

// Error handler for keycloak authentication errors
app.use(function(err, req, res, next) {
  if (err) {
    logError(`Authentication error on ${req.method} ${req.path}:`);
    logError(`  └─ ${err.message || err}`);
    
    // Log stack trace for debugging
    if (err.stack) {
      console.error(err.stack);
    }
  }
  next(err);
});

app.get('/public', function (req, res) {
  res.setHeader('content-type', 'text/plain');
  res.send('Public message!');
});

app.get('/', function (req, res) {
  res.send('<html><body><ul><li><a href="/public">Public endpoint</a></li><li><a href="/secured">Secured endpoint</a></li></ul></body></html>');
});

app.listen(3000, function () {
  log('Started OAuth Playground backend on port 3000');
  console.log('Endpoints:');
  console.log('  - GET /secured (requires nodejs-oauth-backend:user role)');
  console.log('  - GET /public (no authentication required)');
});