package com.demo.query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/greetings")
public class QueryController {

    private static final Logger log = LoggerFactory.getLogger(QueryController.class);

    private final ReadModelStore readModelStore;
    private final EventConsumer eventConsumer;

    public QueryController(ReadModelStore readModelStore, EventConsumer eventConsumer) {
        this.readModelStore = readModelStore;
        this.eventConsumer = eventConsumer;
    }

    @GetMapping
    public List<Map<String, Object>> getTimeline() {
        return readModelStore.getTimeline();
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        return readModelStore.getStats();
    }

    @GetMapping("/by-name/{name}")
    public Map<String, Object> getByName(@PathVariable String name) {
        return readModelStore.getByName(name);
    }

    @PostMapping("/rebuild")
    public Map<String, Object> rebuild() {
        log.info("Rebuild requested");
        int eventsReplayed = eventConsumer.rebuild();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "rebuilt");
        result.put("eventsReplayed", eventsReplayed);
        return result;
    }
}
