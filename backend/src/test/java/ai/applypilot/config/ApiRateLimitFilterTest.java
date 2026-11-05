package ai.applypilot.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ApiRateLimitFilterTest {
    @Test void blocksOverLimitAuthenticationRequestsAndReturnsRetryAfter() throws Exception {
        var filter = new ApiRateLimitFilter((key, seconds) -> 2L, 1, 1, 60);
        var request = request("/api/auth/login");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        assertEquals("60", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("RATE_LIMITED"));
        assertNull(chain.getRequest());
    }

    @Test void ignoresForwardedAddressAndDoesNotStoreRawAddress() throws Exception {
        var key = new AtomicReference<String>();
        var filter = new ApiRateLimitFilter((value, seconds) -> { key.set(value); return 1L; }, 5, 5, 60);
        var request = request("/api/auth/login");
        request.addHeader("X-Forwarded-For", "203.0.113.99");
        request.setRemoteAddr("10.0.0.7");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNotNull(key.get());
        assertTrue(key.get().startsWith("applypilot:rate:v1:auth:"));
        assertFalse(key.get().contains("10.0.0.7"));
        assertFalse(key.get().contains("203.0.113.99"));
    }

    @Test void limitsOnlyConfiguredPublicAndExpensivePostRoutes() throws Exception {
        var filter = new ApiRateLimitFilter((key, seconds) -> 10_000L, 1, 1, 60);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request("/api/profile"), response, chain);
        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test void limitsEvidenceBasedAiDraftRoutesSeparately() throws Exception {
        var key = new AtomicReference<String>();
        var filter = new ApiRateLimitFilter((value, seconds) -> { key.set(value); return 1L; }, 5, 5, 10, 60);
        var response = new MockHttpServletResponse();
        filter.doFilter(request("/api/applications/55b9e5c9-78b8-4a17-943d-8c3d6f8fa987/interview-prep/draft"), response, new MockFilterChain());
        assertEquals(200, response.getStatus());
        assertNotNull(key.get());
        assertTrue(key.get().startsWith("applypilot:rate:v1:ai:"));

        key.set(null);
        filter.doFilter(request("/api/answer-library/draft"), new MockHttpServletResponse(), new MockFilterChain());
        assertNotNull(key.get());
        assertTrue(key.get().startsWith("applypilot:rate:v1:ai:"));
    }

    @Test void failsClosedIfRedisIsUnavailableWithoutLoggingRequestData() throws Exception {
        var filter = new ApiRateLimitFilter((key, seconds) -> { throw new IllegalStateException("redis unavailable"); }, 1, 1, 60);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request("/api/auth/login"), response, chain);
        assertEquals(503, response.getStatus());
        assertEquals("5", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("RATE_LIMITER_UNAVAILABLE"));
        assertNull(chain.getRequest());
    }

    private static MockHttpServletRequest request(String path) {
        var request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
