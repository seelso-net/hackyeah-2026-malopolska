package app.needs.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.needs.model.CommunityConfig.WorkflowState;
import app.needs.support.Geo;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** One lifecycle in the code; each community only renames or hides statuses. */
class CommunityConfigTest {

    private static final CommunityConfig COOP = new CommunityConfig(null, List.of(
            new WorkflowState(CaseStatus.NEW, Map.of("en", "New", "pl", "Nowe")),
            new WorkflowState(CaseStatus.MATCHED, Map.of("en", "Assigned", "pl", "Przydzielone")),
            new WorkflowState(CaseStatus.RESOLVED, Map.of("en", "Done", "pl", "Zrobione"))), null, null);

    @Test
    void hiddenStatusesShowTheLastVisibleLabel() {
        assertEquals("Nowe", COOP.statusLabel(CaseStatus.NEW, "pl"));
        assertEquals("Nowe", COOP.statusLabel(CaseStatus.SUGGESTED, "pl"), "SUGGESTED is hidden in the co-op");
        assertEquals("Assigned", COOP.statusLabel(CaseStatus.IN_PROGRESS, "en"));
        assertEquals("Done", COOP.statusLabel(CaseStatus.CLOSED, "en"));
    }

    @Test
    void labelsFallBackToEnglish() {
        assertEquals("Assigned", COOP.statusLabel(CaseStatus.MATCHED, "uk"));
    }

    @Test
    void rulesHaveSafeDefaults() {
        CommunityConfig.Rules rules = new CommunityConfig(null, null, null, null).rules();
        assertEquals(600, rules.mergeRadius());
        assertEquals(100, rules.locationRounding());
        assertEquals(14, rules.autoClose());
        assertTrue(rules.mapIsPublic());
    }

    @Test
    void publicLocationsSnapToTheGrid() {
        Geo.Point exact = new Geo.Point(52.24963, 21.04104);
        Geo.Point rounded = Geo.round(exact.lat(), exact.lng(), 100);
        double dLat = Math.abs(rounded.lat() - exact.lat()) * 111_320;
        double dLng = Math.abs(rounded.lng() - exact.lng()) * 111_320 * Math.cos(Math.toRadians(exact.lat()));
        assertTrue(dLat <= 50 && dLng <= 50, "moved at most half a grid cell");
        assertEquals(rounded, Geo.round(52.24961, 21.04101, 100), "neighbouring points share a pin");
    }
}
