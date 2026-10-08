package com.skillbridge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateRendererTest {
    private final TemplateRenderer renderer = new TemplateRenderer();
    @TempDir
    Path tempDir;

    @Test
    void rendersExistingLandingAndAuthTemplatesWithoutChangingTheirSource() {
        String landing = renderer.render("landing.html", Map.of(
                "user", "",
                "score", 40,
                "chips", List.of(Map.of("name", "html", "have", true)),
                "cards", List.of(Map.of(
                        "name", "Web Developer", "count", 10, "projects", 3, "top", List.of("html")))),
                "csrf", List.of());
        Map<String, String> registrationValues = Map.of("name", "<script>alert(1)</script>");
        String auth = renderer.render("auth.html", Map.of(
                "mode", "register",
                "user", "",
                "v", registrationValues,
                "e", Map.of(),
                "careers", List.of("Web Developer"),
                "departments", List.of("Computer Science"),
                "years", List.of(1, 2)), "csrf-token", List.of());

        assertTrue(landing.contains("/static/style.css"));
        assertTrue(landing.contains("Web Developer"));
        assertTrue(auth.contains("name=\"csrf\" value=\"csrf-token\""));
        assertTrue(auth.contains("Create account"));
        assertTrue(auth.contains("<label for=\"email\">Email</label>"));
        assertTrue(auth.contains("value=\"&lt;script&gt;alert(1)&lt;/script&gt;\""));
        assertFalse(auth.contains("&lt;label for=\"email\"&gt;"));
        assertFalse(landing.contains("{{"));
        assertFalse(auth.contains("{%"));
    }

    @Test
    void rendersExistingDashboardAndIncludesWithDynamicRoutes() {
        Database database = new Database(tempDir.resolve("skillbridge.db").toString());
        database.initialize();
        SkillBridgeService service = new SkillBridgeService(database);
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", 1L);
        user.put("name", "Example Student");
        user.put("email", "student@example.com");
        user.put("career_goal", "Web Developer");
        user.put("skills", "HTML");
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("user", user);
        model.put("page", "dashboard");
        model.put("careers", List.of("Web Developer", "Data Analyst"));
        model.put("analysis", service.buildAnalysis("Web Developer", "HTML", 1L));
        model.put("saved_items", List.of());

        String result = renderer.render("index.html", model, "csrf-token", List.of());

        assertTrue(result.contains("Example Student"));
        assertTrue(result.contains("href=\"/roadmap\""));
        assertTrue(result.contains("href=\"/static/style.css\""));
        assertTrue(result.contains("src=\"/static/app.js\""));
        assertFalse(result.contains("{%"));
        assertFalse(result.contains("{{"));
    }

    @Test
    void rendersProfileFieldMacrosAsControls() {
        Map<String, Object> user = Map.of(
                "name", "Example Student",
                "email", "student@example.com");
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("user", user);
        model.put("v", Map.of(
                "name", "Example Student",
                "department", "Computer Science",
                "year", "2",
                "career_goal", "Web Developer"));
        model.put("e", Map.of());
        model.put("departments", List.of("Computer Science"));
        model.put("years", List.of(1, 2));
        model.put("careers", List.of("Web Developer"));
        model.put("current_page", "profile");

        String result = renderer.render("profile.html", model, "csrf-token", List.of());

        assertTrue(result.contains("<label for=\"name\">Full name</label>"));
        assertTrue(result.contains("<select id=\"department\" name=\"department\" required>"));
        assertFalse(result.contains("&lt;label for=\"name\"&gt;"));
        assertFalse(result.contains("{{"));
    }
}
