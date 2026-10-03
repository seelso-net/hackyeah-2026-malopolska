package app.needs.support;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * Stores photos and voice notes on local disk under app.media.dir. The database keeps only the key,
 * so swapping this class for S3 or Cloudflare R2 changes nothing else.
 */
@ApplicationScoped
public class MediaStorage {

    @ConfigProperty(name = "app.media.dir", defaultValue = "data/media")
    String dir;

    public record StoredFile(String key, String mimeType, long sizeBytes) {
    }

    public StoredFile store(FileUpload upload) {
        String name = upload.fileName() == null ? "" : upload.fileName();
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot).toLowerCase(Locale.ROOT).replaceAll("[^.a-z0-9]", "") : "";
        String key = LocalDate.now() + "/" + UUID.randomUUID() + ext;
        try {
            Path target = path(key);
            Files.createDirectories(target.getParent());
            Files.copy(upload.uploadedFile(), target, StandardCopyOption.REPLACE_EXISTING);
            String type = upload.contentType() == null ? "application/octet-stream" : upload.contentType();
            return new StoredFile(key, type, Files.size(target));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store " + name, e);
        }
    }

    public Path path(String key) {
        Path base = Path.of(dir).toAbsolutePath().normalize();
        Path p = base.resolve(key).normalize();
        if (!p.startsWith(base)) {
            throw Problems.badRequest("Invalid media key");
        }
        return p;
    }
}
