
package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import io.javalin.Javalin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class IngestionServiceApp {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7030);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/wards", ctx -> {
            try {
                List<Map<String, Object>> wards = readAndCleanWards();
                ctx.contentType("application/json");
                ctx.result(mapper.writeValueAsString(wards));
            } catch (Exception e) {
                ctx.status(500).result("Failed to process ward data");
                e.printStackTrace();
            }
        });
    }

    static List<Map<String, Object>> readAndCleanWards()
            throws Exception {

        Map<String, Map<String, Object>> wards = new LinkedHashMap<>();

        InputStream input = IngestionServiceApp.class
                .getResourceAsStream("/wards-outdated.csv");

        if (input == null) {
            throw new IllegalStateException("CSV file not found");
        }

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {

            reader.readNext(); // Skip column headings

            String[] row;

            while ((row = reader.readNext()) != null) {
                if (row.length != 4) {
                    continue; // Malformed row
                }

                String id = row[0].trim().toUpperCase();
                String wing = normalize(row[1]);
                String department = normalize(row[2]);

                List<String> notes = new ArrayList<>();
                Integer beds = parseBeds(row[3], notes);

                if (id.isBlank()) {
                    continue;
                }

                if (wing == null) {
                    notes.add("Wing is missing");
                }

                if (department == null) {
                    notes.add("Department is missing");
                }

                Map<String, Object> ward = new LinkedHashMap<>();
                ward.put("wardId", id);
                ward.put("wing", wing);
                ward.put("department", department);
                ward.put("bedsAvailable", beds);
                ward.put("notes", notes);

                if (wards.containsKey(id)) {
                    List<String> existingNotes =
                            (List<String>) wards.get(id).get("notes");

                    for (String note : notes) {
                        existingNotes.add("Duplicate record: " + note);
                    }
                    continue;
                }

                wards.put(id, ward);
            }
        }

        return new ArrayList<>(wards.values());
    }

    private static String normalize(String text) {
        String value = text.trim().replaceAll("\\s+", " ");

        if (value.isBlank()
                || value.equalsIgnoreCase("N/A")
                || value.equalsIgnoreCase("TBD")
                || value.equalsIgnoreCase("unknown")
                || value.equalsIgnoreCase("NaN")
                || value.equals("-")) {
            return null;
        }

        if (value.equalsIgnoreCase("pediatrics")) {
            return "Paediatrics";
        }

        StringBuilder result = new StringBuilder();

        for (String word : value.toLowerCase().split(" ")) {
            if (!result.isEmpty()) {
                result.append(" ");
            }

            result.append(
                    Character.toUpperCase(word.charAt(0))
                            + word.substring(1)
            );
        }

        return result.toString();
    }

    private static Integer parseBeds(String value, List<String> notes) {
        String cleaned = value.trim();

        try {
            int beds = Integer.parseInt(cleaned);

            if (beds < 0 || beds > 100) {
                notes.add("Invalid bed count: " + cleaned);
                return null;
            }

            return beds;
        } catch (NumberFormatException e) {
            notes.add("Non-numeric bed count: " + cleaned);
            return null;
        }
    }
}
