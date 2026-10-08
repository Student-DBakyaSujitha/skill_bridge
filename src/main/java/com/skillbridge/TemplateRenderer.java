package com.skillbridge;

import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.lib.fn.ELFunctionDefinition;
import com.hubspot.jinjava.loader.ClasspathResourceLocator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TemplateRenderer {
    private static final ThreadLocal<RenderState> STATE = new ThreadLocal<>();
    private static final Pattern FIELD_MACRO_CALL = Pattern.compile(
            "\\{\\{\\s*((?:inp|sel)\\([^{}]*?\\))\\s*}}", Pattern.DOTALL);
    private final Jinjava jinjava;

    public TemplateRenderer() {
        this.jinjava = new Jinjava();
        this.jinjava.setResourceLocator(new ClasspathResourceLocator());
        this.jinjava.getGlobalContext().setAutoEscape(true);
        this.jinjava.getGlobalContext().registerFunction(new ELFunctionDefinition(
                "", "url_for", TemplateFunctions.class, "urlFor", Object[].class));
        this.jinjava.getGlobalContext().registerFunction(new ELFunctionDefinition(
                "", "csrf_token", TemplateFunctions.class, "csrfToken", Object[].class));
        this.jinjava.getGlobalContext().registerFunction(new ELFunctionDefinition(
                "", "get_flashed_messages", TemplateFunctions.class, "flashedMessages", Object[].class));
    }

    public String render(String templateName, Map<String, ?> context, String csrfToken, List<String> flashes) {
        STATE.set(new RenderState(csrfToken, flashes));
        try {
            String source = loadTemplate(templateName);
            if ("auth.html".equals(templateName) || "profile.html".equals(templateName)) {
                source = markFieldMacroOutputSafe(source);
            }
            String rendered = jinjava.render(source, context);
            if (rendered.contains("{{") || rendered.contains("{%")) {
                throw new IllegalStateException("Template was not fully rendered: " + templateName);
            }
            return rendered;
        } finally {
            STATE.remove();
        }
    }

    private static String markFieldMacroOutputSafe(String source) {
        Matcher matcher = FIELD_MACRO_CALL.matcher(source);
        return matcher.replaceAll("{{ $1|safe }}");
    }

    private static String loadTemplate(String templateName) {
        try (var stream = TemplateRenderer.class.getClassLoader().getResourceAsStream(templateName)) {
            if (stream == null) {
                throw new IllegalArgumentException("Template does not exist: " + templateName);
            }
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not read template: " + templateName, exception);
        }
    }

    public static final class TemplateFunctions {
        private static final Map<String, String> ROUTES = Map.ofEntries(
                Map.entry("home", "/"),
                Map.entry("login", "/login"),
                Map.entry("register", "/register"),
                Map.entry("dashboard", "/dashboard"),
                Map.entry("profile", "/profile"),
                Map.entry("my_skills", "/skills"),
                Map.entry("skill_analysis", "/analysis"),
                Map.entry("learning_roadmap", "/roadmap"),
                Map.entry("project_recommendations", "/projects"),
                Map.entry("learning_resources", "/resources"),
                Map.entry("progress_tracker", "/progress"),
                Map.entry("saved_items_page", "/saved"),
                Map.entry("settings", "/settings"),
                Map.entry("logout", "/logout"));

        private TemplateFunctions() {}

        public static String urlFor(Object... args) {
            if (args.length == 0 || args[0] == null) {
                throw new IllegalArgumentException("A route name is required.");
            }
            String endpoint = args[0].toString();
            if ("static".equals(endpoint)) {
                String filename = null;
                for (int index = 1; index < args.length; index++) {
                    if (args[index] instanceof Map<?, ?> values && values.get("filename") != null) {
                        filename = values.get("filename").toString();
                    } else if (args[index] != null) {
                        filename = args[index].toString();
                    }
                }
                if (filename == null || filename.contains("..") || filename.startsWith("/")) {
                    throw new IllegalArgumentException("A valid static filename is required.");
                }
                return "/static/" + filename;
            }
            String path = ROUTES.get(endpoint);
            if (path == null) {
                throw new IllegalArgumentException("Unknown application route: " + endpoint);
            }
            return path;
        }

        public static String csrfToken(Object... ignored) {
            RenderState state = requireState();
            return state.csrfToken();
        }

        public static List<String> flashedMessages(Object... ignored) {
            return requireState().flashes();
        }

        private static RenderState requireState() {
            RenderState state = STATE.get();
            if (state == null) {
                throw new IllegalStateException("Template helper called outside a render request.");
            }
            return state;
        }
    }

    private record RenderState(String csrfToken, List<String> flashes) {}
}
