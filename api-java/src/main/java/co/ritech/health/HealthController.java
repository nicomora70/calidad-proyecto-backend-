package co.ritech.health;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health")
public class HealthController {

    private final String version;

    public HealthController(@Value("${ritech.version:0.1.0}") String version) {
        this.version = version;
    }

    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "ritech-api", "version", version);
    }
}
