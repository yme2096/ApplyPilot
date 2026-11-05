package ai.applypilot.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts only company facts explicitly published in public page metadata. */
public final class CompanyResearchParser {
    public record Fact(String label, String value, String sourceUrl, Instant retrievedAt) {}
    public record Research(String companyName, String status, String sourceUrl, Instant retrievedAt, List<Fact> facts) {}

    private static final Pattern JSON_LD = Pattern.compile("<script[^>]*type=[\\\"']application/ld\\+json[\\\"'][^>]*>(.*?)</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private final ObjectMapper mapper;

    CompanyResearchParser(ObjectMapper mapper) { this.mapper = mapper; }

    Research parse(String html, String sourceUrl, Instant retrievedAt) {
        List<Fact> facts = new ArrayList<>();
        String name = "";
        Matcher matcher = JSON_LD.matcher(html == null ? "" : html);
        while (matcher.find()) {
            try {
                JsonNode organization = findOrganization(mapper.readTree(matcher.group(1)));
                if (organization == null) continue;
                name = value(organization, "name");
                add(facts, "Company name", name, sourceUrl, retrievedAt);
                add(facts, "Description", value(organization, "description"), sourceUrl, retrievedAt);
                add(facts, "Website", value(organization, "url"), sourceUrl, retrievedAt);
                add(facts, "Founded", value(organization, "foundingDate"), sourceUrl, retrievedAt);
                add(facts, "Headquarters", address(organization.path("address")), sourceUrl, retrievedAt);
                JsonNode sameAs = organization.path("sameAs");
                if (sameAs.isArray()) sameAs.forEach(n -> add(facts, "Public profile", n.asText(""), sourceUrl, retrievedAt));
                else add(facts, "Public profile", sameAs.asText(""), sourceUrl, retrievedAt);
                break;
            } catch (Exception ignored) { /* Ignore malformed JSON-LD blocks; other public metadata may still be usable. */ }
        }
        if (facts.isEmpty()) {
            String siteName = meta(html, "og:site_name");
            String description = meta(html, "og:description");
            if (description == null) description = meta(html, "description");
            add(facts, "Company name", siteName, sourceUrl, retrievedAt);
            add(facts, "Description", description, sourceUrl, retrievedAt);
            name = siteName == null ? "" : siteName;
        }
        String status = facts.isEmpty() ? "UNAVAILABLE" : name.isBlank() ? "PARTIAL" : "VERIFIED_FROM_SOURCE";
        return new Research(name, status, sourceUrl, retrievedAt, List.copyOf(facts));
    }

    private JsonNode findOrganization(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isArray()) {
            for (JsonNode item : node) { JsonNode found = findOrganization(item); if (found != null) return found; }
            return null;
        }
        if (node.has("@graph")) { JsonNode found = findOrganization(node.path("@graph")); if (found != null) return found; }
        JsonNode type = node.path("@type");
        boolean organization = type.asText("").toLowerCase(Locale.ROOT).contains("organization");
        if (type.isArray()) for (JsonNode item : type) organization |= item.asText("").toLowerCase(Locale.ROOT).contains("organization");
        return organization ? node : null;
    }

    private String value(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (value.isTextual()) return value.asText().trim();
        if (value.isObject()) return value.path("name").asText("").trim();
        return "";
    }

    private String address(JsonNode address) {
        if (address.isTextual()) return address.asText().trim();
        List<String> parts = new ArrayList<>();
        for (String field : List.of("streetAddress", "addressLocality", "addressRegion", "postalCode", "addressCountry")) {
            String part = address.path(field).asText("").trim();
            if (!part.isBlank()) parts.add(part);
        }
        return String.join(", ", parts);
    }

    private void add(List<Fact> facts, String label, String value, String source, Instant retrieved) {
        if (value != null && !value.isBlank()) facts.add(new Fact(label, value.trim(), source, retrieved));
    }

    private String meta(String html, String key) {
        if (html == null) return null;
        Matcher matcher = Pattern.compile("<meta[^>]+(?:name|property)=[\\\"']" + Pattern.quote(key) + "[\\\"'][^>]+content=[\\\"'](.*?)[\\\"']", Pattern.CASE_INSENSITIVE).matcher(html);
        return matcher.find() ? matcher.group(1).trim() : null;
    }
}
