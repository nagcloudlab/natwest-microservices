package com.demo.command;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class EventStore {

    private final List<Map<String, Object>> events = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong(0);

    public Map<String, Object> append(String name, String message) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", "GreetingCreated");
        event.put("sequence", sequence.incrementAndGet());
        event.put("timestamp", Instant.now().toString());
        event.put("name", name);
        event.put("message", message);
        events.add(event);
        return event;
    }

    public List<Map<String, Object>> getAllEvents() {
        return Collections.unmodifiableList(events);
    }
}
