package ai.applypilot.certifications;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

final class CertificationRules {
    private CertificationRules() {}

    static LocalDate date(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException ex) { throw bad(field + " must use YYYY-MM-DD."); }
    }

    static void validateDates(LocalDate issued, LocalDate expires) {
        if (issued != null && issued.isAfter(LocalDate.now())) throw bad("Issue date cannot be in the future.");
        if (issued != null && expires != null && expires.isBefore(issued)) throw bad("Expiration date must be on or after the issue date.");
    }

    static String httpsUrl(String value, String field) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > 2048) throw bad(field + " must be at most 2048 characters.");
        try {
            URI uri = URI.create(value.trim()).normalize();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getFragment() != null)
                throw bad(field + " must be a valid HTTPS URL without embedded credentials or fragments.");
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal"))
                throw bad(field + " must use a public HTTPS host.");
            return uri.toString();
        } catch (IllegalArgumentException ex) {
            throw bad(field + " must be a valid HTTPS URL.");
        }
    }

    static List<Integer> reminderDays(List<Integer> days) {
        if (days == null) return List.of(90, 30, 7);
        if (days.size() > 5 || days.stream().anyMatch(d -> d == null || d < 1 || d > 365))
            throw bad("Reminder intervals must contain up to five values from 1 to 365 days.");
        if (new HashSet<>(days).size() != days.size()) throw bad("Reminder intervals must be unique.");
        return days.stream().sorted(Comparator.reverseOrder()).toList();
    }

    static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
