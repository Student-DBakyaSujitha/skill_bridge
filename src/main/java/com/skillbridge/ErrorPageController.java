package com.skillbridge;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
public class ErrorPageController implements ErrorController {
    private final TemplateRenderer templates;

    public ErrorPageController(TemplateRenderer templates) {
        this.templates = templates;
    }

    @RequestMapping("/error")
    @ResponseBody
    public ResponseEntity<?> error(HttpServletRequest request) {
        Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int code = statusAttribute instanceof Number status ? status.intValue() : 500;
        HttpStatus status = HttpStatus.resolve(code);
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            code = status.value();
        }
        String message = switch (code) {
            case 400 -> "That request could not be verified. Refresh the page and try again.";
            case 404 -> "We couldn't find that page.";
            default -> status.getReasonPhrase();
        };
        if (request.getRequestURI().startsWith("/api/")) {
            return ResponseEntity.status(status).body(Map.of("error", message));
        }
        String page = templates.render("error.html", Map.of("code", code, "msg", message), "", java.util.List.of());
        return ResponseEntity.status(status)
                .contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
                .body(page);
    }
}
