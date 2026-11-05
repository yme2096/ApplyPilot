package ai.applypilot.jobs;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RssJobFeedParserTest {
    private final RssJobFeedParser parser = new RssJobFeedParser();

    @Test
    void parsesRssItemsAndRfc1123Dates() throws Exception {
        var items = parser.parse("""
            <?xml version="1.0"?>
            <rss version="2.0"><channel><item>
              <title>Backend Engineer at Example Co</title>
              <link>https://jobs.example.org/123</link>
              <guid>role-123</guid>
              <description>Java &amp; PostgreSQL required</description>
              <pubDate>Tue, 03 Nov 2026 12:30:00 GMT</pubDate>
            </item></channel></rss>
            """);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).title()).isEqualTo("Backend Engineer at Example Co");
        assertThat(items.get(0).link()).isEqualTo("https://jobs.example.org/123");
        assertThat(items.get(0).id()).isEqualTo("role-123");
        assertThat(items.get(0).description()).contains("Java & PostgreSQL");
        assertThat(items.get(0).publishedAt()).isEqualTo(Instant.parse("2026-11-03T12:30:00Z"));
    }

    @Test
    void parsesNamespacedAtomLinkAndIsoDate() throws Exception {
        var items = parser.parse("""
            <feed xmlns="http://www.w3.org/2005/Atom"><entry>
              <title>Platform Engineer</title>
              <link rel="self" href="https://jobs.example.org/feed"/>
              <link rel="alternate" href="https://jobs.example.org/role"/>
              <id>urn:role:456</id><summary>Docker experience</summary>
              <updated>2026-11-03T10:00:00Z</updated>
            </entry></feed>
            """);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).link()).isEqualTo("https://jobs.example.org/role");
        assertThat(items.get(0).id()).isEqualTo("urn:role:456");
        assertThat(items.get(0).description()).isEqualTo("Docker experience");
        assertThat(items.get(0).publishedAt()).isEqualTo(Instant.parse("2026-11-03T10:00:00Z"));
    }

    @Test
    void rejectsDoctypeAndExternalEntityDocuments() {
        String xml = "<!DOCTYPE rss [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><rss><channel><item><title>&xxe;</title></item></channel></rss>";
        assertThatThrownBy(() -> parser.parse(xml)).isInstanceOf(Exception.class);
    }

    @Test
    void rejectsOversizedFeedsAndCapsEntryCount() {
        assertThatThrownBy(() -> parser.parse("x".repeat(2_000_001)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2 MB");
        String xml = "<rss><channel>" + "<item><title>Role</title></item>".repeat(501) + "</channel></rss>";
        assertThatThrownBy(() -> parser.parse(xml))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("500 entries");
    }
}
