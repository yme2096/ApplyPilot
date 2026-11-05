package ai.applypilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class OllamaLanguageModelProvider implements LanguageModelProvider {
    private final String provider;
    private final String baseUrl;
    private final String model;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final HttpClient client;

    public OllamaLanguageModelProvider(
        @Value("${app.ai.provider:none}") String provider,
        @Value("${app.ai.base-url:http://localhost:11434}") String baseUrl,
        @Value("${app.ai.model:qwen2.5:3b}") String model,
        @Value("${app.ai.timeout-ms:30000}") long timeoutMs,
        ObjectMapper mapper
    ) {
        this.provider = provider == null ? "none" : provider.trim().toLowerCase();
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:11434" : baseUrl.trim();
        this.model = model == null ? "" : model.trim();
        this.timeout = Duration.ofMillis(Math.max(1000, Math.min(timeoutMs, 120_000)));
        this.mapper = mapper;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public boolean isConfigured() {
        return provider.equals("ollama") && !model.isBlank();
    }

    @Override
    public JsonNode generateJson(String systemPrompt, String userPrompt) {
        if (!isConfigured()) {
            throw new AiProviderException(AiProviderException.Kind.DISABLED, "Local AI is disabled or no model is configured.");
        }
        try {
            URI endpoint = URI.create(baseUrl.endsWith("/") ? baseUrl + "api/chat" : baseUrl + "/api/chat");
            if (!List.of("http", "https").contains(endpoint.getScheme()) || endpoint.getHost() == null || endpoint.getUserInfo() != null) {
                throw new IllegalArgumentException("Invalid Ollama base URL configuration.");
            }
            String host = endpoint.getHost().toLowerCase();
            if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
            if (!List.of("localhost", "127.0.0.1", "::1", "host.docker.internal").contains(host)) {
                throw new IllegalArgumentException("Ollama must use a local endpoint; remote AI providers are not enabled.");
            }
            var payload = Map.of(
                "model", model,
                "stream", false,
                "format", "json",
                "messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
                ),
                "options", Map.of("temperature", 0.2)
            );
            String body = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<String> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (java.net.http.HttpTimeoutException ex) {
                throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE, "Local Ollama request timed out.", ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE, "Local Ollama request was interrupted.", ex);
            } catch (java.io.IOException ex) {
                throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE, "Local Ollama is unavailable.", ex);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE,
                    response.statusCode() == 404 ? "The configured Ollama model is not available." : "Local Ollama is unavailable.");
            }
            JsonNode responseJson = mapper.readTree(response.body());
            String content = responseJson.path("message").path("content").asText("").trim();
            if (content.isBlank()) throw new IllegalArgumentException("Ollama returned empty content.");
            if (content.startsWith("```") && content.endsWith("```")) {
                content = content.substring(content.indexOf('\n') + 1, content.length() - 3).trim();
            }
            JsonNode generated = mapper.readTree(content);
            if (generated == null || !generated.isObject()) throw new IllegalArgumentException("Ollama returned malformed JSON.");
            return generated;
        } catch (AiProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiProviderException(AiProviderException.Kind.INVALID_RESPONSE, "Local Ollama returned an unusable response.", ex);
        }
    }
}
