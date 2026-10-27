
package co.wethinkcode.healthsafe;

import io.javalin.Javalin;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class AlertLevelServiceApp {

    // Current emergency level. Starts at 0.
    private static final AtomicInteger alertLevel =
            new AtomicInteger(0);

    public static void main(String[] args) {

        Javalin app = Javalin.create().start(7032);

        // Check if the service is running
        app.get("/health", ctx -> ctx.result("OK"));

        // Retrieve the current emergency level
        app.get("/alert-level", ctx -> {
            ctx.json(Map.of(
                    "level", alertLevel.get()
            ));
        });

        // Update the current emergency level
        app.put("/alert-level", ctx -> {

            try {
                String body = ctx.body().trim();

                // Accept a JSON object like {"level": 5}
                java.util.regex.Matcher matcher =
                        java.util.regex.Pattern
                                .compile(
                                    "^\\{\\s*\"level\"\\s*:\\s*(-?\\d+)\\s*\\}$"
                                )
                                .matcher(body);

                if (!matcher.matches()) {
                    ctx.status(400).json(Map.of(
                            "error", "Expected JSON: {\"level\": 0-8}"
                    ));
                    return;
                }

                int newLevel = Integer.parseInt(matcher.group(1));

                if (newLevel < 0 || newLevel > 8) {
                    ctx.status(400).json(Map.of(
                            "error", "Emergency level must be between 0 and 8"
                    ));
                    return;
                }

                alertLevel.set(newLevel);

                ctx.json(Map.of(
                        "level", alertLevel.get(),
                        "message", "Emergency level updated"
                ));

            } catch (NumberFormatException e) {
                ctx.status(400).json(Map.of(
                        "error", "Invalid emergency level"
                ));
            }
        });
    }
}
