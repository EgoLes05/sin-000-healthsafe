
package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class WardServiceApp {

    private static final String INGESTION_URL =
            "http://localhost:7030/wards";

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private static final ObjectMapper mapper =
            new ObjectMapper();

    public static void main(String[] args) {

        Javalin app = Javalin.create().start(7031);

        app.get("/health", ctx -> ctx.result("OK"));

        // Return all cleaned wards
        app.get("/wards", ctx -> {
            try {
                List<Map<String, Object>> wards = fetchWards();
                ctx.json(wards);
            } catch (Exception e) {
                ctx.status(503).json(Map.of(
                        "error", "Ingestion Service unavailable"
                ));
            }
        });

        // Return a specific ward
        app.get("/wards/{id}", ctx -> {
            try {
                String id = ctx.pathParam("id")
                        .trim().toUpperCase();

                List<Map<String, Object>> wards = fetchWards();

                Map<String, Object> ward = wards.stream()
                        .filter(w -> id.equals(w.get("wardId")))
                        .findFirst()
                        .orElse(null);

                if (ward == null) {
                    ctx.status(404).json(Map.of(
                            "error", "Ward not found",
                            "wardId", id
                    ));
                } else {
                    ctx.json(ward);
                }

            } catch (Exception e) {
                ctx.status(503).json(Map.of(
                        "error", "Ingestion Service unavailable"
                ));
            }
        });

        // Return unique department names
        app.get("/departments", ctx -> {
            try {
                List<Map<String, Object>> wards = fetchWards();

                List<String> departments = wards.stream()
                        .map(w -> (String) w.get("department"))
                        .filter(Objects::nonNull)
                        .distinct()
                        .sorted()
                        .toList();

                ctx.json(departments);

            } catch (Exception e) {
                ctx.status(503).json(Map.of(
                        "error", "Ingestion Service unavailable"
                ));
            }
        });
    }

    private static List<Map<String, Object>> fetchWards()
            throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(INGESTION_URL))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Ingestion Service returned status: "
                    + response.statusCode()
            );
        }

        return mapper.readValue(
                response.body(),
                new TypeReference<List<Map<String, Object>>>() {}
        );
    }
}
