package com.demo.time;

import com.demo.time.grpc.GrpcGreetingClient;
import org.springframework.beans.factory.annotation.Value;
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
    private final GrpcGreetingClient grpcGreetingClient;

    @Value("${greeting.service.url}")
    private String greetingServiceUrl;

    public TimeController(RestTemplate restTemplate, GrpcGreetingClient grpcGreetingClient) {
        this.restTemplate = restTemplate;
        this.grpcGreetingClient = grpcGreetingClient;
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
                greetingServiceUrl + "/greeting", Map.class);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("currentTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        response.put("host", InetAddress.getLocalHost().getHostName());
        response.put("greeting", greeting);
        return response;
    }

    @GetMapping("/time/with-greeting/grpc")
    public Map<String, Object> timeWithGreetingGrpc() throws UnknownHostException {
        Map<String, String> greeting = grpcGreetingClient.getGreeting();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("currentTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        response.put("host", InetAddress.getLocalHost().getHostName());
        response.put("greeting", greeting);
        response.put("transport", "gRPC");
        return response;
    }

    @SuppressWarnings("unchecked")
    @GetMapping("/time/with-greeting/compare")
    public Map<String, Object> compareTransports() throws UnknownHostException {
        // REST call with timing
        long restStart = System.nanoTime();
        Map<String, String> restGreeting = restTemplate.getForObject(
                greetingServiceUrl + "/greeting", Map.class);
        long restLatency = (System.nanoTime() - restStart) / 1_000_000;

        // gRPC call with timing
        long grpcStart = System.nanoTime();
        Map<String, String> grpcGreeting = grpcGreetingClient.getGreeting();
        long grpcLatency = (System.nanoTime() - grpcStart) / 1_000_000;

        Map<String, Object> restResult = new LinkedHashMap<>();
        restResult.put("transport", "REST (HTTP/1.1 + JSON)");
        restResult.put("latencyMs", restLatency);
        restResult.put("greeting", restGreeting);

        Map<String, Object> grpcResult = new LinkedHashMap<>();
        grpcResult.put("transport", "gRPC (HTTP/2 + Protobuf)");
        grpcResult.put("latencyMs", grpcLatency);
        grpcResult.put("greeting", grpcGreeting);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("currentTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        response.put("host", InetAddress.getLocalHost().getHostName());
        response.put("rest", restResult);
        response.put("grpc", grpcResult);
        return response;
    }
}
