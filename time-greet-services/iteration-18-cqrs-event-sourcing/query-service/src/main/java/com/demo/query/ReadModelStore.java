package com.demo.query;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ReadModelStore {

    // View 1: Timeline — ordered list of all greetings
    private final List<Map<String, Object>> timeline = new CopyOnWriteArrayList<>();

    // View 2: Stats — aggregate counts
    private final AtomicInteger totalCount = new AtomicInteger(0);
    private final Set<String> uniqueNames = ConcurrentHashMap.newKeySet();
    private volatile String firstTimestamp = null;
    private volatile String latestTimestamp = null;

    // View 3: By-name index — greetings grouped by sender
    private final Map<String, List<Map<String, Object>>> byName = new ConcurrentHashMap<>();

    public void apply(Map<String, Object> event) {
        String name = (String) event.get("name");
        String timestamp = (String) event.get("timestamp");

        // Project into timeline view
        timeline.add(event);

        // Project into stats view
        totalCount.incrementAndGet();
        uniqueNames.add(name);
        if (firstTimestamp == null) {
            firstTimestamp = timestamp;
        }
        latestTimestamp = timestamp;

        // Project into by-name index
        byName.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>()).add(event);
    }

    public void clear() {
        timeline.clear();
        totalCount.set(0);
        uniqueNames.clear();
        firstTimestamp = null;
        latestTimestamp = null;
        byName.clear();
    }

    public List<Map<String, Object>> getTimeline() {
        return Collections.unmodifiableList(timeline);
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalGreetings", totalCount.get());
        stats.put("uniqueNames", uniqueNames.size());
        stats.put("names", new ArrayList<>(uniqueNames));
        stats.put("firstGreeting", firstTimestamp);
        stats.put("latestGreeting", latestTimestamp);
        return stats;
    }

    public Map<String, Object> getByName(String name) {
        List<Map<String, Object>> greetings = byName.getOrDefault(name, List.of());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("count", greetings.size());
        result.put("greetings", greetings);
        return result;
    }
}
