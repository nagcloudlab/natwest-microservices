package com.demo.greeting;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class GreetingController {

    @GetMapping("/greeting")
    public Map<String, String> greeting() throws UnknownHostException {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("message", "Hello from v1!");
        response.put("version", "v1");
        response.put("host", InetAddress.getLocalHost().getHostName());
        return response;
    }
}
