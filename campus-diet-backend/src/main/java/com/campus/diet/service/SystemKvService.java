package com.campus.diet.service;

import com.campus.diet.entity.SystemKv;
import com.campus.diet.mapper.SystemKvMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SystemKvService {

    private static final class CacheVal {
        final String value;
        final long expireAtMs;

        CacheVal(String value, long expireAtMs) {
            this.value = value;
            this.expireAtMs = expireAtMs;
        }
    }

    private final SystemKvMapper systemKvMapper;
    private final Map<String, CacheVal> cache = new ConcurrentHashMap<>();
    private final long cacheTtlMs;

    public SystemKvService(
            SystemKvMapper systemKvMapper,
            @Value("${campus.system-kv.cache-ttl-ms:10000}") long cacheTtlMs) {
        this.systemKvMapper = systemKvMapper;
        this.cacheTtlMs = Math.max(0L, cacheTtlMs);
    }

    public String get(String key, String defaultVal) {
        long now = System.currentTimeMillis();
        CacheVal cached = cache.get(key);
        if (cached != null && cached.expireAtMs >= now) {
            return cached.value == null ? defaultVal : cached.value;
        }
        SystemKv row = systemKvMapper.selectById(key);
        String value = row == null ? defaultVal : row.getV();
        cache.put(key, new CacheVal(value, now + cacheTtlMs));
        return value;
    }

    public boolean flagOn(String key, boolean defaultOn) {
        String v = get(key, defaultOn ? "1" : "0");
        return "1".equals(v) || "true".equalsIgnoreCase(v);
    }

    public void upsert(String key, String value) {
        SystemKv kv = new SystemKv();
        kv.setK(key);
        kv.setV(value);
        if (systemKvMapper.selectById(key) == null) {
            systemKvMapper.insert(kv);
        } else {
            systemKvMapper.updateById(kv);
        }
        cache.remove(key);
    }
}
