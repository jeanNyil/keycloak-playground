package org.keycloak;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Reactive REST Client for communicating with the OAuth backend service.
 * Automatically propagates OpenTelemetry trace context.
 * Uses non-blocking I/O for better scalability.
 */
@RegisterRestClient(configKey = "backend-service")
@Path("/")
public interface BackendServiceClient {
    
    /**
     * Call the public endpoint (no authentication required)
     * @return Uni with the response message
     */
    @GET
    @Path("/public")
    @Produces(MediaType.TEXT_PLAIN)
    Uni<String> getPublic();
    
    /**
     * Call the secured endpoint (requires user role)
     * @param authorization Bearer token in format "Bearer {access_token}"
     * @return Uni with the response message
     */
    @GET
    @Path("/secured")
    @Produces(MediaType.TEXT_PLAIN)
    Uni<String> getSecured(@HeaderParam("Authorization") String authorization);
}
