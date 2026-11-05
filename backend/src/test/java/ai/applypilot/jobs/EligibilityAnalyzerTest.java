package ai.applypilot.jobs;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EligibilityAnalyzerTest {
    private final EligibilityAnalyzer analyzer = new EligibilityAnalyzer();

    @Test
    void marksMatchingExplicitGraduationWindowEligible() {
        var result = analyzer.analyze("Software Intern", "Must be graduating between 2026 and 2028.", 2027);
        assertThat(result.status()).isEqualTo("ELIGIBLE");
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("within the stated range"));
    }

    @Test
    void marksExplicitGraduationWindowMismatchNotEligible() {
        var result = analyzer.analyze("Graduate Engineer", "Graduation year 2025 to 2026 required.", 2024);
        assertThat(result.status()).isEqualTo("NOT_ELIGIBLE");
    }

    @Test
    void keepsEnrollmentAndAuthorizationUnknownWithoutVerifiedProfileFacts() {
        assertThat(analyzer.analyze("Engineering Intern", "Must be currently enrolled in a degree program.", 2027).status()).isEqualTo("UNKNOWN");
        assertThat(analyzer.analyze("Backend Engineer", "Must have work authorization in the United States.", null).status()).isEqualTo("UNKNOWN");
    }

    @Test
    void missingCriteriaNeverDefaultsToEligible() {
        var result = analyzer.analyze("Software Engineer", "Build backend services with Java.", null);
        assertThat(result.status()).isEqualTo("UNKNOWN");
        assertThat(result.reasons().getFirst()).contains("No explicit eligibility criteria");
    }
}
