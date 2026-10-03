package app.needs.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.needs.ai.AiTypes.AnalysisInput;
import app.needs.ai.AiTypes.DoerOption;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The offline engine is what the demo runs on without an API key, so its rules are pinned down here. */
class OfflineAiTest {

    private final OfflineAi ai = new OfflineAi();

    private static final CommunityConfig RIVERSIDE = new CommunityConfig(List.of(
            new Category("seniors", Map.of("en", "Seniors", "pl", "Seniorzy")),
            new Category("safety", Map.of("en", "Safety", "pl", "Bezpieczeństwo")),
            new Category("streets", Map.of("en", "Streets", "pl", "Ulice")),
            new Category("green", Map.of("en", "Green spaces", "pl", "Zieleń")),
            new Category("ideas", Map.of("en", "Ideas", "pl", "Pomysły"))), null, null, null);

    private ReportAnalysis read(String text, ReportKind kind) {
        return ai.analyze(new AnalysisInput(text, kind, RIVERSIDE, List.of("pl", "en", "uk"), "pl"));
    }

    @Test
    void readsPolishUkrainianAndEnglishReports() {
        ReportAnalysis pl = read("Mama ma 82 lata i nie da rady zejść po schodach, w bloku nie ma windy.", ReportKind.NEED);
        assertEquals("pl", pl.language());
        assertEquals("seniors", pl.categoryCode());
        assertEquals(Urgency.MEDIUM, pl.urgency());

        ReportAnalysis uk = read("Я живу на п’ятому поверсі без ліфта і не можу спуститися сходами.", ReportKind.NEED);
        assertEquals("uk", uk.language());
        assertEquals("seniors", uk.categoryCode());

        ReportAnalysis en = read("The lamp on the riverside path is broken and it is dark and unsafe.", ReportKind.NEED);
        assertEquals("en", en.language());
        assertEquals("safety", en.categoryCode());
        assertEquals(Urgency.HIGH, en.urgency());
    }

    @Test
    void someoneHurtRaisesUrgencyAndASafetyAlert() {
        ReportAnalysis r = read("Chodnik jest cały w dziurach, starsza pani się dziś przewróciła.", ReportKind.NEED);
        assertEquals("streets", r.categoryCode());
        assertEquals(Urgency.HIGH, r.urgency());
        assertTrue(r.safetyConcern());

        ReportAnalysis fire = read("Smoke and fire in the basement of block 4!", ReportKind.NEED);
        assertEquals(Urgency.EMERGENCY, fire.urgency());
        assertTrue(fire.safetyConcern());
    }

    @Test
    void ideasAreNeverUrgent() {
        ReportAnalysis idea = read("Could we have a shelf of shared tools in the community centre?", ReportKind.IDEA);
        assertEquals("ideas", idea.categoryCode());
        assertEquals(Urgency.LOW, idea.urgency());
        assertFalse(idea.safetyConcern());
    }

    private static MatchInput matchInput(List<PlaybookOption> playbooks) {
        UUID ngo = UUID.randomUUID();
        UUID office = UUID.randomUUID();
        UUID nearVolunteer = UUID.randomUUID();
        UUID farVolunteer = UUID.randomUUID();
        List<DoerOption> doers = List.of(
                new DoerOption(ngo, "Good Neighbours", ActorKind.NGO, "", 0.3, true, 200, 3000),
                new DoerOption(office, "Welfare Office", ActorKind.INSTITUTION, "", 0.2, true, 900, 5000),
                new DoerOption(nearVolunteer, "Anna", ActorKind.VOLUNTEER, "", 0.2, true, 50, 500),
                new DoerOption(farVolunteer, "Piotr", ActorKind.VOLUNTEER, "", 0.2, true, 4000, 500));
        return new MatchInput(UUID.randomUUID(), "Seniors stuck in blocks without lifts", "seniors",
                Map.of("en", "Seniors", "pl", "Seniorzy"), List.of("pl", "en"), "pl", playbooks, doers, 0.2);
    }

    @Test
    void picksAProvenPlaybookAndNearbyDoers() {
        UUID stairBuddies = UUID.randomUUID();
        MatchDecision d = ai.match(matchInput(List.of(
                new PlaybookOption(stairBuddies, UUID.randomUUID(), Map.of("en", "Stair Buddies"), Map.of("en", "Old Town"), 0.35, true),
                new PlaybookOption(UUID.randomUUID(), UUID.randomUUID(), Map.of("en", "Repair café"), Map.of("en", "Riverside"), 0.07, false))));
        assertEquals(stairBuddies, d.playbookId());
        assertTrue(d.reason().in("en").contains("Old Town"));
        assertTrue(d.reason().generated(), "AI-written reasons are not translations of someone's words");
        long selected = d.doers().stream().filter(p -> p.selected()).count();
        assertEquals(3, selected, "the NGO, the office and the volunteer within reach; not the one 4 km away");
    }

    @Test
    void aSharedCategoryAloneIsNotAMatch() {
        MatchDecision d = ai.match(matchInput(List.of(
                new PlaybookOption(UUID.randomUUID(), UUID.randomUUID(), Map.of("en", "Fix a dark path"), Map.of("en", "Riverside"), 0.07, true))));
        assertNull(d.playbookId());
        assertTrue(d.reason().in("en").startsWith("No proven playbook"));
    }

    @Test
    void draftKeepsTheStepsAndAddsWhatTheDoersLearned() {
        PlaybookStep existing = new PlaybookStep(1, LocalizedText.of("en", "Find who needs help"),
                LocalizedText.of("en", "Ask the welfare office."), List.of("INSTITUTION"), null);
        UpdateNote learned = new UpdateNote(LocalizedText.of("pl",
                "Wywieście listę chętnych na drzwiach każdej klatki. Zgłosiło się 9 sąsiadów."), "NGO");
        DraftResult r = ai.draft(new DraftInput("Stair Buddies", List.of(existing), "Seniors stuck at home",
                List.of(learned), 1, 18, 412, Map.of("en", "Riverside"), List.of("pl", "en"), "pl"));
        assertEquals(2, r.steps().size());
        PlaybookStep added = r.steps().get(1);
        assertEquals("NEW", added.change());
        assertEquals("Wywieście listę chętnych na drzwiach każdej klatki", added.title().in("pl"));
        assertEquals(List.of("NGO"), added.roles());
        assertNotNull(r.changeNotes().in("en"));
        assertTrue(r.changeNotes().in("en").contains("R-0412"));
    }
}
