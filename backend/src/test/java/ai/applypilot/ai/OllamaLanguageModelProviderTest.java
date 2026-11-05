package ai.applypilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaLanguageModelProviderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<String> content = new AtomicReference<>("{\"answer\":\"Grounded draft\"}");
    private final AtomicReference<String> requestPath = new AtomicReference<>("");
    private final AtomicReference<String> requestBody = new AtomicReference<>("");
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private final AtomicInteger delayMs = new AtomicInteger(0);
    private HttpServer server;
    private ExecutorService executor;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/api/chat", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (delayMs.get() > 0) {
                try { Thread.sleep(delayMs.get()); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
            byte[] body = responseStatus.get() == 200
                ? mapper.writeValueAsBytes(java.util.Map.of("message", java.util.Map.of("content", content.get())))
                : mapper.writeValueAsBytes(java.util.Map.of("error", "model not found"));
            exchange.sendResponseHeaders(responseStatus.get(), body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }

    @Test
    void sends_chat_request_to_configured_model_without_pull() {
        var result = provider("ollama", "qwen2.5:3b", 5000).generateJson("system", "user");
        assertThat(result.path("answer").asText()).isEqualTo("Grounded draft");
        assertThat(requestPath.get()).isEqualTo("/api/chat");
        assertThat(requestBody.get()).contains("qwen2.5:3b", "\"format\":\"json\"");
        assertThat(requestPath.get()).doesNotContain("pull");
    }

    @Test
    void remains_disabled_without_provider_and_model() {
        var disabled = provider("none", "", 1000);
        assertThat(disabled.isConfigured()).isFalse();
        assertThatThrownBy(() -> disabled.generateJson("system", "user"))
            .isInstanceOf(AiProviderException.class)
            .extracting(ex -> ((AiProviderException) ex).kind()).isEqualTo(AiProviderException.Kind.DISABLED);
        assertThat(requestPath.get()).isEmpty();
        assertThat(provider("ollama", "", 1000).isConfigured()).isFalse();
    }

    @Test
    void reports_missing_model_without_attempting_a_download() {
        responseStatus.set(404);
        assertThatThrownBy(() -> provider("ollama", "missing-model", 5000).generateJson("system", "user"))
            .isInstanceOf(AiProviderException.class)
            .extracting(ex -> ((AiProviderException) ex).kind()).isEqualTo(AiProviderException.Kind.UNAVAILABLE);
        assertThat(requestPath.get()).isEqualTo("/api/chat");
    }

    @Test
    void reports_malformed_model_json() {
        content.set("not a JSON object");
        assertThatThrownBy(() -> provider("ollama", "qwen2.5:3b", 5000).generateJson("system", "user"))
            .isInstanceOf(AiProviderException.class)
            .extracting(ex -> ((AiProviderException) ex).kind()).isEqualTo(AiProviderException.Kind.INVALID_RESPONSE);
    }

    @Test
    void reports_timeout_as_unavailable() {
        delayMs.set(1500);
        assertThatThrownBy(() -> provider("ollama", "qwen2.5:3b", 1000).generateJson("system", "user"))
            .isInstanceOf(AiProviderException.class)
            .extracting(ex -> ((AiProviderException) ex).kind()).isEqualTo(AiProviderException.Kind.UNAVAILABLE);
    }

    @Test
    void refuses_remote_hosts_to_prevent_cloud_calls() {
        var remote = new OllamaLanguageModelProvider("ollama", "https://ollama.com", "some-model", 5000, mapper);
        assertThatThrownBy(() -> remote.generateJson("system", "user"))
            .isInstanceOf(AiProviderException.class)
            .extracting(ex -> ((AiProviderException) ex).kind()).isEqualTo(AiProviderException.Kind.INVALID_RESPONSE);
        assertThat(requestPath.get()).isEmpty();
    }

    private OllamaLanguageModelProvider provider(String name, String model, long timeoutMs) {
        return new OllamaLanguageModelProvider(name, "http://127.0.0.1:" + server.getAddress().getPort(), model, timeoutMs, mapper);
    }
}
