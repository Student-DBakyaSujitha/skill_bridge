package com.skillbridge;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static com.skillbridge.CareerCatalog.CAREERS;

@RestController
public class ApiController {
    private final Database database;
    private final SkillBridgeService service;
    private final SessionSupport sessions;

    public ApiController(Database database, SkillBridgeService service, SessionSupport sessions) {
        this.database = database;
        this.service = service;
        this.sessions = sessions;
    }

    @PostMapping("/api/analyze")
    public ResponseEntity<Map<String, Object>> analyze(
            @RequestBody(required = false) Map<String, Object> body, HttpSession session) {
        Optional<Map<String, Object>> user = sessions.currentUser(session);
        if (user.isEmpty()) {
            return error(HttpStatus.UNAUTHORIZED, "Your session ended. Please log in again.");
        }
        String career = stringValue(body, "career");
        Object skillValue = body == null ? null : body.get("skills");
        if (career == null || !CAREERS.containsKey(career)) {
            return error(HttpStatus.BAD_REQUEST, "Choose a career from the list.");
        }
        if (!(skillValue instanceof String rawSkills)) {
            return error(HttpStatus.BAD_REQUEST, "Enter your current skills as text.");
        }
        String skills = rawSkills.strip();
        if (skills.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "List at least one skill you already have.");
        }
        skills = truncateCodePoints(skills, 2000);
        long userId = ((Number) user.get().get("id")).longValue();
        database.update("UPDATE users SET skills=?, career_goal=? WHERE id=?", skills, career, userId);
        sessions.flash(session, "Your skill analysis and personalized roadmap are saved.");
        return ResponseEntity.ok(service.buildAnalysis(career, skills, userId));
    }

    @PostMapping("/api/roadmap/progress")
    public ResponseEntity<Map<String, Object>> updateRoadmapProgress(
            @RequestBody(required = false) Map<String, Object> body, HttpSession session) {
        Optional<Map<String, Object>> user = sessions.currentUser(session);
        if (user.isEmpty()) {
            return error(HttpStatus.UNAUTHORIZED, "Your session ended. Please log in again.");
        }
        String careerName = stringValue(body, "career");
        String skill = stringValue(body, "skill");
        CareerCatalog.Career career = careerName == null ? null : CAREERS.get(careerName);
        if (career == null || skill == null || !career.skills().containsKey(skill)) {
            return error(HttpStatus.BAD_REQUEST, "Choose a skill from your learning roadmap.");
        }
        Object completedValue = body.get("completed");
        if (!(completedValue instanceof Boolean completed)) {
            return error(HttpStatus.BAD_REQUEST, "Choose whether this learning task is complete.");
        }
        long userId = ((Number) user.get().get("id")).longValue();
        database.update("""
                INSERT INTO roadmap_progress (user_id, career, skill, completed, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(user_id, career, skill) DO UPDATE SET
                completed=excluded.completed, updated_at=CURRENT_TIMESTAMP
                """, userId, careerName, skill, completed ? 1 : 0);
        Map<String, Object> updated = service.buildAnalysis(
                careerName, (String) user.get().get("skills"), userId);
        return ResponseEntity.ok(Map.of(
                "completed_count", updated.get("completed_count"),
                "pending_count", updated.get("pending_count"),
                "task_percent", updated.get("task_percent")));
    }

    @PostMapping("/api/saved")
    public ResponseEntity<Map<String, Object>> toggleSavedItem(
            @RequestBody(required = false) Map<String, Object> body, HttpSession session) {
        Optional<Map<String, Object>> user = sessions.currentUser(session);
        if (user.isEmpty()) {
            return error(HttpStatus.UNAUTHORIZED, "Your session ended. Please log in again.");
        }
        Object itemTypeValue = body == null ? null : body.get("type");
        Object itemKeyValue = body == null ? null : body.get("key");
        Object saveValue = body == null ? null : body.get("saved");
        if (!(saveValue instanceof Boolean shouldSave)) {
            return error(HttpStatus.BAD_REQUEST, "Choose whether to save or remove this item.");
        }

        long userId = ((Number) user.get().get("id")).longValue();
        String careerGoal = (String) user.get().get("career_goal");
        CareerCatalog.Career currentCareer = CAREERS.get(careerGoal);
        String title = null;
        String details = null;
        if ("skill".equals(itemTypeValue) && itemKeyValue instanceof String key) {
            CareerCatalog.Skill match = currentCareer.skills().get(key);
            if (match == null && !shouldSave) {
                match = findSkill(key);
            }
            if (match != null) {
                title = key;
                details = match.tip();
            }
        } else if ("project".equals(itemTypeValue) && itemKeyValue instanceof String key) {
            CareerCatalog.Project match = findProject(currentCareer, key);
            if (match == null && !shouldSave) {
                match = findProjectInAllCareers(key);
            }
            if (match != null) {
                title = match.title();
                details = match.description();
            }
        }
        if (title == null) {
            return error(HttpStatus.BAD_REQUEST, "That skill or project cannot be saved.");
        }
        String itemType = itemTypeValue.toString();
        String itemKey = itemKeyValue.toString();
        if (shouldSave) {
            database.update("""
                    INSERT OR REPLACE INTO saved_items (user_id, item_type, item_key, title, details)
                    VALUES (?, ?, ?, ?, ?)
                    """, userId, itemType, itemKey, title, details);
        } else {
            database.update(
                    "DELETE FROM saved_items WHERE user_id=? AND item_type=? AND item_key=?",
                    userId, itemType, itemKey);
        }
        return ResponseEntity.ok(Map.of("saved", shouldSave));
    }

    @PostMapping("/api/settings")
    public ResponseEntity<Map<String, Object>> updateSettings(
            @RequestBody(required = false) Map<String, Object> body, HttpSession session) {
        Optional<Map<String, Object>> user = sessions.currentUser(session);
        if (user.isEmpty()) {
            return error(HttpStatus.UNAUTHORIZED, "Your session ended. Please log in again.");
        }
        String career = stringValue(body, "career");
        if (career == null || !CAREERS.containsKey(career)) {
            return error(HttpStatus.BAD_REQUEST, "Choose a career goal from the list.");
        }
        database.update("UPDATE users SET career_goal=? WHERE id=?",
                career, ((Number) user.get().get("id")).longValue());
        sessions.flash(session, "Learning preferences updated.");
        return ResponseEntity.ok(Map.of("message", "Career preference updated."));
    }

    private static CareerCatalog.Skill findSkill(String key) {
        for (CareerCatalog.Career career : CAREERS.values()) {
            CareerCatalog.Skill skill = career.skills().get(key);
            if (skill != null) {
                return skill;
            }
        }
        return null;
    }

    private static CareerCatalog.Project findProject(CareerCatalog.Career career, String key) {
        return career.projects().stream().filter(project -> project.title().equals(key)).findFirst().orElse(null);
    }

    private static CareerCatalog.Project findProjectInAllCareers(String key) {
        return CAREERS.values().stream()
                .flatMap(career -> career.projects().stream())
                .filter(project -> project.title().equals(key))
                .findFirst().orElse(null);
    }

    private static String stringValue(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value instanceof String string ? string : null;
    }

    private static String truncateCodePoints(String value, int limit) {
        int count = value.codePointCount(0, value.length());
        return count <= limit ? value : value.substring(0, value.offsetByCodePoints(0, limit));
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }
}
