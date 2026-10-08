package com.skillbridge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillBridgeServiceTest {
    @TempDir
    Path tempDir;

    private Database database;

    @BeforeEach
    void setUp() {
        database = new Database(tempDir.resolve("skillbridge.db").toString());
        database.initialize();
    }

    @Test
    void detectsCanonicalSkillsAliasesAndCommonTypos() {
        SkillBridgeService service = new SkillBridgeService(database);

        Set<String> detected = service.extractSkills("I know Javscript, html5, and Postgres.");

        assertEquals(Set.of("javascript", "html", "sql"), detected);
    }

    @Test
    void buildsOrderedRoadmapAndProgressSummary() {
        SkillBridgeService service = new SkillBridgeService(database);

        Map<String, Object> analysis = service.buildAnalysis("Web Developer", "HTML CSS", 7L);

        assertEquals("Web Developer", analysis.get("career"));
        assertEquals(20, analysis.get("score"));
        assertEquals(List.of("javascript", "git", "responsive design"), analysis.get("next_up"));
        assertEquals(8, analysis.get("task_count"));
        assertEquals(0, analysis.get("completed_count"));
        assertEquals(0, analysis.get("task_percent"));
    }
}
