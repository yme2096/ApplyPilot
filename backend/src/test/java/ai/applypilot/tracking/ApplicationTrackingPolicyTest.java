package ai.applypilot.tracking;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ApplicationTrackingPolicyTest {
    @Test void acceptsOnlyTheDocumentedPipelineStatuses() {
        assertThat(ApplicationTrackingPolicy.STATUSES).containsExactlyInAnyOrder(
            "SAVED","PREPARING","READY_FOR_REVIEW","APPLIED","SHORTLISTED","INTERVIEW","REJECTED","OFFER","WITHDRAWN");
        assertThat(ApplicationTrackingPolicy.validStatus("INVENTED")).isFalse();
    }
    @Test void appliedRequiresExplicitCandidateConfirmation() {
        assertThat(ApplicationTrackingPolicy.mayMarkApplied("SAVED",false)).isTrue();
        assertThat(ApplicationTrackingPolicy.mayMarkApplied("APPLIED",false)).isFalse();
        assertThat(ApplicationTrackingPolicy.mayMarkApplied("APPLIED",true)).isTrue();
    }
}
