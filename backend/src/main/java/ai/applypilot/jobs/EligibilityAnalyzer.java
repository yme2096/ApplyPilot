package ai.applypilot.jobs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Interprets only explicit eligibility criteria that can be compared with verified profile fields. */
final class EligibilityAnalyzer {
    record Result(String status, List<String> reasons) {}
    private static final Pattern YEAR_WINDOW = Pattern.compile("(?:graduat\\w*|class of|degree completion)[^0-9]{0,35}(20\\d{2})\\D{0,20}(20\\d{2})", Pattern.CASE_INSENSITIVE);

    Result analyze(String title, String description, Integer graduationYear) {
        String text = ((title == null ? "" : title) + " " + (description == null ? "" : description)).toLowerCase(Locale.ROOT);
        List<String> reasons = new ArrayList<>();
        Matcher window = YEAR_WINDOW.matcher(text);
        if (window.find()) {
            int low = Math.min(Integer.parseInt(window.group(1)), Integer.parseInt(window.group(2)));
            int high = Math.max(Integer.parseInt(window.group(1)), Integer.parseInt(window.group(2)));
            if (graduationYear == null) {
                reasons.add("The role specifies graduation years " + low + "–" + high + "; add a verified graduation year to assess this criterion.");
                return new Result("UNKNOWN", List.copyOf(reasons));
            }
            if (graduationYear >= low && graduationYear <= high) {
                reasons.add("Verified graduation year " + graduationYear + " is within the stated range " + low + "–" + high + ".");
                return new Result("ELIGIBLE", List.copyOf(reasons));
            }
            reasons.add("Verified graduation year " + graduationYear + " is outside the stated range " + low + "–" + high + ".");
            return new Result("NOT_ELIGIBLE", List.copyOf(reasons));
        }

        boolean explicit = text.matches("(?s).*(currently enrolled|must be enrolled|enrolled in an? (accredited|undergraduate|graduate)|work authorization|authorized to work|citizenship|security clearance|graduat\\w* between|graduation year|\\d+ years? of (relevant )?experience|bachelor'?s degree required|master'?s degree required).*");
        if (explicit) {
            reasons.add("The posting includes eligibility conditions that this profile does not fully verify; confirm them with the employer.");
            return new Result("UNKNOWN", List.copyOf(reasons));
        }
        reasons.add("No explicit eligibility criteria were detected. Eligibility is unknown and requires candidate review.");
        return new Result("UNKNOWN", List.copyOf(reasons));
    }
}
