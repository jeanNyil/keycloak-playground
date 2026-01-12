# OAuth 2 Connect Playground

## Instructions to run locally

1. Open two terminals

2.  In the first terminal:
    1. Optionally, set the following environment variables according to your environment:
        - **`OTEL_EXPORTER_OTLP_ENDPOINT`**: OpenTelemetry Collector gRPC endpoint for distributed tracing (optional).
        ```shell
        export OTEL_EXPORTER_OTLP_ENDPOINT=<YOUR_OTEL_COLLECTOR_GRPC_ENDPOINT> # Default: http://localhost:4317
        ```
    2. Start the OAuth Playground backend REST API:
        ```shell
        cd 02-Oauth2/backend
        npm install
        npm start
        ```

3. In the second terminal:
    1. Optionally, set the following environment variables according to your Keycloak environment:
        - **`KC_URL`**: Keycloak server root URL.
        - **`INPUT_ISSUER`**: Keycloak server realm URL that issues OAuth tokens. This can also be updated on the OAuth Playground frontend UI.
        - **`OTEL_EXPORTER_OTLP_ENDPOINT`**: OpenTelemetry Collector gRPC endpoint for distributed tracing (optional).
        ```shell
        export KC_URL=<YOUR_KEYCLOAK_SERVER_ROOT_URL> # Default: http://localhost:8080/
        export INPUT_ISSUER=<YOUR_KEYCLOAK_ISSUER_REALM_URL> # Default: http://localhost:8080/realms/demo
        export OTEL_EXPORTER_OTLP_ENDPOINT=<YOUR_OTEL_COLLECTOR_GRPC_ENDPOINT> # Default: http://localhost:4317
        ```
    2. Start the OAuth Playground frontend:
        ```shell
        cd 02-Oauth2/frontend
        npm install
        npm start
        ```

4. Open the playground application at http://localhost:8000

    ![OAuth 2 Playground Application](../_images/oauth-playground-app.png)

    1. Load the OAuth 2.0 provider configuration by clicking on the button labelled **`Load OAuth 2.0 Provider Configuration`**
    2. Click on the button labeled **`2 - Authorization`**. 
    3. You can leave the **`client_id`** and **`scope`** values as they are, then click on the button labeled **`Send Authorization Request`**
    4. Now that the playground application has obtained an access token, try to invoke the REST API. Click on the button labeled **`3 - Invoke Service`**, then click on **`Invoke`**.
        >**NOTE**: The REST API will only grant access if the authenticated user has the **`oauth-backend:user`** client role and the access token **`aud`** claim is verified. **_Use access limitation by scope or client role assignment_**.