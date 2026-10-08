package com.skillbridge;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.skillbridge.CareerCatalog.CAREERS;

@Controller
public class PageController {
    private final Database database;
    private final SkillBridgeService service;
    private final SessionSupport sessions;
    private final TemplateRenderer templates;

    public PageController(Database database, SkillBridgeService service,
                          SessionSupport sessions, TemplateRenderer templates) {
        this.database = database;
        this.service = service;
        this.sessions = sessions;
        this.templates = templates;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> landing(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        SetView sample = makeSample();
        List<Map<String, Object>> cards = new ArrayList<>();
        CAREERS.forEach((name, career) -> cards.add(Map.of(
                "name", name,
                "count", career.skills().size(),
                "projects", career.projects().size(),
                "top", career.skills().keySet().stream().limit(4).toList())));
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("user", SessionSupport.userContext(sessions.currentUser(session).orElse(null)));
        model.put("chips", sample.chips());
        model.put("score", sample.score());
        model.put("cards", cards);
        return html(templates.render("landing.html", model, sessions.csrfToken(session), sessions.flashes(session)),
                session);
    }

    @GetMapping(value = {
            "/dashboard", "/skills", "/analysis", "/roadmap", "/projects",
            "/resources", "/progress", "/saved", "/settings"
    }, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> studentPage(HttpServletRequest request, HttpSession session) {
        Optional<Map<String, Object>> userOpt = sessions.currentUser(session);
        if (userOpt.isEmpty()) {
            return redirect("/login");
        }
        String page = pageFor(request.getRequestURI());
        Map<String, Object> user = userOpt.get();
        long userId = ((Number) user.get("id")).longValue();
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("user", user);
        model.put("page", page);
        model.put("careers", new ArrayList<>(CAREERS.keySet()));
        model.put("analysis", service.buildAnalysis(
                (String) user.get("career_goal"), (String) user.get("skills"), userId));
        model.put("saved_items", database.query(
                "SELECT item_type, item_key, title, details FROM saved_items WHERE user_id=? ORDER BY created_at DESC",
                userId));
        String rendered = templates.render(
                "index.html", model, sessions.csrfToken(session), sessions.flashes(session));
        sessions.clearFlashes(session);
        return html(rendered, session);
    }

    private SetView makeSample() {
        var have = service.extractSkills("HTML, CSS, JavaScript and Git");
        var skills = CAREERS.get("Web Developer").skills();
        List<Map<String, Object>> chips = skills.keySet().stream()
                .map(name -> Map.<String, Object>of("name", name, "have", have.contains(name)))
                .toList();
        long count = chips.stream().filter(chip -> Boolean.TRUE.equals(chip.get("have"))).count();
        return new SetView(chips, (int) Math.rint(100.0 * count / chips.size()));
    }

    private static String pageFor(String path) {
        return switch (path) {
            case "/dashboard" -> "dashboard";
            case "/skills" -> "skills";
            case "/analysis" -> "analysis";
            case "/roadmap" -> "roadmap";
            case "/projects" -> "projects";
            case "/resources" -> "resources";
            case "/progress" -> "progress";
            case "/saved" -> "saved";
            case "/settings" -> "settings";
            default -> throw new IllegalArgumentException("Unknown application page: " + path);
        };
    }

    private static ResponseEntity<String> html(String body, HttpSession session) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8));
        if (session.getAttribute("uid") != null) {
            headers.setCacheControl("no-store");
        }
        return ResponseEntity.ok().headers(headers).body(body);
    }

    private static ResponseEntity<String> redirect(String path) {
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, path).build();
    }

    private record SetView(List<Map<String, Object>> chips, int score) {}
}
