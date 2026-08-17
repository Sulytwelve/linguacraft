package cn.sulyhub.linguacraft.client.translate;

import java.util.LinkedHashMap;
import java.util.Map;

public class TranslationCache {
    private static final int MAX_CACHE_SIZE = 3000;
    private final Map<String, String> cache;

    public TranslationCache() {
        this.cache = new LinkedHashMap<String, String>(MAX_CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        };
    }

    public synchronized String get(String key) {
        return cache.get(key);
    }

    public synchronized void put(String key, String value) {
        if (key != null && value != null) {
            cache.put(key, value);
        }
    }

    public synchronized boolean contains(String key) {
        return cache.containsKey(key);
    }

    public synchronized void clear() {
        cache.clear();
    }

    public synchronized int size() {
        return cache.size();
    }
}
