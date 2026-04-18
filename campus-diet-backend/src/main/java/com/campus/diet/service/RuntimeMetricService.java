package com.campus.diet.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAccumulator;

@Service
public class RuntimeMetricService {

    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> timeTotalsMs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> timeSamples = new ConcurrentHashMap<>();
    /** 进程内观测到的单次耗时上界（非窗口 P95；用于最小看板与告警粗判）。 */
    private final ConcurrentHashMap<String, LongAccumulator> timeMaxMs = new ConcurrentHashMap<>();

    public void increment(String key) {
        counters.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
    }

    public void incrementBy(String key, long delta) {
        counters.computeIfAbsent(key, ignored -> new AtomicLong()).addAndGet(delta);
    }

    public void recordCostMs(String key, long costMs) {
        long safeCost = Math.max(0L, costMs);
        timeTotalsMs.computeIfAbsent(key, ignored -> new AtomicLong()).addAndGet(safeCost);
        timeSamples.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
        timeMaxMs
                .computeIfAbsent(key, ignored -> new LongAccumulator(Long::max, 0L))
                .accumulate(safeCost);
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Long> counterSnapshot = new LinkedHashMap<>();
        for (Map.Entry<String, AtomicLong> entry : counters.entrySet()) {
            counterSnapshot.put(entry.getKey(), entry.getValue().get());
        }
        Map<String, Long> avgMs = new LinkedHashMap<>();
        for (Map.Entry<String, AtomicLong> entry : timeTotalsMs.entrySet()) {
            String key = entry.getKey();
            long total = entry.getValue().get();
            long sample = timeSamples.getOrDefault(key, new AtomicLong()).get();
            avgMs.put(key, sample <= 0 ? 0L : total / sample);
        }
        Map<String, Long> maxMs = new LinkedHashMap<>();
        for (Map.Entry<String, LongAccumulator> entry : timeMaxMs.entrySet()) {
            maxMs.put(entry.getKey(), entry.getValue().get());
        }
        out.put("counters", counterSnapshot);
        out.put("avgCostMs", avgMs);
        out.put("maxCostMs", maxMs);
        return out;
    }
}
