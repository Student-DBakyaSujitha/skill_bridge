package com.skillbridge;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class SessionSupport {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Database database;

    public SessionSupport(Database database) {
        this.database = database;
    }

    public String csrfToken(HttpSession session) {
        String token = (String) session.getAttribute("csrf");
        if (token == null || token.isEmpty()) {
            byte[] bytes = new byte[16];
            RANDOM.nextBytes(bytes);
            token = HexFormat.of().formatHex(bytes);
            session.setAttribute("csrf", token);
        }
        return token;
    }

    public Optional<Map<String, Object>> currentUser(HttpSession session) {
        Object userId = session.getAttribute("uid");
        if (!(userId instanceof Number id)) {
            return Optional.empty();
        }
        Optional<Map<String, Object>> user = database.findUser(id.longValue());
        if (user.isEmpty()) {
            session.removeAttribute("uid");
        }
        return user;
    }

    public void flash(HttpSession session, String message) {
        @SuppressWarnings("unchecked")
        List<String> flashes = (List<String>) session.getAttribute("flashes");
        if (flashes == null) {
            flashes = new ArrayList<>();
            session.setAttribute("flashes", flashes);
        }
        flashes.add(message);
    }

    public List<String> flashes(HttpSession session) {
        @SuppressWarnings("unchecked")
        List<String> flashes = (List<String>) session.getAttribute("flashes");
        return flashes == null ? List.of() : List.copyOf(flashes);
    }

    public void clearFlashes(HttpSession session) {
        session.removeAttribute("flashes");
    }

    public static Map<String, Object> userContext(Map<String, Object> user) {
        return user == null ? null : Map.copyOf(user);
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
