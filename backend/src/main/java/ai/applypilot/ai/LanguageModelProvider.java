package ai.applypilot.ai;

import com.fasterxml.jackson.databind.JsonNode;

/** Optional local language-model adapter. Product features must remain usable when no provider is configured. */
public interface LanguageModelProvider {
    boolean isConfigured();
    JsonNode generateJson(String systemPrompt, String userPrompt);
}
