package app.needs.api;

import app.needs.ai.Ai;
import app.needs.service.BackgroundJobs;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Startup;

/** /q/health/started turns UP once startup vectors and suggestions exist (a Kubernetes startup probe, or the demo script). */
@Startup
@ApplicationScoped
public class AiStartupCheck implements HealthCheck {

    @Inject
    BackgroundJobs jobs;

    @Inject
    Ai ai;

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("ai-startup")
                .status(jobs.ready())
                .withData("engine", ai.engineId())
                .build();
    }
}
