package com.demo.greeting;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class GreetingController {

    @Value("${greeting.message:Hello from Greeting Service!}")
    private String greetingMessage;

    @GetMapping("/greeting")
    public Map<String, String> greeting() throws UnknownHostException {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("message", greetingMessage);
        response.put("host", InetAddress.getLocalHost().getHostName());
        return response;
    }
}
