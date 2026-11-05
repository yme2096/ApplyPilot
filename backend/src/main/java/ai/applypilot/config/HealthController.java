package ai.applypilot.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@RestController
public class HealthController {
    private static final ExecutorService HEALTH_CHECKS = Executors.newFixedThreadPool(3, task -> {
        Thread thread = new Thread(task, "applypilot-health-check");
        thread.setDaemon(true);
        return thread;
    });
    private final JdbcTemplate jdbc;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final URI workerHealthUri;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public HealthController(JdbcTemplate jdbc, ObjectProvider<StringRedisTemplate> redisProvider,
            @Value("${app.worker-url:http://localhost:8000}") String workerUrl) {
        this.jdbc = jdbc;
        this.redisProvider = redisProvider;
        this.workerHealthUri = URI.create(workerUrl.replaceAll("/$", "") + "/health");
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, String>> health() {
        var database = bounded(this::checkDatabase);
        var redis = bounded(this::checkRedis);
        var worker = bounded(this::checkWorker);
        CompletableFuture.allOf(database, redis, worker).join();
        boolean ready = database.getNow(false) && redis.getNow(false) && worker.getNow(false);
        return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("status", ready ? "UP" : "DOWN"));
    }
    private static CompletableFuture<Boolean> bounded(Supplier<Boolean> check) {
        return CompletableFuture.supplyAsync(check, HEALTH_CHECKS)
            .completeOnTimeout(false, 4, TimeUnit.SECONDS)
            .exceptionally(unavailable -> false);
    }
    private boolean checkDatabase() {
        try { return Integer.valueOf(1).equals(jdbc.queryForObject("SELECT 1", Integer.class)); }
        catch (RuntimeException unavailable) { return false; }
    }
    private boolean checkRedis() {
        try {
            var redis = redisProvider.getIfAvailable();
            if (redis == null) return false;
            try (var connection = redis.getConnectionFactory().getConnection()) {
                return "PONG".equalsIgnoreCase(connection.ping());
            }
        } catch (RuntimeException unavailable) { return false; }
    }
    private boolean checkWorker() {
        try {
            var request = HttpRequest.newBuilder(workerHealthUri).timeout(Duration.ofSeconds(3)).GET().build();
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception unavailable) { return false; }
    }
}
