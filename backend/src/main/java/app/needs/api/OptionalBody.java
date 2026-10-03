package app.needs.api;

import app.needs.support.Problems;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Reads an optional JSON body sent with any content type (or none), so a bare
 * {@code curl -X POST .../accept} works as well as a browser sending {@code {}}.
 */
@ApplicationScoped
public class OptionalBody {

    @Inject
    ObjectMapper mapper;

    public <T> T read(String body, Class<T> type) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(body, type);
        } catch (JsonProcessingException e) {
            throw Problems.badRequest("Invalid JSON for " + type.getSimpleName() + ": " + e.getOriginalMessage());
        }
    }
}
