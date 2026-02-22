package com.demo.greeting;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
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

    @GetMapping("/greeting/whoami")
    public Map<String, String> whoami(
            @RequestHeader(value = "X-Authenticated-User", defaultValue = "anonymous") String user,
            @RequestHeader(value = "X-User-Roles", defaultValue = "none") String roles)
            throws UnknownHostException {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("authenticatedUser", user);
        response.put("roles", roles);
        response.put("host", InetAddress.getLocalHost().getHostName());
        return response;
    }
}
