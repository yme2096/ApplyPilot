package ai.applypilot.jobs;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerDraftGroundingValidatorTest {
    private final AnswerDraftGroundingValidator validator = new AnswerDraftGroundingValidator();

    @Test
    void acceptsAClaimWithStrongOverlapToSuppliedResumeFacts() {
        assertThat(validator.isSupported("I built a resilient parser using Java and Python.",
            Set.of("Built a parser for candidate documents using Java and Python."))).isTrue();
    }

    @Test
    void rejectsAnUnsupportedProjectOrExperienceClaim() {
        assertThat(validator.isSupported("I led the Aurora platform team using Java.",
            Set.of("Built a parser using Java."))).isFalse();
    }

    @Test
    void rejectsUnsupportedMetricsAndClaimsInAnySentence() {
        assertThat(validator.isSupported("I built a parser using Java. I improved reliability by 40 percent.",
            Set.of("Built a parser using Java."))).isFalse();
    }

    @Test
    void rejectsEmptyEvidenceOrUnverifiableAnswers() {
        assertThat(validator.isSupported("I am an expert in Kubernetes.", Set.of())).isFalse();
        assertThat(validator.isSupported("Led a global engineering organization.",
            Set.of("Built a parser using Java and Python."))).isFalse();
    }
}
