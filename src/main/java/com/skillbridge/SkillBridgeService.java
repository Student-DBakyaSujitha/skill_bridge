package com.skillbridge;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.skillbridge.CareerCatalog.*;

@Service
public class SkillBridgeService {
    private static final Pattern TOKENS = Pattern.compile("[a-z0-9.+#-]{4,}");
    private static final Map<String, String> VOCAB;
    private static final List<String> SINGLE_TERMS;
    private static final Map<Integer, String> HOURS = Map.of(
            1, "4–6 hours", 2, "6–10 hours", 3, "10–15 hours");
    private final Database database;

    static {
        LinkedHashMap<String, String> vocabulary = new LinkedHashMap<>();
        ALL_SKILLS.forEach(skill -> vocabulary.put(skill, skill));
        ALIASES.forEach(vocabulary::put);
        VOCAB = Collections.unmodifiableMap(vocabulary);
        SINGLE_TERMS = vocabulary.keySet().stream()
                .filter(term -> term.matches("[a-z0-9.+#-]+"))
                .toList();
    }

    public SkillBridgeService(Database database) {
        this.database = database;
    }

    public Set<String> extractSkills(String text) {
        String normalized = text == null ? "" : text.toLowerCase();
        LinkedHashSet<String> found = new LinkedHashSet<>();

        VOCAB.forEach((term, canonical) -> {
            String expression = "(?<![\\w+#.])" + Pattern.quote(term) + "(?![\\w+#])";
            if (Pattern.compile(expression).matcher(normalized).find()) {
                found.add(canonical);
            }
        });

        Matcher tokenMatcher = TOKENS.matcher(normalized);
        Set<String> tokens = new LinkedHashSet<>();
        while (tokenMatcher.find()) {
            tokens.add(tokenMatcher.group());
        }
        for (String token : tokens) {
            if (VOCAB.containsKey(token)) {
                continue;
            }
            String closest = null;
            double bestRatio = 0.85;
            for (String candidate : SINGLE_TERMS) {
                double ratio = matchingRatio(token, candidate);
                if (ratio > bestRatio) {
                    bestRatio = ratio;
                    closest = candidate;
                }
            }
            if (closest != null) {
                found.add(VOCAB.get(closest));
            }
        }
        return found;
    }

    public Map<String, Object> buildAnalysis(String careerName, String text, long userId) {
        Career career = CAREERS.get(careerName);
        if (career == null) {
            careerName = CAREERS.keySet().iterator().next();
            career = CAREERS.get(careerName);
        }

        Set<String> found = extractSkills(text);
        List<Map<String, Object>> progressRows = database.query(
                "SELECT skill, completed FROM roadmap_progress WHERE user_id=? AND career=?",
                userId, careerName);
        Set<String> completed = new LinkedHashSet<>();
        for (Map<String, Object> row : progressRows) {
            if (number(row.get("completed")) != 0) {
                completed.add((String) row.get("skill"));
            }
        }

        List<Map<String, Object>> skills = new ArrayList<>();
        for (Map.Entry<String, Skill> entry : career.skills().entrySet()) {
            skills.add(map(
                    "name", entry.getKey(),
                    "have", found.contains(entry.getKey()),
                    "level", entry.getValue().level(),
                    "tip", entry.getValue().tip()));
        }
        List<Map<String, Object>> missing = skills.stream()
                .filter(item -> !Boolean.TRUE.equals(item.get("have")))
                .toList();

        List<Map<String, Object>> roadmap = new ArrayList<>();
        for (int level = 1; level <= 3; level++) {
            List<Map<String, Object>> steps = new ArrayList<>();
            for (Map<String, Object> item : missing) {
                if (number(item.get("level")) != level) {
                    continue;
                }
                String skill = (String) item.get("name");
                List<Link> links = RESOURCE_LINKS.getOrDefault(
                        skill, List.of(new Link("freeCodeCamp", "https://www.freecodecamp.org/learn/")));
                List<String> relatedProjects = career.projects().stream()
                        .filter(project -> project.skills().contains(skill))
                        .map(Project::title)
                        .toList();
                List<Map<String, String>> linkMaps = links.stream()
                        .map(link -> Map.of("label", link.label(), "url", link.url()))
                        .toList();
                Map<String, Object> step = new LinkedHashMap<>(item);
                step.put("completed", completed.contains(skill));
                step.put("hours", HOURS.get(level));
                step.put("objective", "Understand " + skill + " fundamentals and apply them to a practical outcome.");
                step.put("practice", item.get("tip"));
                step.put("resources", linkMaps);
                step.put("mini_project", relatedProjects.isEmpty()
                        ? "Create a small " + skill + " portfolio exercise"
                        : relatedProjects.get(0));
                steps.add(step);
            }
            if (!steps.isEmpty()) {
                roadmap.add(map(
                        "phase", PHASES.get(level),
                        "level_name", LEVEL_NAMES.get(level),
                        "level", level,
                        "steps", steps));
            }
        }

        List<Map<String, Object>> projects = new ArrayList<>();
        for (Project project : career.projects()) {
            List<String> gaps = project.skills().stream()
                    .filter(skill -> missing.stream().anyMatch(item -> skill.equals(item.get("name"))))
                    .toList();
            if (!gaps.isEmpty()) {
                projects.add(map(
                        "title", project.title(),
                        "desc", project.description(),
                        "demonstrates", gaps,
                        "saved", savedExists(userId, "project", project.title())));
            }
        }
        projects.sort(Comparator.comparingInt(
                (Map<String, Object> item) -> ((List<?>) item.get("demonstrates")).size()).reversed());

        List<Map<String, Object>> allTasks = new ArrayList<>();
        for (Map<String, Object> phase : roadmap) {
            Object phaseSteps = phase.get("steps");
            if (phaseSteps instanceof List<?> steps) {
                for (Object step : steps) {
                    if (step instanceof Map<?, ?> stepMap) {
                        Map<String, Object> task = new LinkedHashMap<>();
                        stepMap.forEach((key, value) -> task.put((String) key, value));
                        allTasks.add(task);
                    }
                }
            }
        }
        int doneCount = (int) allTasks.stream()
                .filter(task -> Boolean.TRUE.equals(task.get("completed")))
                .count();
        int taskPercent = allTasks.isEmpty() ? 100 : roundPercent(doneCount, allTasks.size());
        List<Map<String, Object>> resources = skills.stream()
                .map(item -> map(
                        "skill", item.get("name"),
                        "links", RESOURCE_LINKS.getOrDefault(
                                (String) item.get("name"),
                                List.of(new Link("freeCodeCamp", "https://www.freecodecamp.org/learn/"))
                        ).stream().map(link -> List.of(link.label(), link.url())).toList()))
                .toList();

        return map(
                "career", careerName,
                "score", roundPercent(skills.stream().filter(item -> Boolean.TRUE.equals(item.get("have"))).count(),
                        skills.size()),
                "skills", skills,
                "next_up", missing.stream().limit(3).map(item -> item.get("name")).toList(),
                "roadmap", roadmap,
                "projects", projects,
                "detected", found.stream().filter(ALL_SKILLS::contains).sorted().toList(),
                "task_count", allTasks.size(),
                "completed_count", doneCount,
                "pending_count", allTasks.size() - doneCount,
                "task_percent", taskPercent,
                "resources", resources);
    }

    public boolean savedExists(long userId, String itemType, String itemKey) {
        return database.queryOne(
                "SELECT 1 FROM saved_items WHERE user_id=? AND item_type=? AND item_key=?",
                userId, itemType, itemKey).isPresent();
    }

    private static double matchingRatio(String left, String right) {
        int[][] lcs = new int[left.length() + 1][right.length() + 1];
        for (int i = 1; i <= left.length(); i++) {
            for (int j = 1; j <= right.length(); j++) {
                lcs[i][j] = left.charAt(i - 1) == right.charAt(j - 1)
                        ? lcs[i - 1][j - 1] + 1
                        : Math.max(lcs[i - 1][j], lcs[i][j - 1]);
            }
        }
        return 2.0 * lcs[left.length()][right.length()] / (left.length() + right.length());
    }

    private static int roundPercent(long numerator, long denominator) {
        return (int) Math.rint(100.0 * numerator / denominator);
    }

    private static int number(Object value) {
        return value instanceof Number numeric ? numeric.intValue() : Integer.parseInt(value.toString());
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            result.put((String) pairs[index], pairs[index + 1]);
        }
        return result;
    }
}
