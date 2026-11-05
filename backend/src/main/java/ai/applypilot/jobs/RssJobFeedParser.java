package ai.applypilot.jobs;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Parses public RSS and Atom feeds without permitting external XML resources. */
final class RssJobFeedParser {
    private static final int MAX_FEED_CHARS = 2_000_000;
    private static final int MAX_ITEMS = 500;

    record FeedItem(String title, String link, String id, String description, Instant publishedAt) {}

    List<FeedItem> parse(String xml) throws Exception {
        if (xml == null || xml.isBlank() || xml.length() > MAX_FEED_CHARS) {
            throw new IllegalArgumentException("Feed is empty or exceeds the 2 MB limit.");
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        NodeList entries = document.getElementsByTagNameNS("*", "item");
        if (entries.getLength() == 0) entries = document.getElementsByTagNameNS("*", "entry");
        if (entries.getLength() > MAX_ITEMS) throw new IllegalArgumentException("Feed contains more than 500 entries.");

        List<FeedItem> result = new ArrayList<>(entries.getLength());
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            String title = childText(entry, "title");
            String link = childText(entry, "link");
            if (link.isBlank()) link = atomLink(entry);
            String id = firstNonBlank(childText(entry, "guid"), childText(entry, "id"), link);
            String description = firstNonBlank(childText(entry, "description"), childText(entry, "content"), childText(entry, "summary"));
            String date = firstNonBlank(childText(entry, "pubDate"), childText(entry, "updated"), childText(entry, "published"));
            result.add(new FeedItem(title, link, id, description, parseDate(date)));
        }
        return result;
    }

    private String childText(Element parent, String name) {
        NodeList nodes = parent.getElementsByTagNameNS("*", name);
        if (nodes.getLength() == 0) nodes = parent.getElementsByTagName(name);
        if (nodes.getLength() == 0) return "";
        Node node = nodes.item(0);
        return node.getTextContent() == null ? "" : node.getTextContent().trim();
    }

    private String atomLink(Element entry) {
        NodeList nodes = entry.getElementsByTagNameNS("*", "link");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element link = (Element) nodes.item(i);
            String rel = link.getAttribute("rel");
            if (rel.isBlank() || rel.equals("alternate")) return link.getAttribute("href").trim();
        }
        return "";
    }

    private Instant parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Instant.parse(value); } catch (DateTimeParseException ignored) { }
        try { return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(); }
        catch (DateTimeParseException ignored) { return null; }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return "";
    }
}
