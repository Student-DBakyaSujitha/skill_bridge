package com.skillbridge;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static com.skillbridge.CareerCatalog.CAREERS;
import static com.skillbridge.CareerCatalog.DEPARTMENTS;
import static com.skillbridge.CareerCatalog.YEARS;

@Controller
public class AuthController {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final ConcurrentHashMap<String, FailedLogin> FAILED_LOGINS = new ConcurrentHashMap<>();
    private static final PasswordHasher PASSWORD_HASHER = new PasswordHasher();
    private static final String DUMMY_PASSWORD_HASH = PASSWORD_HASHER.hash("not-a-real-password");

    private final Database database;
    private final SessionSupport sessions;
    private final PasswordHasher passwordHasher;
    private final TemplateRenderer templates;

    public AuthController(Database database, SessionSupport sessions, PasswordHasher passwordHasher,
                          TemplateRenderer templates) {
        this.database = database;
        this.sessions = sessions;
        this.passwordHasher = passwordHasher;
        this.templates = templates;
    }

    @GetMapping(value = "/register", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> registerPage(HttpServletRequest request) {
        if (sessions.currentUser(request.getSession(true)).isPresent()) {
            return redirect("/dashboard");
        }
        return authPage("register", new LinkedHashMap<>(), new LinkedHashMap<>(),
                request.getSession());
    }

    @PostMapping(value = "/register", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> register(@RequestParam Map<String, String> form, HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        if (sessions.currentUser(session).isPresent()) {
            return redirect("/dashboard");
        }
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, String> errors = new LinkedHashMap<>();
        cleanProfile(form, values, errors);
        String email = SessionSupport.normalizeEmail(form.get("email"));
        values.put("email", email);
        String password = value(form, "password");
        String confirmation = value(form, "confirm");

        if (!EMAIL.matcher(email).matches() || email.length() > 120) {
            errors.put("email", "Enter a valid email address.");
        }
        int passwordLength = password.codePointCount(0, password.length());
        if (passwordLength < 8 || passwordLength > 128
                || !password.matches("(?s).*[A-Za-z].*")
                || !password.matches("(?s).*\\d.*")) {
            errors.put("password", "Use 8 or more characters with at least one letter and one number.");
        } else if (!password.equals(confirmation)) {
            errors.put("confirm", "The passwords do not match.");
        }

        if (errors.isEmpty()) {
            try {
                long userId = database.createUser(
                        values.get("name"), email, passwordHasher.hash(password),
                        values.get("department"), Integer.parseInt(values.get("year")),
                        values.get("career_goal"));
                establishLogin(request, userId);
                return redirect("/dashboard");
            } catch (Database.DuplicateEmailException exception) {
                errors.put("email", "An account with this email already exists. Try logging in.");
            }
        }
        return authPage("register", values, errors, session);
    }

    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> loginPage(HttpServletRequest request) {
        if (sessions.currentUser(request.getSession(true)).isPresent()) {
            return redirect("/dashboard");
        }
        return authPage("login", Map.of(), Map.of(), request.getSession());
    }

    @PostMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> login(@RequestParam Map<String, String> form, HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        if (sessions.currentUser(session).isPresent()) {
            return redirect("/dashboard");
        }
        String email = SessionSupport.normalizeEmail(form.get("email"));
        String password = value(form, "password");
        String key = email + "\u0000" + request.getRemoteAddr();
        long now = System.currentTimeMillis();
        FailedLogin failures = FAILED_LOGINS.get(key);
        if (failures == null || now - failures.windowStartedAt() > 600_000) {
            failures = new FailedLogin(0, now);
        }

        Map<String, String> errors = new LinkedHashMap<>();
        Optional<Map<String, Object>> credentials = database.findUserCredentials(email);
        String encoded = credentials.map(row -> (String) row.get("password_hash")).orElse(DUMMY_PASSWORD_HASH);
        boolean passwordMatches = passwordHasher.matches(password, encoded);
        if (failures.count() >= 5) {
            errors.put("form", "Too many failed attempts. Please wait a few minutes and try again.");
        } else if (credentials.isPresent() && passwordMatches) {
            FAILED_LOGINS.remove(key);
            long userId = ((Number) credentials.get().get("id")).longValue();
            establishLogin(request, userId);
            return redirect("/dashboard");
        } else {
            FAILED_LOGINS.put(key, new FailedLogin(failures.count() + 1, failures.windowStartedAt()));
            errors.put("form", "The email or password is incorrect.");
        }
        return authPage("login", Map.of("email", email), errors, session);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return redirectVoid("/login");
    }

    @RequestMapping(value = "/profile", method = {RequestMethod.GET, RequestMethod.POST},
            produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> profile(
            @RequestParam(required = false) Map<String, String> form,
            HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        Optional<Map<String, Object>> userOpt = sessions.currentUser(session);
        if (userOpt.isEmpty()) {
            return redirect("/login");
        }
        Map<String, Object> user = userOpt.get();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("name", (String) user.get("name"));
        values.put("email", (String) user.get("email"));
        values.put("department", (String) user.get("department"));
        values.put("year", String.valueOf(user.get("year")));
        values.put("career_goal", (String) user.get("career_goal"));
        Map<String, String> errors = new LinkedHashMap<>();

        if ("POST".equalsIgnoreCase(request.getMethod())) {
            cleanProfile(form == null ? Map.of() : form, values, errors);
            if (errors.isEmpty()) {
                database.update(
                        "UPDATE users SET name=?, department=?, year=?, career_goal=? WHERE id=?",
                        values.get("name"), values.get("department"), Integer.parseInt(values.get("year")),
                        values.get("career_goal"), user.get("id"));
                sessions.flash(session, "Profile updated.");
                return redirect("/profile");
            }
        }

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("user", user);
        model.put("v", values);
        model.put("e", errors);
        model.put("departments", DEPARTMENTS);
        model.put("years", YEARS);
        model.put("careers", List.copyOf(CAREERS.keySet()));
        model.put("current_page", "profile");
        return html(templates.render(
                "profile.html", model, sessions.csrfToken(session), sessions.flashes(session)), session);
    }

    private ResponseEntity<String> authPage(String mode, Map<String, String> values,
                                            Map<String, String> errors, HttpSession session) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("mode", mode);
        model.put("v", values);
        model.put("e", errors);
        model.put("departments", DEPARTMENTS);
        model.put("years", YEARS);
        model.put("careers", List.copyOf(CAREERS.keySet()));
        model.put("user", null);
        return html(templates.render("auth.html", model, sessions.csrfToken(session), sessions.flashes(session)),
                session);
    }

    private static void cleanProfile(Map<String, String> form, Map<String, String> values,
                                     Map<String, String> errors) {
        String name = String.join(" ", value(form, "name").trim().split("\\s+"));
        String department = value(form, "department");
        String year = value(form, "year");
        String careerGoal = value(form, "career_goal");
        values.put("name", name);
        values.put("department", department);
        values.put("year", year);
        values.put("career_goal", careerGoal);

        int nameLength = name.codePointCount(0, name.length());
        if (nameLength < 2 || nameLength > 80) {
            errors.put("name", "Enter your full name (2 to 80 characters).");
        }
        if (!DEPARTMENTS.contains(department)) {
            errors.put("department", "Choose your department.");
        }
        if (!YEARS.stream().map(String::valueOf).toList().contains(year)) {
            errors.put("year", "Choose your year of study.");
        }
        if (!CAREERS.containsKey(careerGoal)) {
            errors.put("career_goal", "Choose a career goal.");
        }
    }

    private void establishLogin(HttpServletRequest request, long userId) {
        request.changeSessionId();
        HttpSession session = request.getSession(false);
        session.removeAttribute("csrf");
        session.removeAttribute("flashes");
        session.setAttribute("uid", userId);
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

    private static ResponseEntity<Void> redirectVoid(String path) {
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, path).build();
    }

    private static String value(Map<String, String> values, String key) {
        return Optional.ofNullable(values.get(key)).orElse("");
    }

    private record FailedLogin(int count, long windowStartedAt) {}
}
