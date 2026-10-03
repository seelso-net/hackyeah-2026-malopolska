package app.needs.support;

import app.needs.model.AppUser;
import app.needs.model.Community;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Picks the reader's language for a community: ?lang= or the X-Lang header (the app's language switch),
 * the user's saved locale, Accept-Language, then the community default. Only languages the community lists are used.
 */
@RequestScoped
public class Locales {

    @Inject
    HttpServerRequest request;

    @Inject
    CurrentUser current;

    public String pick(Community community) {
        List<String> supported = community.localeList();
        String explicit = request.getParam("lang");
        if (explicit == null) {
            explicit = request.getHeader("X-Lang");
        }
        if (explicit != null && supported.contains(explicit)) {
            return explicit;
        }
        Optional<AppUser> user = current.find();
        if (user.isPresent() && user.get().locale != null && supported.contains(user.get().locale)) {
            return user.get().locale;
        }
        String header = request.getHeader("Accept-Language");
        if (header != null) {
            for (String part : header.split(",")) {
                String code = part.split(";")[0].trim().toLowerCase(Locale.ROOT);
                if (code.length() > 2) {
                    code = code.substring(0, 2);
                }
                if (supported.contains(code)) {
                    return code;
                }
            }
        }
        return community.defaultLocale;
    }
}
