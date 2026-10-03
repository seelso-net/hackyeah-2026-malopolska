package app.needs.ai;

import app.needs.ai.AiTypes.AnalysisInput;
import app.needs.ai.AiTypes.DoerOption;
import app.needs.ai.AiTypes.DoerPick;
import app.needs.ai.AiTypes.DraftInput;
import app.needs.ai.AiTypes.DraftResult;
import app.needs.ai.AiTypes.MatchDecision;
import app.needs.ai.AiTypes.MatchInput;
import app.needs.ai.AiTypes.PlaybookOption;
import app.needs.ai.AiTypes.ReportAnalysis;
import app.needs.ai.AiTypes.UpdateNote;
import app.needs.model.ActorKind;
import app.needs.model.CommunityConfig;
import app.needs.model.CommunityConfig.Category;
import app.needs.model.LocalizedText;
import app.needs.model.PlaybookStep;
import app.needs.model.ReportKind;
import app.needs.model.Urgency;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * AI without AI: keyword rules, hashed embeddings and sentence templates in en / pl / uk.
 * Runs with no API key, so the demo works offline; it is also the fallback when an LLM call fails.
 */
@ApplicationScoped
public class OfflineAi implements AiEngine {

    /** Keyword stems per category code, already lower-case and without diacritics. A leading space means "word starts with". */
    private static final Map<String, List<String>> KEYWORDS = Map.ofEntries(
            Map.entry("seniors", List.of(" senior", " elder", " older", " pensioner", " lift", " elevator", " stairs", " stair",
                    " floor", " walk up", " windy", " winda", " schod", " pietr", " pieter", " starsz", " emeryt", " babci",
                    " dziad", "ліфт", "поверх", "сход", "літн", "пенсіон")),
            Map.entry("safety", List.of(" light", " lamp", " dark", " unsafe", " danger", " crime", " scared", " latarn",
                    " ciemn", " niebezp", " oswietl", " boje", "світл", "темн", "небезпе", "ліхтар")),
            Map.entry("streets", List.of(" road", " street", " pothole", " pavement", " sidewalk", " bus ", " tram", " traffic",
                    " crossing", " dziur", " ulic", " chodnik", " autobus", " tramwaj", " przejsci", " jezdni", "дорог",
                    "автобус", "тротуар")),
            Map.entry("green", List.of(" park", " tree", " bins", " bin ", " rubbish", " litter", " trash", " garbage",
                    " playground", " kosz", " smiec", " drzew", " zielen", " zabaw", "смітт", "парк", "дерев")),
            Map.entry("youth", List.of(" teen", " youth", " kids", " children", " school", " pupil", " mlodziez", " nastolat",
                    " dzieci", " szkol", " uczni", "молод", "діт", "школ", "підліт")),
            Map.entry("housing", List.of(" flat", " apartment", " rent", " heating", " mould", " landlord", " mieszkan",
                    " czynsz", " ogrzew", " grzyb", "житл", "квартир", "опален")),
            Map.entry("culture", List.of(" concert", " event", " festival", " theatre", " koncert", " wydarzen", " festyn",
                    " teatr", "концерт", "фестив")),
            Map.entry("ideas", List.of(" idea", " propose", " suggest", " could we", " pomysl", " propon", " moze by",
                    "ідея", "пропон")),
            Map.entry("study", List.of(" study", " desk", " quiet room", " nauk", " czytelni", "навчан")),
            Map.entry("mental-health", List.of(" stress", " anxiety", " mental", " lonely", " burnout", " stres",
                    " psycholog", " depres", "стрес", "тривог")),
            Map.entry("accessibility", List.of(" wheelchair", " ramp", " accessib", " blind", " wozek", " podjazd", " dostep",
                    " niewidom", "пандус", "візок")),
            Map.entry("student-housing", List.of(" dorm", " akademik", " pokoj", "гуртож")),
            Map.entry("night-safety", List.of(" night", " dark", " noc", " ciemn", "ніч", "темн")),
            Map.entry("clubs", List.of(" club", " society", " kolo ", " klub", " zajeci", "гурт", "клуб")),
            Map.entry("repairs", List.of(" broken", " leak", " repair", " zepsut", " przecie", " napraw", " awari", " cieknie",
                    "зламан", "ремонт", "протіка")),
            Map.entry("shared-spaces", List.of(" laundry", " basement", " bike", " storage", " pralni", " piwnic", " rower",
                    " suszarni", "пральн", "підвал")),
            Map.entry("neighbours", List.of(" noise", " neighbour", " neighbor", " halas", " sasiad", "шум", "сусід")),
            Map.entry("courtyard", List.of(" courtyard", " yard", " podwork", " podworz", " trawnik", "подвір")));

    private static final List<String> EMERGENCY = List.of(" fire", " smoke", " gas leak", " unconscious", " bleeding",
            " not breathing", " pozar", " dym ", " ulatnia", " nieprzytom", " krwaw", " nie oddycha", "пожеж", "газ ",
            "непритом");

    /** Someone got hurt: urgent, and moderators get a safety alert right away. */
    private static final List<String> INJURY = List.of(" injur", " hurt", " fell ", " fall ", " fallen", " przewroci",
            " upadl", " wypad", " uraz", " zlama", " rann", "травм", "впав", "впала", "поран");

    private static final List<String> HIGH = List.of(" danger", " unsafe", " dark", " broken glass", " niebezp", " ciemn",
            " szklo", " boje", "небезпе", "темн");

    static final Map<String, String> PLAYBOOK_REASON = Map.of(
            "en", "Proven in %s with the same kind of problem.",
            "pl", "Sprawdzone w: %s, przy podobnym problemie.",
            "uk", "Перевірено в: %s, для подібної проблеми.");

    static final Map<String, String> NO_PLAYBOOK_REASON = Map.of(
            "en", "No proven playbook fits yet. Plan this case by hand; when it closes, it becomes the first playbook.",
            "pl", "Żaden sprawdzony scenariusz jeszcze nie pasuje. Zaplanuj sprawę ręcznie; po zamknięciu stanie się pierwszym scenariuszem.",
            "uk", "Жоден перевірений сценарій ще не підходить. Сплануйте справу вручну; після закриття вона стане першим сценарієм.");

    static final Map<String, String> ORG_REASON = Map.of(
            "en", "Works on %s and covers this area.",
            "pl", "Działa w obszarze „%s” i w tej okolicy.",
            "uk", "Працює у сфері «%s» і в цьому районі.");

    static final Map<String, String> VOLUNTEER_REASON = Map.of(
            "en", "Lives about %d m away and offers help with %s.",
            "pl", "Mieszka około %d m stąd i oferuje pomoc: %s.",
            "uk", "Живе приблизно за %d м і пропонує допомогу: %s.");

    static final Map<String, String> EXTRA_REASON = Map.of(
            "en", "Could help too; check before adding.",
            "pl", "Też może pomóc; sprawdź przed dodaniem.",
            "uk", "Також може допомогти; перевірте перед додаванням.");

    static final Map<String, String> CHANGE_NOTES = Map.of(
            "en", "Drafted from case R-%04d (%s). Updates from doers: %d. Residents who said it helped: %d of %d.",
            "pl", "Szkic na podstawie sprawy R-%04d (%s). Aktualizacje od wykonawców: %d. Mieszkańcy, którzy potwierdzili, że pomogło: %d z %d.",
            "uk", "Чернетка на основі справи R-%04d (%s). Оновлення від виконавців: %d. Мешканці, які підтвердили, що це допомогло: %d з %d.");

    @Override
    public String id() {
        return "offline";
    }

    @Override
    public String embedderId() {
        return "offline-hash-v2";
    }

    @Override
    public ReportAnalysis analyze(AnalysisInput in) {
        String text = in.text();
        String lang = TextTools.detectLanguage(text);
        String norm = " " + TextTools.normalize(text) + " ";
        Urgency urgency = urgency(norm, in.kind());
        boolean safety = urgency == Urgency.EMERGENCY || INJURY.stream().anyMatch(norm::contains);
        return new ReportAnalysis(lang, category(norm, in.kind(), in.config()), urgency, safety,
                LocalizedText.of(lang, TextTools.title(text)), LocalizedText.of(lang, TextTools.summary(text)));
    }

    String category(String norm, ReportKind kind, CommunityConfig cfg) {
        String best = null;
        int bestScore = 0;
        for (Category c : cfg.categories()) {
            int score = 0;
            for (String keyword : keywords(c)) {
                if (norm.contains(keyword)) {
                    score++;
                }
            }
            if (score > bestScore) {
                best = c.code();
                bestScore = score;
            }
        }
        if (best != null) {
            return best;
        }
        if (kind == ReportKind.IDEA && cfg.category("ideas").isPresent()) {
            return "ideas";
        }
        return cfg.categories().isEmpty() ? null : cfg.categories().get(0).code();
    }

    private static List<String> keywords(Category c) {
        List<String> builtIn = KEYWORDS.get(c.code());
        if (builtIn != null) {
            return builtIn;
        }
        // A community-defined category without built-in keywords: use the words of its labels.
        List<String> out = new ArrayList<>();
        for (String label : c.labels().values()) {
            for (String token : TextTools.tokens(TextTools.normalize(label))) {
                out.add(" " + (token.length() > 6 ? token.substring(0, 6) : token));
            }
        }
        return out;
    }

    private static Urgency urgency(String norm, ReportKind kind) {
        if (EMERGENCY.stream().anyMatch(norm::contains)) {
            return Urgency.EMERGENCY;
        }
        if (kind != ReportKind.NEED) {
            return Urgency.LOW;
        }
        boolean high = HIGH.stream().anyMatch(norm::contains) || INJURY.stream().anyMatch(norm::contains);
        return high ? Urgency.HIGH : Urgency.MEDIUM;
    }

    @Override
    public float[] embed(String text) {
        return TextTools.hashEmbedding(text);
    }

    @Override
    public Map<String, String> translate(String text, String sourceLang, List<String> targetLangs) {
        return Map.of();
    }

    @Override
    public MatchDecision match(MatchInput in) {
        // A shared category is a hint, not proof: the playbook must also be about the same thing.
        double min = in.minPlaybookSimilarity();
        PlaybookOption best = in.playbooks().stream()
                .filter(p -> p.similarity() >= min || (p.categoryMatch() && p.similarity() >= min * 0.75))
                .max(Comparator.comparingDouble(p -> (p.categoryMatch() ? 0.5 : 0) + p.similarity()))
                .orElse(null);

        List<DoerOption> orgs = in.doers().stream()
                .filter(d -> d.kind() != ActorKind.VOLUNTEER && d.categoryMatch())
                .sorted(Comparator.comparingInt((DoerOption d) -> kindRank(d.kind()))
                        .thenComparing(Comparator.comparingDouble(DoerOption::similarity).reversed()))
                .limit(2)
                .toList();
        if (orgs.isEmpty()) {
            orgs = in.doers().stream()
                    .filter(d -> d.kind() != ActorKind.VOLUNTEER)
                    .max(Comparator.comparingDouble(DoerOption::similarity))
                    .stream().toList();
        }
        List<DoerOption> volunteers = in.doers().stream()
                .filter(d -> d.kind() == ActorKind.VOLUNTEER && d.categoryMatch() && withinReach(d))
                .sorted(Comparator.comparingInt(d -> d.distanceMeters() == null ? Integer.MAX_VALUE : d.distanceMeters()))
                .limit(3)
                .toList();

        List<DoerPick> picks = new ArrayList<>();
        Set<Object> chosen = new HashSet<>();
        for (DoerOption d : orgs) {
            picks.add(new DoerPick(d.actorId(), localized(ORG_REASON, in.locales(),
                    lang -> new Object[] {label(in.categoryLabels(), lang, in.categoryCode())}), score(d), true));
            chosen.add(d.actorId());
        }
        for (DoerOption d : volunteers) {
            int meters = d.distanceMeters() == null ? 0 : roundTo(d.distanceMeters(), 50);
            picks.add(new DoerPick(d.actorId(), localized(VOLUNTEER_REASON, in.locales(),
                    lang -> new Object[] {meters, label(in.categoryLabels(), lang, in.categoryCode())}), score(d), true));
            chosen.add(d.actorId());
        }
        in.doers().stream()
                .filter(d -> !chosen.contains(d.actorId()))
                .sorted(Comparator.comparingDouble(DoerOption::similarity).reversed())
                .limit(2)
                .forEach(d -> picks.add(new DoerPick(d.actorId(),
                        localized(EXTRA_REASON, in.locales(), lang -> new Object[0]), score(d), false)));

        LocalizedText reason = best == null
                ? localized(NO_PLAYBOOK_REASON, in.locales(), lang -> new Object[0])
                : localized(PLAYBOOK_REASON, in.locales(),
                        lang -> new Object[] {label(best.originNames(), lang, "")});
        return new MatchDecision(best == null ? null : best.playbookId(), reason, picks);
    }

    private static boolean withinReach(DoerOption d) {
        if (d.distanceMeters() == null) {
            return true;
        }
        int reach = d.serviceRadiusMeters() == null ? 1500 : Math.max(d.serviceRadiusMeters(), 300);
        return d.distanceMeters() <= reach;
    }

    private static int kindRank(ActorKind kind) {
        return switch (kind) {
            case NGO -> 0;
            case INSTITUTION -> 1;
            case VOLUNTEER_GROUP -> 2;
            case BUSINESS -> 3;
            case VOLUNTEER -> 4;
        };
    }

    private static double score(DoerOption d) {
        return Math.round(((d.categoryMatch() ? 0.5 : 0) + d.similarity()) * 100) / 100.0;
    }

    private static int roundTo(int value, int step) {
        return Math.max(step, Math.round(value / (float) step) * step);
    }

    @Override
    public DraftResult draft(DraftInput in) {
        List<PlaybookStep> steps = new ArrayList<>();
        int position = 1;
        for (PlaybookStep s : in.currentSteps()) {
            steps.add(s.withPosition(position++, null));
        }
        List<UpdateNote> updates = in.updates();
        if (in.currentSteps().isEmpty()) {
            for (UpdateNote u : updates.stream().limit(5).toList()) {
                steps.add(stepFrom(u, position++));
            }
        } else if (!updates.isEmpty()) {
            // The latest update usually holds what the doers learned; it becomes a new step to review.
            steps.add(stepFrom(updates.get(updates.size() - 1), position));
        }
        LocalizedText notes = localized(CHANGE_NOTES, in.locales(), lang -> new Object[] {
                in.caseNumber(), label(in.communityNames(), lang, ""), updates.size(), in.helped(), in.participants()});
        return new DraftResult(steps, notes);
    }

    private static PlaybookStep stepFrom(UpdateNote u, int position) {
        Map<String, String> titles = new LinkedHashMap<>();
        u.text().values().forEach((lang, text) -> titles.put(lang, TextTools.title(text)));
        String role = u.actorKind() == null ? ActorKind.NGO.name() : u.actorKind();
        return new PlaybookStep(position, new LocalizedText(u.text().source(), titles), u.text(), List.of(role), "NEW");
    }

    /** Fills a template in every community language (English always included); no version is a translation. */
    static LocalizedText localized(Map<String, String> templates, List<String> locales,
                                   Function<String, Object[]> args) {
        Set<String> langs = new LinkedHashSet<>(locales);
        langs.add("en");
        Map<String, String> values = new LinkedHashMap<>();
        for (String lang : langs) {
            String template = templates.getOrDefault(lang, templates.get("en"));
            values.put(lang, String.format(template, args.apply(lang)));
        }
        return LocalizedText.generated(values);
    }

    static String label(Map<String, String> labels, String lang, String fallback) {
        return CommunityConfig.Labels.pick(labels, lang, fallback);
    }
}
