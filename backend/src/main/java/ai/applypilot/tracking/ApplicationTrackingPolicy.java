package ai.applypilot.tracking;

import java.util.Set;

public final class ApplicationTrackingPolicy {
    public static final Set<String> STATUSES = Set.of("SAVED", "PREPARING", "READY_FOR_REVIEW", "APPLIED", "SHORTLISTED", "INTERVIEW", "REJECTED", "OFFER", "WITHDRAWN");
    private ApplicationTrackingPolicy() {}
    public static boolean validStatus(String status) { return STATUSES.contains(status); }
    public static boolean mayMarkApplied(String status, boolean candidateConfirmed) { return !"APPLIED".equals(status) || candidateConfirmed; }
}
