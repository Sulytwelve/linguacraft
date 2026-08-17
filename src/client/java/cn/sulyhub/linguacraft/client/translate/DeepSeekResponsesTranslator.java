package cn.sulyhub.linguacraft.client.translate;

import cn.sulyhub.linguacraft.client.config.LinguaCraftConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DeepSeekResponsesTranslator {
    private static final Logger LOGGER = LoggerFactory.getLogger("LinguaCraft/DeepSeek");
    private static final Gson GSON = new Gson();

    // Constant English System Prompt Harness to maximize model performance and DeepSeek Server-Side Context Hard Disk Cache hit rate
    private static final String BATCH_INSTRUCTIONS =
            "You are a professional Minecraft game localization engine. " +
            "Translate the input JSON array of strings into concise Simplified Chinese. " +
            "Rules:\n" +
            "1. Preserve Minecraft game terminology (enchantments, attributes, effects, rarities, ranks, scoreboard data).\n" +
            "2. Keep numbers, punctuation, and structural symbols intact.\n" +
            "3. Output ONLY a valid JSON array of translated strings with the exact same element count and order.\n" +
            "4. Do NOT output any markdown code blocks, explanations, or introductory text.";

    private static final String SINGLE_INSTRUCTIONS =
            "You are a professional Minecraft game localization engine. " +
            "Translate the input text into concise Simplified Chinese. " +
            "Rules:\n" +
            "1. Preserve Minecraft game terminology (enchantments, attributes, effects, rarities, ranks, scoreboard data).\n" +
            "2. Output ONLY the translated text without quotes, explanations, or notes.";

    private final HttpClient httpClient;

    public DeepSeekResponsesTranslator() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();
    }

    private String getEndpoint(LinguaCraftConfig config) {
        String endpoint = (config.apiEndpoint != null && !config.apiEndpoint.isBlank())
                ? config.apiEndpoint.trim()
                : "https://api.deepseek.com/v1/responses";

        if (endpoint.endsWith("api.deepseek.com") || endpoint.endsWith("api.deepseek.com/")) {
            endpoint = endpoint.replaceAll("/+$", "") + "/v1/responses";
        }
        return endpoint;
    }

    public CompletableFuture<String> translate(String text, LinguaCraftConfig config) {
        if (text == null || text.isBlank() || config.apiKey == null || config.apiKey.isBlank()) {
            return CompletableFuture.completedFuture(text);
        }

        String endpoint = getEndpoint(config);
        String modelName = (config.model != null && !config.model.isBlank()) ? config.model.trim() : "deepseek-v4-flash";

        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", modelName);
        requestJson.addProperty("instructions", SINGLE_INSTRUCTIONS);
        requestJson.addProperty("input", text);
        requestJson.addProperty("temperature", 0.1);

        // Turn off reasoning/thinking effort
        JsonObject reasoning = new JsonObject();
        reasoning.addProperty("effort", "none");
        requestJson.add("reasoning", reasoning);

        String requestBody = GSON.toJson(requestJson);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(8))
                .header("Authorization", "Bearer " + config.apiKey.trim())
                .header("Content-Type", "application/json")
                .header("User-Agent", "LinguaCraft-Fabric/1.0.0")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonObject responseJson = GSON.fromJson(response.body(), JsonObject.class);
                            logUsageAndCache(responseJson);

                            String extracted = extractMessageText(responseJson);
                            if (extracted != null && !extracted.isBlank()) {
                                return cleanText(extracted);
                            }
                        } catch (Exception e) {
                            LOGGER.error("Failed to parse DeepSeek Responses API result: {}", response.body(), e);
                        }
                    } else {
                        LOGGER.warn("DeepSeek API error HTTP {}: {}", response.statusCode(), response.body());
                    }
                    return text;
                })
                .exceptionally(throwable -> {
                    LOGGER.error("DeepSeek API communication failure: {}", throwable.getMessage());
                    return text;
                });
    }

    public CompletableFuture<List<String>> translateBatch(List<String> texts, LinguaCraftConfig config) {
        if (texts == null || texts.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        if (texts.size() == 1) {
            return translate(texts.getFirst(), config).thenApply(Collections::singletonList);
        }
        if (config.apiKey == null || config.apiKey.isBlank()) {
            return CompletableFuture.completedFuture(new ArrayList<>(texts));
        }

        String endpoint = getEndpoint(config);
        String modelName = (config.model != null && !config.model.isBlank()) ? config.model.trim() : "deepseek-v4-flash";

        JsonArray inputArray = new JsonArray();
        for (String t : texts) {
            inputArray.add(t);
        }

        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", modelName);
        requestJson.addProperty("instructions", BATCH_INSTRUCTIONS);
        requestJson.addProperty("input", GSON.toJson(inputArray));
        requestJson.addProperty("temperature", 0.1);

        // Turn off reasoning/thinking effort
        JsonObject reasoning = new JsonObject();
        reasoning.addProperty("effort", "none");
        requestJson.add("reasoning", reasoning);

        String requestBody = GSON.toJson(requestJson);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + config.apiKey.trim())
                .header("Content-Type", "application/json")
                .header("User-Agent", "LinguaCraft-Fabric/1.0.0")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonObject responseJson = GSON.fromJson(response.body(), JsonObject.class);
                            logUsageAndCache(responseJson);

                            String outputRaw = extractMessageText(responseJson);
                            if (outputRaw != null) {
                                List<String> parsed = parseJsonArray(outputRaw, texts.size());
                                if (parsed != null && parsed.size() == texts.size()) {
                                    return parsed;
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.error("Failed to parse DeepSeek batch response: {}", response.body(), e);
                        }
                    } else {
                        LOGGER.warn("DeepSeek batch API error HTTP {}: {}", response.statusCode(), response.body());
                    }
                    return new ArrayList<>(texts);
                })
                .exceptionally(throwable -> {
                    LOGGER.error("DeepSeek batch API failure: {}", throwable.getMessage());
                    return new ArrayList<>(texts);
                });
    }

    /**
     * Extracts ONLY final message text, strictly ignoring CoT / reasoning / thinking tokens
     */
    private String extractMessageText(JsonObject responseJson) {
        // 1. Prefer inspecting 'output' items to filter out any 'reasoning' items
        if (responseJson.has("output")) {
            JsonArray outputArray = responseJson.getAsJsonArray("output");
            for (JsonElement itemElem : outputArray) {
                if (itemElem.isJsonObject()) {
                    JsonObject itemObj = itemElem.getAsJsonObject();
                    String itemType = itemObj.has("type") ? itemObj.get("type").getAsString() : "";
                    
                    // Skip reasoning item completely!
                    if ("reasoning".equalsIgnoreCase(itemType)) {
                        continue;
                    }

                    if (itemObj.has("content")) {
                        JsonArray contentArray = itemObj.getAsJsonArray("content");
                        for (JsonElement cElem : contentArray) {
                            if (cElem.isJsonObject()) {
                                JsonObject cObj = cElem.getAsJsonObject();
                                String contentType = cObj.has("type") ? cObj.get("type").getAsString() : "";
                                if ("reasoning_text".equalsIgnoreCase(contentType)) {
                                    continue;
                                }
                                if (cObj.has("text")) {
                                    return cObj.get("text").getAsString().trim();
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Direct output_text if present and output array was not present
        if (responseJson.has("output_text")) {
            String out = responseJson.get("output_text").getAsString().trim();
            // Safeguard: make sure it's not reasoning text
            if (!out.startsWith("We need to translate") && !out.startsWith("The user wants")) {
                return out;
            }
        }

        // 3. Fallback for chat/completions (choices)
        if (responseJson.has("choices")) {
            JsonArray choices = responseJson.getAsJsonArray("choices");
            if (!choices.isEmpty()) {
                JsonObject choice = choices.get(0).getAsJsonObject();
                if (choice.has("message")) {
                    JsonObject message = choice.getAsJsonObject("message");
                    if (message.has("content")) {
                        return message.get("content").getAsString().trim();
                    }
                }
            }
        }

        return null;
    }

    private void logUsageAndCache(JsonObject responseJson) {
        if (responseJson.has("usage")) {
            JsonObject usage = responseJson.getAsJsonObject("usage");
            int cachedTokens = usage.has("prompt_cache_hit_tokens") ? usage.get("prompt_cache_hit_tokens").getAsInt() : 0;
            int missTokens = usage.has("prompt_cache_miss_tokens") ? usage.get("prompt_cache_miss_tokens").getAsInt() : 0;
            if (cachedTokens > 0) {
                LOGGER.debug("DeepSeek Context Cache HIT: {} tokens (Miss: {} tokens)", cachedTokens, missTokens);
            }
        }
    }

    private List<String> parseJsonArray(String raw, int expectedSize) {
        String clean = raw.trim();
        // Remove markdown ```json ``` wraps if present
        if (clean.startsWith("```")) {
            int firstNewline = clean.indexOf('\n');
            int lastBacktick = clean.lastIndexOf("```");
            if (firstNewline != -1 && lastBacktick > firstNewline) {
                clean = clean.substring(firstNewline + 1, lastBacktick).trim();
            }
        }

        try {
            JsonArray arr = GSON.fromJson(clean, JsonArray.class);
            List<String> list = new ArrayList<>();
            for (JsonElement elem : arr) {
                list.add(elem.getAsString());
            }
            return list;
        } catch (Exception e) {
            LOGGER.warn("Could not parse as JSON array: '{}'", clean);
            return null;
        }
    }

    private String cleanText(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() > 1) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
