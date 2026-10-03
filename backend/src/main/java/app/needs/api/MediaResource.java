package app.needs.api;

import app.needs.model.AppUser;
import app.needs.model.CaseFile;
import app.needs.model.MediaAsset;
import app.needs.model.Report;
import app.needs.support.CurrentUser;
import app.needs.support.MediaStorage;
import app.needs.support.Problems;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.core.Response;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;

@jakarta.ws.rs.Path("/api/media")
@Tag(name = "Residents")
public class MediaResource {

    @Inject
    CurrentUser current;

    @Inject
    Access access;

    @Inject
    MediaStorage storage;

    @GET
    @jakarta.ws.rs.Path("/{id}")
    @Operation(summary = "A photo or voice note. Report media: author, moderators and assigned doers; update photos: "
            + "whoever can see the case. In <img> tags pass the demo login as ?as=")
    public Response get(@RestPath UUID id) {
        MediaAsset m = MediaAsset.<MediaAsset>findByIdOptional(id).orElseThrow(() -> Problems.notFound("Media not found."));
        if (m.report != null) {
            Report r = m.report;
            AppUser user = current.require();
            boolean author = r.author != null && r.author.id.equals(user.id);
            boolean allowed = author || current.isModerator(r.community.id)
                    || (r.caseFile != null && access.myAssignment(r.caseFile).isPresent());
            if (!allowed) {
                throw Problems.forbidden("Only the author, moderators and assigned doers can open this file.");
            }
        } else if (m.caseEvent != null) {
            CaseFile c = m.caseEvent.caseFile;
            access.requireSee(c);
        }
        Path file = storage.path(m.storageKey);
        if (!Files.exists(file)) {
            throw Problems.notFound("The file is gone from storage.");
        }
        return Response.ok(file.toFile(), m.mimeType)
                .header("Cache-Control", "private, max-age=3600")
                .build();
    }
}
