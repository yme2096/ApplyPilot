package ai.applypilot.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** Redis fixed-window limits for public authentication and expensive ingestion routes. */
@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {
    private static final DefaultRedisScript<Long> INCREMENT_WINDOW = new DefaultRedisScript<>(
        "local count=redis.call('INCR',KEYS[1]); if count==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]); end; return count", Long.class);
    private final RateLimitStore store;
    private final int authLimit;
    private final int importLimit;
    private final int aiLimit;
    private final int windowSeconds;
    private final boolean enabled;

    @Autowired
    public ApiRateLimitFilter(ObjectProvider<RateLimitStore> storeProvider,
            @Value("${app.rate-limit.auth-max:20}") int authLimit,
            @Value("${app.rate-limit.import-max:30}") int importLimit,
            @Value("${app.rate-limit.ai-max:10}") int aiLimit,
            @Value("${app.rate-limit.window-seconds:60}") int windowSeconds,
            @Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this.store = storeProvider.getIfAvailable();
        this.authLimit = Math.max(1, authLimit);
        this.importLimit = Math.max(1, importLimit);
        this.aiLimit = Math.max(1, aiLimit);
        this.windowSeconds = Math.max(1, windowSeconds);
        this.enabled = enabled;
    }

    ApiRateLimitFilter(RateLimitStore store, int authLimit, int importLimit, int windowSeconds) {
        this(store, authLimit, importLimit, 10, windowSeconds);
    }

    ApiRateLimitFilter(RateLimitStore store, int authLimit, int importLimit, int aiLimit, int windowSeconds) {
        this.store = store;
        this.authLimit = Math.max(1, authLimit);
        this.importLimit = Math.max(1, importLimit);
        this.aiLimit = Math.max(1, aiLimit);
        this.windowSeconds = Math.max(1, windowSeconds);
        this.enabled = true;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled) { chain.doFilter(request, response); return; }
        Limit limit = limitFor(request);
        if (limit == null) { chain.doFilter(request, response); return; }
        if (store == null) { unavailable(response); return; }
        try {
            // remoteAddr is supplied by the socket/container. Forwarding headers are deliberately ignored.
            String key = "applypilot:rate:v1:" + limit.category + ":" + digest(request.getRemoteAddr());
            Long count = store.increment(key, windowSeconds);
            if (count == null) { unavailable(response); return; }
            if (count > limit.maximum) {
                response.setStatus(429);
                response.setHeader("Retry-After", Integer.toString(windowSeconds));
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests. Please try again shortly.\"}");
                return;
            }
        } catch (RuntimeException unavailable) {
            unavailable(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private static void unavailable(HttpServletResponse response) throws IOException {
        response.setStatus(503);
        response.setHeader("Retry-After", "5");
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"RATE_LIMITER_UNAVAILABLE\",\"message\":\"This request is temporarily unavailable. Please retry shortly.\"}");
    }

    private Limit limitFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!HttpMethod.POST.matches(request.getMethod())) return null;
        if (path.equals("/api/auth/login") || path.equals("/api/auth/register") || path.equals("/api/auth/password-reset/request"))
            return new Limit("auth", authLimit);
        if (path.equals("/api/jobs/import/url") || path.equals("/api/jobs/import/rss") || path.equals("/api/jobs/import/greenhouse")
                || path.equals("/api/jobs/import/lever") || path.equals("/api/jobs/analyze") || path.equals("/api/jobs/company-research"))
            return new Limit("ingest", importLimit);
        if (path.equals("/api/answer-library/draft") || path.matches("/api/applications/[^/]+/interview-prep/draft"))
            return new Limit("ai", aiLimit);
        return null;
    }

    private static String digest(String address) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(address.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception impossible) { throw new IllegalStateException("SHA-256 is unavailable"); }
    }
    private record Limit(String category, int maximum) {}

    public interface RateLimitStore { Long increment(String key, int windowSeconds); }

    @Component
    static class RedisRateLimitStore implements RateLimitStore {
        private final ObjectProvider<StringRedisTemplate> redisProvider;
        RedisRateLimitStore(ObjectProvider<StringRedisTemplate> redisProvider) { this.redisProvider = redisProvider; }
        @Override public Long increment(String key, int windowSeconds) {
            StringRedisTemplate redis = redisProvider.getIfAvailable();
            if (redis == null) throw new IllegalStateException("Redis rate limit store is unavailable");
            return redis.execute(INCREMENT_WINDOW, List.of(key), Integer.toString(windowSeconds));
        }
    }
}
