package cn.sulyhub.linguacraft.client.translate;

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

public class DeepLTranslator {
    private static final Logger LOGGER = LoggerFactory.getLogger("LinguaCraft/DeepL");
    private static final String FREE_API_URL = "https://api-free.deepl.com/v2/translate";
    private static final String PRO_API_URL = "https://api.deepl.com/v2/translate";
    private static final Gson GSON = new Gson();

    private final HttpClient httpClient;

    public DeepLTranslator() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();
    }

    private String getEndpoint(String apiKey) {
        if (apiKey != null && apiKey.endsWith(":fx")) {
            return FREE_API_URL;
        }
        return PRO_API_URL;
    }

    public CompletableFuture<String> translate(String text, String apiKey, String targetLang) {
        if (text == null || text.isBlank() || apiKey == null || apiKey.isBlank()) {
            return CompletableFuture.completedFuture(text);
        }

        return translateBatch(Collections.singletonList(text), apiKey, targetLang)
                .thenApply(list -> list.isEmpty() ? text : list.getFirst());
    }

    public CompletableFuture<List<String>> translateBatch(List<String> texts, String apiKey, String targetLang) {
        if (texts == null || texts.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        if (apiKey == null || apiKey.isBlank()) {
            return CompletableFuture.completedFuture(new ArrayList<>(texts));
        }

        String endpoint = getEndpoint(apiKey);

        JsonObject requestJson = new JsonObject();
        JsonArray textArray = new JsonArray();
        for (String t : texts) {
            textArray.add(t);
        }
        requestJson.add("text", textArray);
        requestJson.addProperty("target_lang", (targetLang == null || targetLang.isBlank()) ? "ZH" : targetLang.toUpperCase());

        String requestBody = GSON.toJson(requestJson);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(8))
                .header("Authorization", "DeepL-Auth-Key " + apiKey.trim())
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
                            if (responseJson.has("translations")) {
                                JsonArray translations = responseJson.getAsJsonArray("translations");
                                List<String> results = new ArrayList<>();
                                for (JsonElement elem : translations) {
                                    if (elem.isJsonObject()) {
                                        JsonObject obj = elem.getAsJsonObject();
                                        if (obj.has("text")) {
                                            results.add(obj.get("text").getAsString());
                                        }
                                    }
                                }
                                return results;
                            }
                        } catch (Exception e) {
                            LOGGER.error("Failed to parse DeepL API response: {}", response.body(), e);
                        }
                    } else if (statusCode == 403) {
                        LOGGER.warn("DeepL API authentication failed (HTTP 403). Please check your API key.");
                    } else if (statusCode == 456) {
                        LOGGER.warn("DeepL API quota exceeded (HTTP 456).");
                    } else if (statusCode == 429) {
                        LOGGER.warn("DeepL API rate limited (HTTP 429). Too many requests.");
                    } else {
                        LOGGER.warn("DeepL API returned HTTP {}: {}", statusCode, response.body());
                    }
                    return new ArrayList<>(texts);
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Error communicating with DeepL API: {}", throwable.getMessage());
                    return new ArrayList<>(texts);
                });
    }
}
