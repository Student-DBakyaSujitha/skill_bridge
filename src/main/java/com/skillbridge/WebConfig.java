package com.skillbridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final ObjectMapper objectMapper;
    private final TemplateRenderer templates;

    public WebConfig(ObjectMapper objectMapper, TemplateRenderer templates) {
        this.objectMapper = objectMapper;
        this.templates = templates;
    }

    @Bean
    ServletContextInitializer sessionCookieSettings(Environment environment) {
        return (ServletContext context) -> {
            var cookie = context.getSessionCookieConfig();
            cookie.setHttpOnly(true);
            cookie.setSecure(isProduction(environment.getProperty("PRODUCTION")));
        };
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new CsrfInterceptor(objectMapper, templates));
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**").addResourceLocations("classpath:/static/");
    }

    @Bean
    RequestSecurityHeadersFilter requestSecurityHeadersFilter() {
        return new RequestSecurityHeadersFilter();
    }

    private static boolean isProduction(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private record CsrfInterceptor(ObjectMapper objectMapper, TemplateRenderer templates)
            implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                throws Exception {
            if (!"POST".equalsIgnoreCase(request.getMethod())) {
                return true;
            }
            var session = request.getSession(false);
            String expected = session == null ? "" : Optional.ofNullable(
                    (String) session.getAttribute("csrf")).orElse("");
            String provided = Optional.ofNullable(request.getHeader("X-CSRF-Token"))
                    .orElseGet(() -> Optional.ofNullable(request.getParameter("csrf")).orElse(""));
            boolean valid = !expected.isEmpty() && !provided.isEmpty()
                    && MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
            if (valid) {
                return true;
            }
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            if (request.getRequestURI().startsWith("/api/")) {
                response.setContentType("application/json");
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                objectMapper.writeValue(response.getWriter(), Map.of(
                        "error", "That request could not be verified. Refresh the page and try again."));
            } else {
                response.setContentType("text/html");
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write(templates.render(
                        "error.html",
                        Map.of("code", 400, "msg", "That request could not be verified. Refresh the page and try again."),
                        expected, java.util.List.of()));
            }
            return false;
        }
    }
}
