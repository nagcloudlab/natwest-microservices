package com.demo.gateway;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class QueryController {

    private final GreetingClient greetingClient;
    private final TimeClient timeClient;

    public QueryController(GreetingClient greetingClient, TimeClient timeClient) {
        this.greetingClient = greetingClient;
        this.timeClient = timeClient;
    }

    // === Java Records for GraphQL types ===

    record Greeting(String message, String host) {}
    record Time(String currentTime, String host) {}
    record FetchTiming(int greetingMs, int timeMs, int totalMs) {}
    record Combined(Greeting greeting, Time time, FetchTiming fetchedIn) {}

    // === GraphQL Query Mappings ===

    @QueryMapping
    public Greeting greeting() {
        Map<String, Object> result = greetingClient.getGreeting();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        return new Greeting(
                (String) data.get("message"),
                (String) data.get("host")
        );
    }

    @QueryMapping
    public Time time() {
        Map<String, Object> result = timeClient.getTime();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        return new Time(
                (String) data.get("currentTime"),
                (String) data.get("host")
        );
    }

    @QueryMapping
    public Combined combined() {
        long totalStart = System.nanoTime();

        Map<String, Object> greetingResult = greetingClient.getGreeting();
        @SuppressWarnings("unchecked")
        Map<String, Object> greetingData = (Map<String, Object>) greetingResult.get("data");
        int greetingMs = ((Number) greetingResult.get("latencyMs")).intValue();

        Map<String, Object> timeResult = timeClient.getTime();
        @SuppressWarnings("unchecked")
        Map<String, Object> timeData = (Map<String, Object>) timeResult.get("data");
        int timeMs = ((Number) timeResult.get("latencyMs")).intValue();

        int totalMs = (int) ((System.nanoTime() - totalStart) / 1_000_000);

        return new Combined(
                new Greeting(
                        (String) greetingData.get("message"),
                        (String) greetingData.get("host")
                ),
                new Time(
                        (String) timeData.get("currentTime"),
                        (String) timeData.get("host")
                ),
                new FetchTiming(greetingMs, timeMs, totalMs)
        );
    }

    // === REST Comparison Endpoint ===

    @GetMapping("/api/combined")
    @ResponseBody
    public Map<String, Object> restCombined() {
        long totalStart = System.nanoTime();

        Map<String, Object> greetingResult = greetingClient.getGreeting();
        @SuppressWarnings("unchecked")
        Map<String, Object> greetingData = (Map<String, Object>) greetingResult.get("data");
        int greetingMs = ((Number) greetingResult.get("latencyMs")).intValue();

        Map<String, Object> timeResult = timeClient.getTime();
        @SuppressWarnings("unchecked")
        Map<String, Object> timeData = (Map<String, Object>) timeResult.get("data");
        int timeMs = ((Number) timeResult.get("latencyMs")).intValue();

        int totalMs = (int) ((System.nanoTime() - totalStart) / 1_000_000);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("greeting", greetingData);
        response.put("time", timeData);

        Map<String, Object> fetchedIn = new LinkedHashMap<>();
        fetchedIn.put("greetingMs", greetingMs);
        fetchedIn.put("timeMs", timeMs);
        fetchedIn.put("totalMs", totalMs);
        response.put("fetchedIn", fetchedIn);

        return response;
    }
}
