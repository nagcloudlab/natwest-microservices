package com.demo.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/greetings")
public class CommandController {

    private static final Logger log = LoggerFactory.getLogger(CommandController.class);

    private final EventStore eventStore;
    private final EventPublisher eventPublisher;

    public CommandController(EventStore eventStore, EventPublisher eventPublisher) {
        this.eventStore = eventStore;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createGreeting(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        String message = request.get("message");

        // 1. Append to event store (source of truth)
        Map<String, Object> event = eventStore.append(name, message);
        log.info("Event stored: sequence={}, name={}", event.get("sequence"), name);

        // 2. Publish to Kafka (for downstream consumers)
        eventPublisher.publish(name, event);

        return event;
    }

    @GetMapping
    public List<Map<String, Object>> getEventLog() {
        return eventStore.getAllEvents();
    }
}
