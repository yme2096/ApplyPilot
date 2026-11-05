package ai.applypilot.resume;

import java.util.*;
import java.util.stream.Collectors;

public final class TailoringEngine {
    private TailoringEngine() {}

    private static final Set<String> STOP_WORDS = Set.of(
        "the","a","an","and","or","but","in","on","at","to","for","of","with","by",
        "from","is","are","was","were","be","been","being","have","has","had","do",
        "does","did","will","would","could","should","may","might","must","shall",
        "this","that","these","those","we","you","your","our","their","its","it",
        "as","if","when","then","than","so","not","no","nor","yet","both","either",
        "neither","each","more","most","other","some","such","only","own","same",
        "few","into","through","during","before","after",
        "above","below","between","out","up","down","about","against","within"
    );

    public static List<String> extractKeywords(String jobDescription) {
        if (jobDescription == null || jobDescription.isBlank()) return List.of();
        var words = jobDescription.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9+#./ ]", " ")
            .split("\\s+");
        var freq = new LinkedHashMap<String, Integer>();
        for (var w : words) {
            if (w.length() >= 3 && !STOP_WORDS.contains(w)) {
                freq.merge(w, 1, Integer::sum);
            }
        }
        return freq.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(30)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    public static String tailor(String resumeText, String jobDescription, String targetRole) {
        var keywords = extractKeywords(jobDescription);
        var candidateText = resumeText.toLowerCase(Locale.ROOT);

        var matched = keywords.stream()
            .filter(k -> candidateText.contains(k))
            .collect(Collectors.toList());

        var sb = new StringBuilder();
        sb.append("--- TAILORED FOR: ").append(targetRole.toUpperCase(Locale.ROOT)).append(" ---\n");
        sb.append("[Tailored version — review all details before submitting.]\n\n");

        if (!matched.isEmpty()) {
            sb.append("RELEVANT SKILLS & KEYWORDS FOUND:\n");
            matched.forEach(k -> sb.append("• ").append(k).append("\n"));
            sb.append("\n");
        }

        // Reorder sections: push lines containing job keywords to the top
        var lines = Arrays.asList(resumeText.split("\n"));
        var topLines = new ArrayList<String>();
        var restLines = new ArrayList<String>();
        for (var line : lines) {
            var lower = line.toLowerCase(Locale.ROOT);
            boolean relevant = matched.stream().anyMatch(lower::contains);
            if (relevant && !line.isBlank()) topLines.add(line);
            else restLines.add(line);
        }
        if (!topLines.isEmpty()) {
            sb.append("HIGHLIGHTED EXPERIENCE:\n");
            topLines.forEach(l -> sb.append(l).append("\n"));
            sb.append("\nFULL RESUME:\n");
        }
        restLines.forEach(l -> sb.append(l).append("\n"));

        return sb.toString().trim();
    }
}
