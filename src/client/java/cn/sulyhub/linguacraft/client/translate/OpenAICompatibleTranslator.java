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

public class OpenAICompatibleTranslator {
    private static final Logger LOGGER = LoggerFactory.getLogger("LinguaCraft/OpenAI-DeepSeek");
    private static final Gson GSON = new Gson();

    private static final String SYSTEM_PROMPT = 
            "You are a professional Minecraft game translator. " +
            "Translate the given text accurately and concisely into Simplified Chinese. " +
            "Preserve Minecraft terminology (e.g. enchantment names, RPG stats, potion effects, ranks). " +
            "Do NOT output explanations, introductory words, or markdown quotes. " +
            "Output ONLY the translated text.";

    private final HttpClient httpClient;

    public OpenAICompatibleTranslator() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public CompletableFuture<String> translate(String text, LinguaCraftConfig config) {
        if (text == null || text.isBlank() || config.apiKey == null || config.apiKey.isBlank()) {
            return CompletableFuture.completedFuture(text);
        }

        String endpoint = (config.apiEndpoint != null && !config.apiEndpoint.isBlank()) 
                ? config.apiEndpoint.trim() 
                : "https://api.deepseek.com/chat/completions";
        String modelName = (config.model != null && !config.model.isBlank()) 
                ? config.model.trim() 
                : "deepseek-chat";

        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", modelName);
        requestJson.addProperty("temperature", 0.2);

        JsonArray messages = new JsonArray();

        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", SYSTEM_PROMPT);
        messages.add(systemMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", text);
        messages.add(userMsg);

        requestJson.add("messages", messages);

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
                    int statusCode = response.statusCode();
                    if (statusCode == 200) {
                        try {
                            JsonObject responseJson = GSON.fromJson(response.body(), JsonObject.class);
                            if (responseJson.has("choices")) {
                                JsonArray choices = responseJson.getAsJsonArray("choices");
                                if (!choices.isEmpty()) {
                                    JsonObject choice = choices.get(0).getAsJsonObject();
                                    if (choice.has("message")) {
                                        JsonObject message = choice.getAsJsonObject("message");
                                        if (message.has("content")) {
                                            String content = message.get("content").getAsString().trim();
                                            // Strip any unwanted wrapping quotes if LLM added them
                                            if (content.startsWith("\"") && content.endsWith("\"") && content.length() > 1) {
                                                content = content.substring(1, content.length() - 1);
                                            }
                                            return content;
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.error("Failed to parse OpenAI/DeepSeek API response: {}", response.body(), e);
                        }
                    } else if (statusCode == 401) {
                        LOGGER.warn("API authentication failed (HTTP 401). Please verify your API key.");
                    } else if (statusCode == 429) {
                        LOGGER.warn("API rate limited (HTTP 429). Too many requests or balance depleted.");
                    } else {
                        LOGGER.warn("API returned HTTP {}: {}", statusCode, response.body());
                    }
                    return text;
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Error communicating with API ({}): {}", endpoint, throwable.getMessage());
                    return text;
                });
    }

    public CompletableFuture<List<String>> translateBatch(List<String> texts, LinguaCraftConfig config) {
        if (texts == null || texts.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (String t : texts) {
            futures.add(translate(t, config));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<String> results = new ArrayList<>();
                    for (CompletableFuture<String> f : futures) {
                        results.add(f.join());
                    }
                    return results;
                });
    }
}
