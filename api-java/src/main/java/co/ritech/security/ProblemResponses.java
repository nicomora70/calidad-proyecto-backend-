package co.ritech.security;

import java.io.IOException;
import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

/** Writes RFC 7807 bodies for errors raised by the security filter chain (outside MVC). */
final class ProblemResponses {

    private ProblemResponses() {
    }

    static AuthenticationEntryPoint entryPoint(ObjectMapper mapper) {
        return (request, response, ex) -> {
            boolean expired = ex.getMessage() != null && ex.getMessage().toLowerCase().contains("expired");
            write(mapper, response, HttpStatus.UNAUTHORIZED,
                    expired ? "TOKEN_EXPIRED" : "UNAUTHORIZED",
                    expired ? "Access token has expired" : "Authentication is required");
        };
    }

    static AccessDeniedHandler accessDenied(ObjectMapper mapper) {
        return (request, response, ex) -> write(mapper, response, HttpStatus.FORBIDDEN,
                "FORBIDDEN", "You do not have permission to perform this action");
    }

    private static void write(ObjectMapper mapper, HttpServletResponse response, HttpStatus status,
                              String code, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:ritech:error:" + code));
        problem.setProperty("code", code);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        if (status == HttpStatus.UNAUTHORIZED) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        mapper.writeValue(response.getOutputStream(), problem);
    }
}
