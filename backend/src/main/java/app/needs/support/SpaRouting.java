package app.needs.support;

import io.quarkus.logging.Log;
import io.quarkus.vertx.http.runtime.RouteConstants;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Serves the web app (built into META-INF/resources by the Dockerfile) for its own paths, so a link
 * like /case/42 opens the app instead of a 404. API, health and files with an extension pass through.
 * index.html is never cached, so a new deployment reaches everyone on their next visit.
 */
@ApplicationScoped
public class SpaRouting {

    private static final List<String> SERVER_PREFIXES = List.of("/api", "/q");
    private static final Pattern FILE = Pattern.compile(".*/[^/]+\\.[A-Za-z0-9]+$");

    void install(@Observes Router router) {
        if (Thread.currentThread().getContextClassLoader().getResource("META-INF/resources/index.html") == null) {
            Log.info("No web app bundled (META-INF/resources/index.html); serving the API only");
            return;
        }
        router.route().order(RouteConstants.ROUTE_ORDER_BEFORE_DEFAULT).handler(rc -> {
            String path = rc.normalizedPath();
            HttpMethod method = rc.request().method();
            if ((method != HttpMethod.GET && method != HttpMethod.HEAD) || isServerPath(path)) {
                rc.next();
                return;
            }
            if (FILE.matcher(path).matches()) {
                if (path.endsWith(".webmanifest")) {
                    // Vert.x does not know this extension; browsers expect the manifest media type.
                    rc.addHeadersEndHandler(v -> rc.response().headers().set("Content-Type", "application/manifest+json"));
                }
                rc.next();
                return;
            }
            rc.addHeadersEndHandler(v -> rc.response().headers().set("Cache-Control", "no-cache"));
            if (path.equals("/") || path.equals("/index.html")) {
                rc.next();
            } else {
                rc.reroute("/");
            }
        });
    }

    private static boolean isServerPath(String path) {
        return SERVER_PREFIXES.stream().anyMatch(p -> path.equals(p) || path.startsWith(p + "/"));
    }
}
