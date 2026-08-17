package cn.sulyhub.linguacraft.client.translate;

import cn.sulyhub.linguacraft.client.config.LinguaCraftConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class TranslationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("LinguaCraft/Manager");
    private static final Pattern FORMATTING_PATTERN = Pattern.compile("(?i)§[0-9A-FK-OR]");
    private static final Pattern CHINESE_PATTERN = Pattern.compile("\\p{IsHan}");
    private static final Pattern SYMBOLS_NUMBERS_PATTERN = Pattern.compile("^[\\s\\d\\p{Punct}\\p{Sc}]+$");

    private static TranslationManager INSTANCE;

    private final DeepSeekResponsesTranslator deepSeekTranslator;
    private final OpenAICompatibleTranslator openAITranslator;
    private final DeepLTranslator deepLTranslator;
    private final TranslationCache cache;
    private final Map<String, CompletableFuture<String>> inFlight;

    public TranslationManager() {
        this.deepSeekTranslator = new DeepSeekResponsesTranslator();
        this.openAITranslator = new OpenAICompatibleTranslator();
        this.deepLTranslator = new DeepLTranslator();
        this.cache = new TranslationCache();
        this.inFlight = new ConcurrentHashMap<>();
    }

    public static TranslationManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new TranslationManager();
        }
        return INSTANCE;
    }

    public static String stripFormatting(String input) {
        if (input == null) return "";
        return FORMATTING_PATTERN.matcher(input).replaceAll("");
    }

    public boolean shouldSkip(String cleanText) {
        if (cleanText == null || cleanText.isBlank()) {
            return true;
        }
        if (CHINESE_PATTERN.matcher(cleanText).find()) {
            return true;
        }
        if (SYMBOLS_NUMBERS_PATTERN.matcher(cleanText).matches()) {
            return true;
        }
        if (cleanText.trim().length() <= 1) {
            return true;
        }
        return false;
    }

    private CompletableFuture<String> executeTranslate(String cleanText, LinguaCraftConfig config) {
        if (config.provider == LinguaCraftConfig.Provider.DEEPSEEK_RESPONSES) {
            return deepSeekTranslator.translate(cleanText, config);
        } else if (config.provider == LinguaCraftConfig.Provider.DEEPL) {
            return deepLTranslator.translate(cleanText, config.apiKey, config.targetLanguage);
        } else {
            return openAITranslator.translate(cleanText, config);
        }
    }

    public String getOrRequest(String originalText, Runnable onComplete) {
        LinguaCraftConfig config = LinguaCraftConfig.getInstance();
        if (!config.enabled || config.apiKey == null || config.apiKey.isBlank()) {
            return originalText;
        }

        String cleanText = stripFormatting(originalText).trim();
        if (shouldSkip(cleanText)) {
            return originalText;
        }

        String cacheKey = config.provider.name() + ":" + config.targetLanguage + ":" + cleanText;
        String cached = cache.get(cacheKey);
        if (cached != null) {
            return formatResult(originalText, cleanText, cached, config);
        }

        inFlight.computeIfAbsent(cacheKey, key -> {
            return executeTranslate(cleanText, config)
                    .thenApply(translated -> {
                        if (translated != null && !translated.equals(cleanText)) {
                            cache.put(cacheKey, translated);
                        }
                        inFlight.remove(cacheKey);
                        if (onComplete != null) {
                            onComplete.run();
                        }
                        return translated;
                    });
        });

        return originalText;
    }

    public void queueBatch(List<String> originalTexts, Runnable onComplete) {
        LinguaCraftConfig config = LinguaCraftConfig.getInstance();
        if (!config.enabled || config.apiKey == null || config.apiKey.isBlank()) {
            return;
        }

        List<String> toTranslate = new ArrayList<>();
        List<String> cacheKeys = new ArrayList<>();

        for (String originalText : originalTexts) {
            String cleanText = stripFormatting(originalText).trim();
            if (shouldSkip(cleanText)) {
                continue;
            }
            String cacheKey = config.provider.name() + ":" + config.targetLanguage + ":" + cleanText;
            if (!cache.contains(cacheKey) && !inFlight.containsKey(cacheKey)) {
                toTranslate.add(cleanText);
                cacheKeys.add(cacheKey);
            }
        }

        if (toTranslate.isEmpty()) {
            return;
        }

        CompletableFuture<List<String>> batchFuture;
        if (config.provider == LinguaCraftConfig.Provider.DEEPSEEK_RESPONSES) {
            batchFuture = deepSeekTranslator.translateBatch(toTranslate, config);
        } else if (config.provider == LinguaCraftConfig.Provider.DEEPL) {
            batchFuture = deepLTranslator.translateBatch(toTranslate, config.apiKey, config.targetLanguage);
        } else {
            batchFuture = openAITranslator.translateBatch(toTranslate, config);
        }

        batchFuture.thenAccept(results -> {
            for (int i = 0; i < results.size() && i < cacheKeys.size(); i++) {
                String translated = results.get(i);
                String key = cacheKeys.get(i);
                if (translated != null) {
                    cache.put(key, translated);
                }
            }
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    private String formatResult(String originalText, String cleanText, String translated, LinguaCraftConfig config) {
        if (config.showOriginalAndTranslation) {
            return originalText + " " + config.prefix + translated;
        } else {
            if (originalText.contains(cleanText)) {
                return originalText.replace(cleanText, config.prefix + translated);
            }
            return config.prefix + translated;
        }
    }

    public TranslationCache getCache() {
        return cache;
    }
}
