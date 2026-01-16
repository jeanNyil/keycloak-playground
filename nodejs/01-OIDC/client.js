/****************************/
/* OpenID Connect functions */
/****************************/

// Load the OpenID Provider Configuration (via proxy for distributed tracing)
function loadDiscovery() {
    var issuer = getInput('input-issuer');
    setState('issuer', issuer);

    var req = new XMLHttpRequest();
    req.onreadystatechange = function() {
        if (req.readyState === 4) {
            setState('discovery', JSON.parse(req.responseText));
            setOutput('output-discovery', state.discovery);
        }
    }
    // Use proxy endpoint for distributed tracing
    req.open('GET', '/api/keycloak/discovery?issuer=' + encodeURIComponent(issuer), true);
    req.send();
}

// Create an Authentication Request
function generateAuthenticationRequest() {
    var req = state.discovery['authorization_endpoint'];

    var clientId = getInput('input-clientid');
    var scope = getInput('input-scope');
    var prompt = getInput('input-prompt');
    var maxAge = getInput('input-maxage');
    var loginHint = getInput('input-loginhint');

    var authenticationInput = {
        clientId: clientId,
        scope: scope,
        prompt: prompt,
        maxAge: maxAge,
        loginHint: loginHint
    }
    setState('authenticationInput', authenticationInput);

    req += '?client_id=' + clientId;
    req += '&response_type=code';
    req += '&redirect_uri=' + document.location.href.split('?')[0];
    if ('' !== scope) {
        req += '&scope=' + scope;
    }
    if ('' !== prompt) {
        req += '&prompt=' + prompt;
    }
    if ('' !== maxAge) {
        req += '&max_age=' + maxAge;
    }
    if ('' !== loginHint) {
        req += '&login_hint=' + loginHint;
    }

    setOutput('output-authenticationRequest', req.replace('?', '<br/><br/>').replaceAll('&', '<br/>'));
    document.getElementById('authenticationRequestLink').onclick = function() {
        document.location.href = req;
    }
}

// Create a Token Exchange Request (via proxy for distributed tracing)
function loadTokens() {
    var code = getInput('input-code');
    var clientId = getInput('input-clientid');

    // Use proxy endpoint for distributed tracing
    var proxyParams = {
        token_endpoint: state.discovery['token_endpoint'],
        grant_type: 'authorization_code',
        code: code,
        client_id: clientId,
        redirect_uri: document.location.href.split('?')[0]
    };

    var req = new XMLHttpRequest();
    req.onreadystatechange = function() {
        if (req.readyState === 4) {
            var response = JSON.parse(req.responseText);
            setOutput('output-response', req.responseText);

            if (response['id_token']) {
                var idToken = response['id_token'].split('.');
                var idTokenHeader = JSON.parse(base64UrlDecode(idToken[0]));
                var idTokenBody = JSON.parse(base64UrlDecode(idToken[1]));
                var idTokenSignature = idToken[2];
                setOutput('output-idtokenHeader', idTokenHeader);
                setOutput('output-idtoken', idTokenBody);
                setOutput('output-idtokenSignature', idTokenSignature);
                setState('refreshToken', response['refresh_token']);
                setState('idToken', response['id_token']);
                setState('accessToken', response['access_token']);
            } else {
                setOutput('output-idtoken', '');
            }
        }
    }
    req.open('POST', '/api/keycloak/token', true);
    req.setRequestHeader('Content-type', 'application/json');

    setOutput('output-tokenRequest', state.discovery['token_endpoint'] + '<br/><br/>' + 
        'grant_type=authorization_code<br/>code=' + code + '<br/>client_id=' + clientId + 
        '<br/>redirect_uri=' + document.location.href.split('?')[0]);

    req.send(JSON.stringify(proxyParams));

    window.history.pushState({}, document.title, '/');
}

// Create a Refresh Token Request (via proxy for distributed tracing)
function refreshTokens() {
    var code = getInput('input-code');
    var clientId = getInput('input-clientid');

    // Use proxy endpoint for distributed tracing
    var proxyParams = {
        token_endpoint: state.discovery['token_endpoint'],
        grant_type: 'refresh_token',
        refresh_token: state.refreshToken,
        client_id: clientId,
        scope: 'openid'
    };

    var req = new XMLHttpRequest();
    req.onreadystatechange = function() {
        if (req.readyState === 4) {
            var response = JSON.parse(req.responseText);
            setOutput('output-refreshResponse', req.responseText);

            if (response['id_token']) {
                var idToken = JSON.parse(base64UrlDecode(response['id_token'].split('.')[1]));
                setOutput('output-idtokenRefreshed', idToken);
                setState('refreshToken', response['refresh_token']);
            } else {
                setOutput('output-idtokenRefreshed', '');
            }
        }
    }
    req.open('POST', '/api/keycloak/token', true);
    req.setRequestHeader('Content-type', 'application/json');

    setOutput('output-refreshRequest', state.discovery['token_endpoint'] + '<br/><br/>' + 
        'grant_type=refresh_token<br/>refresh_token=' + state.refreshToken + 
        '<br/>client_id=' + clientId + '<br/>scope=openid');

    req.send(JSON.stringify(proxyParams));

    window.history.pushState({}, document.title, '/');
}

// Create a UserInfo Request (via proxy for distributed tracing)
function userInfo() {
    var req = new XMLHttpRequest();
    req.onreadystatechange = function() {
        if (req.readyState === 4) {
            var response = JSON.parse(req.responseText);
            setOutput('output-userInfoResponse', req.responseText);
        }
    }
    // Use proxy endpoint for distributed tracing
    var proxyUrl = '/api/keycloak/userinfo?endpoint=' + encodeURIComponent(state.discovery['userinfo_endpoint']);
    req.open('GET', proxyUrl, true);
    req.setRequestHeader('Authorization', 'Bearer ' + state.accessToken);

    setOutput('output-userInfoRequest', state.discovery['userinfo_endpoint'] + '<br/><br/>' + 'Authorization: Bearer ' + state.accessToken);

    req.send();

    window.history.pushState({}, document.title, '/');
}

/*************************/
/* Application functions */
/*************************/

var steps = ['discovery', 'authentication', 'token', 'refresh', 'userinfo']
var state = loadState();

function reset() {
    localStorage.removeItem('state');
    window.location.reload();
}

// Logout from Keycloak and clear local state (via proxy for distributed tracing)
function logout() {
    if (!state.discovery || !state.discovery['end_session_endpoint']) {
        alert('Please load discovery first to enable logout');
        return;
    }

    var idTokenHint = state.idToken || '';
    var postLogoutRedirectUri = document.location.href.split('?')[0];

    // Clear local state first
    localStorage.removeItem('state');

    // Redirect to logout proxy endpoint
    var logoutUrl = '/api/keycloak/logout?' + 
        'end_session_endpoint=' + encodeURIComponent(state.discovery['end_session_endpoint']) +
        '&post_logout_redirect_uri=' + encodeURIComponent(postLogoutRedirectUri);
    if (idTokenHint) {
        logoutUrl += '&id_token_hint=' + encodeURIComponent(idTokenHint);
    }

    window.location.href = logoutUrl;
}

function loadState() {
   var s = localStorage.getItem('state');
   if (s) {
       return JSON.parse(s);
   } else {
       return {
           step: 'discovery'
       }
   }
}

function setState(key, value) {
    state[key] = value;
    localStorage.setItem('state', JSON.stringify(state));
}

function step(step) {
    setState('step', step);
    for (i = 0; i < steps.length; i++) {
        document.getElementById('step-' + steps[i]).style.display = steps[i] === step ? 'block' : 'none'
    }
    setState('step', step);

    switch(step) {
        case 'discovery':
            if (state.issuer) {
                setInput('input-issuer', state.issuer);
            }
            break;
        case 'authentication':
            var authenticationInput = state.authenticationInput;
            if (authenticationInput) {
                setInput('input-clientid', authenticationInput.clientId);
                setInput('input-scope', authenticationInput.scope);
                setInput('input-prompt', authenticationInput.prompt);
                setInput('input-maxage', authenticationInput.maxAge);
                setInput('input-loginhint', authenticationInput.loginHint);
                setOutput('output-authenticationResponse', '');
            }
            break;
    }
}

function getInput(id) {
    return document.getElementById(id).value
}

function setInput(id, value) {
    return document.getElementById(id).value = value
}

function setOutput(id, value) {
    if (typeof value === 'object') {
        value = JSON.stringify(value, null, 2)
    } else if (value.startsWith('{')) {
        value = JSON.stringify(JSON.parse(value), null, 2)
    }
    document.getElementById(id).innerHTML = value;
}

function getQueryVariable(key) {
    var query = window.location.search.substring(1);
    var vars = query.split('&');
    for (var i = 0; i < vars.length; i++) {
        var pair = vars[i].split('=');
        if (decodeURIComponent(pair[0]) == key) {
            return decodeURIComponent(pair[1]);
        }
    }
}

function base64UrlDecode(input) {
    input = input
        .replace(/-/g, '+')
        .replace(/_/g, '/');

    var pad = input.length % 4;
    if(pad) {
      if(pad === 1) {
        throw new Error('InvalidLengthError: Input base64url string is the wrong length to determine padding');
      }
      input += new Array(5-pad).join('=');
    }

    return atob(input);
}

function init() {
    step(state.step);
    if (state.discovery) {
        setOutput('output-discovery', state.discovery);
    }

    var code = getQueryVariable('code');
    if (code) {
        setInput('input-code', code);
        setOutput('output-authenticationResponse', 'code=' + code);
    }

    var error = getQueryVariable('error');
    var errorDescription = getQueryVariable('error_description');
    if (error) {
        setOutput('output-authenticationResponse', 'error=' + error + '<br/>error_description=' + errorDescription);
    }
}