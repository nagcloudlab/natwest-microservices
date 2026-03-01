package com.demo.time.controller;

import com.demo.time.api.CombinedApi;
import com.demo.time.model.CombinedResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.OffsetDateTime;

@RestController
public class TimeController implements CombinedApi {

    private final RestClient restClient;

    public TimeController(@Value("${greeting.service.url:http://localhost:9001}") String greetingUrl) {
        this.restClient = RestClient.builder().baseUrl(greetingUrl).build();
    }

    @Override
    public ResponseEntity<CombinedResponse> getCombined() {
        String greeting;
        try {
            var body = restClient.get()
                    .uri("/api/v1/greeting")
                    .retrieve()
                    .body(java.util.Map.class);
            greeting = body != null ? (String) body.get("message") : "Hello, World!";
        } catch (Exception e) {
            greeting = "Hello, World! (greeting-service unavailable)";
        }

        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            host = "unknown";
        }

        CombinedResponse response = new CombinedResponse()
                .time(OffsetDateTime.now())
                .greeting(greeting)
                .host(host);
        return ResponseEntity.ok(response);
    }
}
