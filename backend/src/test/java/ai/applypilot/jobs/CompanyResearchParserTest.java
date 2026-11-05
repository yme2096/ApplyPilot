package ai.applypilot.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyResearchParserTest {
    private final CompanyResearchParser parser = new CompanyResearchParser(new ObjectMapper());
    private final Instant retrieved = Instant.parse("2026-11-03T10:00:00Z");

    @Test
    void extractsOnlyPublishedOrganizationFactsAndAttributesEachToThePage() {
        String html = """
            <script type="application/ld+json">{"@graph":[{"@type":"WebPage","name":"About"},{"@type":"Organization","name":"Example Labs","description":"Builds developer tools.","url":"https://example.org","foundingDate":"2012","address":{"@type":"PostalAddress","addressLocality":"Pune","addressCountry":"IN"},"sameAs":["https://example.org/news"]}]}</script>
            """;
        var result = parser.parse(html, "https://example.org/about", retrieved);

        assertThat(result.companyName()).isEqualTo("Example Labs");
        assertThat(result.status()).isEqualTo("VERIFIED_FROM_SOURCE");
        assertThat(result.facts()).extracting(CompanyResearchParser.Fact::label)
            .contains("Company name", "Description", "Website", "Founded", "Headquarters", "Public profile");
        assertThat(result.facts()).allSatisfy(fact -> {
            assertThat(fact.sourceUrl()).isEqualTo("https://example.org/about");
            assertThat(fact.retrievedAt()).isEqualTo(retrieved);
        });
    }

    @Test
    void usesOnlyOpenGraphValuesWhenNoOrganizationJsonLdExists() {
        var result = parser.parse("<meta property='og:site_name' content='Example Co'><meta property='og:description' content='Published company profile'>",
            "https://example.org/about", retrieved);
        assertThat(result.companyName()).isEqualTo("Example Co");
        assertThat(result.facts()).hasSize(2);
        assertThat(result.facts().get(1).value()).isEqualTo("Published company profile");
        assertThat(result.facts().get(1).sourceUrl()).isEqualTo(result.sourceUrl());
    }

    @Test
    void returnsUnavailableInsteadOfGuessingWhenMetadataHasNoFacts() {
        var result = parser.parse("<html><title>About us</title></html>", "https://example.org/about", retrieved);
        assertThat(result.companyName()).isEmpty();
        assertThat(result.status()).isEqualTo("UNAVAILABLE");
        assertThat(result.facts()).isEmpty();
    }

    @Test
    void labelsDescriptionWithoutCompanyNameAsPartialMetadata() {
        var result = parser.parse("<meta property='og:description' content='Published page description'>",
            "https://example.org/about", retrieved);
        assertThat(result.status()).isEqualTo("PARTIAL");
        assertThat(result.companyName()).isEmpty();
        assertThat(result.facts()).hasSize(1);
    }
}
