package co.wethinkcode.healthsafe;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IngestionServiceAppTest {

    @Test
    void shouldReturn17Wards() throws Exception {
        List<Map<String, Object>> wards =
                IngestionServiceApp.readAndCleanWards();

        assertEquals(17, wards.size());
    }

    @Test
    void shouldNormalizeWardIds() throws Exception {
        List<Map<String, Object>> wards =
                IngestionServiceApp.readAndCleanWards();

        Map<String, Object> ward = findWard(wards, "W-02");

        assertEquals("W-02", ward.get("wardId"));
        assertEquals("West Wing", ward.get("wing"));
        assertEquals("Paediatrics", ward.get("department"));
    }

    @Test
    void shouldRemoveDuplicateWards() throws Exception {
        List<Map<String, Object>> wards =
                IngestionServiceApp.readAndCleanWards();

        Set<Object> uniqueIds = new HashSet<>();

        for (Map<String, Object> ward : wards) {
            assertTrue(uniqueIds.add(ward.get("wardId")),
                    "Duplicate ward ID found");
        }

        Map<String, Object> ward05 = findWard(wards, "W-05");

        assertEquals(5, ward05.get("bedsAvailable"));
        assertFalse(getNotes(ward05).isEmpty());
    }

    @Test
    void shouldRejectInvalidBedCounts() throws Exception {
        List<Map<String, Object>> wards =
                IngestionServiceApp.readAndCleanWards();

        Map<String, Object> ward04 = findWard(wards, "W-04");
        Map<String, Object> ward12 = findWard(wards, "W-12");

        assertNull(ward04.get("bedsAvailable"));
        assertNull(ward12.get("bedsAvailable"));

        assertTrue(getNotes(ward04).stream()
                .anyMatch(note -> note.contains("Invalid bed count")));

        assertTrue(getNotes(ward12).stream()
                .anyMatch(note -> note.contains("Non-numeric bed count")));
    }

    @Test
    void shouldDetectMissingWing() throws Exception {
        List<Map<String, Object>> wards =
                IngestionServiceApp.readAndCleanWards();

        Map<String, Object> ward08 = findWard(wards, "W-08");

        assertNull(ward08.get("wing"));
        assertTrue(getNotes(ward08).contains("Wing is missing"));
    }

    private Map<String, Object> findWard(
            List<Map<String, Object>> wards, String id) {

        return wards.stream()
                .filter(ward -> id.equals(ward.get("wardId")))
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError("Ward not found: " + id));
    }

    @SuppressWarnings("unchecked")
    private List<String> getNotes(Map<String, Object> ward) {
        return (List<String>) ward.get("notes");
    }
}
