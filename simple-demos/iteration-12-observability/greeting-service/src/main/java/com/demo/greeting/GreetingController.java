package com.demo.greeting;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

@RestController
public class GreetingController {

    @GetMapping("/greeting")
    public Map<String, String> greeting() throws UnknownHostException {
        return Map.of(
                "message", "Hello from Greeting Service!",
                "host", InetAddress.getLocalHost().getHostName()
        );
    }
}
