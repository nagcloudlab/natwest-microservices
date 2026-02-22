package com.demo.time;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class TimeController {

    private final RestTemplate restTemplate;

    public TimeController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping("/time")
    public Map<String, String> time() throws UnknownHostException {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("currentTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        response.put("host", InetAddress.getLocalHost().getHostName());
        return response;
    }

    @SuppressWarnings("unchecked")
    @GetMapping("/time/with-greeting")
    public Map<String, Object> timeWithGreeting() throws UnknownHostException {
        Map<String, String> greeting = restTemplate.getForObject(
                "http://greeting-service/greeting", Map.class);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("currentTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        response.put("host", InetAddress.getLocalHost().getHostName());
        response.put("greeting", greeting);
        return response;
    }
}
