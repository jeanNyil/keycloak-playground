var express = require('express');
var app = express();
var stringReplace = require('string-replace-middleware');

var KC_URL = process.env.KC_URL || "http://localhost:8080/";
var INPUT_ISSUER = process.env.INPUT_ISSUER || "http://localhost:8080/realms/demo";

console.log('env KC_URL:', KC_URL);
console.log('env INPUT_ISSUER:', INPUT_ISSUER);

app.use(stringReplace({
   'KC_URL': KC_URL,
   'INPUT_ISSUER': INPUT_ISSUER
}));
app.use(express.static('.'))

app.get('/', function(req, res) {
    res.render('index.html');
});

app.listen(8000, function () {
    console.log('Started OIDC Playground on port 8000');
});