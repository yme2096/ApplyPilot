package ai.applypilot.jobs;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Conservative lexical guard; candidates must still review every generated draft. */
public final class AnswerDraftGroundingValidator {
    private static final Set<String> STOP = Set.of("i", "me", "my", "we", "our", "the", "a", "an", "and", "or", "but", "to", "of", "in", "on", "at", "for", "with", "by", "from", "as", "is", "are", "was", "were", "be", "been", "being", "have", "has", "had", "do", "did", "can", "could", "will", "would", "should", "this", "that", "these", "those", "which", "who", "what", "how", "because", "while", "during", "using", "used", "into", "also");
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}][\\p{L}0-9+#./-]{1,}");

    public boolean isSupported(String answer, Set<String> evidence) {
        if (answer == null || answer.isBlank() || evidence == null || evidence.isEmpty()) return false;
        Set<String> evidenceTokens = evidence.stream().flatMap(s -> tokens(s).stream()).collect(Collectors.toSet());
        for (String sentence : answer.split("(?<=[.!?])\\s+")) {
            Set<String> claim = tokens(sentence);
            if (claim.isEmpty()) continue;
            long overlap = claim.stream().filter(evidenceTokens::contains).count();
            if ((double) overlap / claim.size() < 0.6) return false;
        }
        return true;
    }

    private Set<String> tokens(String text) {
        return TOKEN.matcher(text.toLowerCase(Locale.ROOT)).results().map(m -> m.group())
            .filter(token -> token.length() > 2 && !STOP.contains(token))
            .collect(Collectors.toSet());
    }
}
