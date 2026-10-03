package app.needs.api;

import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.hibernate.StaleObjectStateException;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/** Small JSON errors for the exceptions bad input or concurrent edits cause, instead of a 500 page. */
public class ErrorMappers {

    @ServerExceptionMapper
    public Response badValue(IllegalArgumentException e) {
        return error(Response.Status.BAD_REQUEST, e.getMessage() == null ? "Invalid value." : e.getMessage());
    }

    @ServerExceptionMapper({OptimisticLockException.class, StaleObjectStateException.class})
    public Response concurrentEdit(RuntimeException e) {
        return error(Response.Status.CONFLICT, "Someone changed this case at the same moment. Reload and try again.");
    }

    private static Response error(Response.Status status, String message) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("status", status.getStatusCode(), "error", message))
                .build();
    }
}
