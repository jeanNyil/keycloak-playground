# OpenID Connect Playground

## Instructions to run locally

1. Open a terminal

2. Run the OIDC playground application:
    ```shell
    cd 01-OIDC
    npm install
    npm start
    ```

4. Open the playground application at http://localhost:8000

    ![OpenID Connect Playground Application](../_images/oidc-playground-app.png)
    
    1. Load the OpenID provider configuration by clicking on the button labelled **`Load OpenID Provider Configuration`**
    2. Click on the button labeled **`2 - Authentication`** to generate an authentication request by clicking on **`Generate Authentication Request`**. Next, click on the button labeled **`Send Authentication Request`** and you will be redirected to the Keycloak login pages. If you want to experiment a bit you can, for example, try the following steps:
        - **`Set prompt to login`**: With this value, Keycloak should always ask you to re-authenticate.
        - **`Set max_age to 60`**:  With this value, Keycloak will re-authenticate you if you wait for at least 60 seconds since the last time you authenticated.
        - **`Set login_hint to your username`**: This should prefill the username in the Keycloak login page.

        >**NOTE**: If you try any of the preceding steps, don't forget to generate and send the authentication request again to see how Keycloak behaves.

        After Keycloak has redirected back to the playground application, you will see the authentication response in the **`Authentication Response`** section. The code is what is called the **`authorization code`**, which the application uses to obtain the ID token and the refresh token.
    3. Click on the button labeled **`3 - Token`**. You will see the authorization code has already been filled in on the form so you can go ahead and click on the button labeled **`Send Token Request`**.
    4. Click on **`5 - UserInfo`** to invoke the UserInfo endpoint. Under **`UserInfo Request`**, you will see that playground application is sending a request to the Keycloak UserInfo endpoint, including the access token in the authorization header.