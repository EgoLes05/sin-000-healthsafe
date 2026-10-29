
package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StaffingServiceApp {

    private static final String WARD_URL =
            "http://localhost:7031/wards/";

    private static final String ALERT_URL =
            "http://localhost:7032/alert-level";

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {

        Javalin app = Javalin.create().start(7033);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/schedule/{wardId}", ctx -> {

            String wardId = ctx.pathParam("wardId")
                    .trim()
                    .toUpperCase(Locale.ROOT);

            if (!wardId.matches("[A-Z0-9-]+")) {
                ctx.status(400).json(Map.of(
                        "error", "Invalid ward ID"
                ));
                return;
            }

            try {
                // 1. Check that the requested ward exists
                HttpResponse<String> wardResponse =
                        sendGet(WARD_URL + wardId);

                if (wardResponse.statusCode() == 404) {
                    ctx.status(404).json(Map.of(
                            "error", "Ward not found",
                            "wardId", wardId
                    ));
                    return;
                }

                if (wardResponse.statusCode() != 200) {
                    ctx.status(503).json(Map.of(
                            "error", "Ward Service unavailable"
                    ));
                    return;
                }

                JsonNode ward = mapper.readTree(wardResponse.body());
                String department = ward.path("department").asText();

                // 2. Read the emergency level
                HttpResponse<String> alertResponse =
                        sendGet(ALERT_URL);

                if (alertResponse.statusCode() != 200) {
                    ctx.status(503).json(Map.of(
                            "error", "Alert Level Service unavailable"
                    ));
                    return;
                }

                JsonNode alert = mapper.readTree(alertResponse.body());

                if (!alert.path("level").canConvertToInt()) {
                    ctx.status(502).json(Map.of(
                            "error", "Invalid emergency level response"
                    ));
                    return;
                }

                int level = alert.path("level").asInt();

                if (level < 0 || level > 8) {
                    ctx.status(502).json(Map.of(
                            "error", "Emergency level is outside 0-8"
                    ));
                    return;
                }

                // 3. Calculate required doctors
                int requiredDoctors;

                if (level <= 2) {
                    requiredDoctors = 1;
                } else if (level <= 5) {
                    requiredDoctors = 2;
                } else if (level <= 7) {
                    requiredDoctors = 3;
                } else {
                    requiredDoctors = 4;
                }

                // 4. Build the on-call schedule
                List<Map<String, Object>> schedule =
                        new ArrayList<>();

                for (int i = 1; i <= requiredDoctors; i++) {
                    schedule.add(Map.of(
                            "slot", i,
                            "role", "On-call doctor",
                            "department", department
                    ));
                }

                // 5. Return the schedule
                ctx.json(Map.of(
                        "wardId", wardId,
                        "department", department,
                        "alertLevel", level,
                        "requiredDoctors", requiredDoctors,
                        "codeBlue", level == 8,
                        "schedule", schedule
                ));

            } catch (Exception e) {
                    System.err.println("Staffing Service error:");
                    e.printStackTrace();

                ctx.status(503).json(Map.of(
                        "error", "Unable to communicate with dependent services"
                ));
            }
        });
    }

    private static HttpResponse<String> sendGet(String url)
            throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        return client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }
}
