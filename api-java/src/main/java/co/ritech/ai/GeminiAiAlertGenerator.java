package co.ritech.ai;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.ritech.common.ResilientCall;
import co.ritech.config.RitechProperties;

/**
 * Google Gemini (generateContent REST API). The key travels in the x-goog-api-key header,
 * never in the URL, so it does not end up in proxy logs. Transient failures (network, 5xx)
 * are retried with backoff; 4xx (invalid key, quota) fail at once.
 */
@Component
@ConditionalOnProperty(name = "ritech.ai.provider", havingValue = "gemini")
public class GeminiAiAlertGenerator implements AiAlertGenerator {

    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;
    private final ResilientCall resilience = ResilientCall.withDefaults("gemini");

    public GeminiAiAlertGenerator(RitechProperties properties, RestClient.Builder builder, ObjectMapper mapper) {
        var ai = properties.ai();
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(ai.timeout());
        factory.setReadTimeout(ai.timeout());
        this.restClient =
                builder.baseUrl(ai.geminiUrl()).requestFactory(factory).build();
        this.mapper = mapper;
        this.apiKey = ai.geminiApiKey();
        this.model = ai.geminiModel();
    }

    @Override
    public GeneratedAlert generate(AlertContext context) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiAlertException("Gemini API key is not configured");
        }
        var body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", AlertPrompt.render(context))))),
                "generationConfig", Map.of("responseMimeType", "application/json", "temperature", 0.4));
        JsonNode response;
        try {
            response = resilience.execute(
                    () -> restClient
                            .post()
                            .uri("/v1beta/models/{model}:generateContent", model)
                            .header("x-goog-api-key", apiKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(JsonNode.class),
                    ex -> new AiAlertException("Could not reach Gemini", ex));
        } catch (RestClientException ex) {
            throw new AiAlertException("Gemini rejected the request: " + ex.getMessage(), ex);
        }
        return parse(response, model, mapper);
    }

    static GeneratedAlert parse(JsonNode response, String model, ObjectMapper mapper) {
        if (response == null) {
            throw new AiAlertException("Empty Gemini response");
        }
        String text = response.path("candidates")
                .path(0)
                .path("content")
                .path("parts")
                .path(0)
                .path("text")
                .asText("")
                .strip();
        // Some models still wrap the JSON in a markdown fence
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(json)?", "").replaceFirst("```$", "").strip();
        }
        if (text.isEmpty()) {
            throw new AiAlertException("Gemini returned no text (blocked or empty candidate)");
        }
        JsonNode json;
        try {
            json = mapper.readTree(text);
        } catch (JsonProcessingException ex) {
            throw new AiAlertException("Gemini response is not valid JSON", ex);
        }
        String title = json.path("title").asText("").strip();
        String content = json.path("content").asText("").strip();
        if (title.isEmpty() || content.isEmpty()) {
            throw new AiAlertException("Gemini JSON lacks title or content");
        }
        JsonNode usage = response.path("usageMetadata");
        return new GeneratedAlert(
                title,
                content,
                model,
                AlertPrompt.VERSION,
                usage.path("promptTokenCount").asInt(0),
                usage.path("candidatesTokenCount").asInt(0));
    }
}
