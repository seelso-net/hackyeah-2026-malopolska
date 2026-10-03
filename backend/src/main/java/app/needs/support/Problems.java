package app.needs.support;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

/** HTTP errors with a small JSON body: {"status": 404, "error": "..."}. */
public final class Problems {

    private Problems() {
    }

    public static WebApplicationException notFound(String message) {
        return of(Response.Status.NOT_FOUND, message);
    }

    public static WebApplicationException badRequest(String message) {
        return of(Response.Status.BAD_REQUEST, message);
    }

    public static WebApplicationException forbidden(String message) {
        return of(Response.Status.FORBIDDEN, message);
    }

    public static WebApplicationException unauthorized(String message) {
        return of(Response.Status.UNAUTHORIZED, message);
    }

    public static WebApplicationException conflict(String message) {
        return of(Response.Status.CONFLICT, message);
    }

    private static WebApplicationException of(Response.Status status, String message) {
        return new WebApplicationException(message, Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("status", status.getStatusCode(), "error", message))
                .build());
    }
}
