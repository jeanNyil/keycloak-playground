package org.keycloak;

import io.quarkus.oidc.runtime.OidcJwtCallerPrincipal;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

@Path("/")
public class OAuthServiceResource {

    private static final Logger LOG = Logger.getLogger(OAuthServiceResource.class);

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    @Path("/public")
    @PermitAll
    @Produces(MediaType.TEXT_PLAIN)
    public String publicEndpoint() {
        LOG.info("GET /public - Public endpoint accessed");
        return "Public message!";
    }

    @GET
    @Path("/secured")
    @RolesAllowed("user")
    @Produces(MediaType.TEXT_PLAIN)
    public String securedEndpoint() {
        String username = securityIdentity.getPrincipal().getName();
        
        // Log token validation details
        if (securityIdentity.getPrincipal() instanceof OidcJwtCallerPrincipal jwtPrincipal) {
            String audience = jwtPrincipal.getClaim("aud");
            String issuer = jwtPrincipal.getIssuer();
            
            LOG.infof("GET /secured - Token validation successful");
            LOG.infof("  └─ User: %s", username);
            LOG.infof("  └─ Issuer: %s", issuer);
            LOG.infof("  └─ Audience: %s (verified against 'quarkus-oauth-backend')", audience);
            LOG.infof("  └─ Roles: %s", securityIdentity.getRoles());
            LOG.infof("  └─ ✓ Access GRANTED - User '%s' has required 'user' role", username);
        } else {
            LOG.infof("GET /secured - Secured endpoint accessed by user: %s", username);
            LOG.infof("  └─ ✓ Access GRANTED - User '%s' has required 'user' role", username);
            LOG.infof("  └─ User roles: %s", securityIdentity.getRoles());
        }
        
        return "Secret message!";
    }
}
