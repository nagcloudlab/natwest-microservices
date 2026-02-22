package com.demo.greeting;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class GreetingController {

    @GetMapping("/greeting")
    public Map<String, String> greeting(Authentication auth) throws UnknownHostException {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("message", "Hello from Greeting Service!");
        response.put("authenticatedUser", auth.getName());
        response.put("host", InetAddress.getLocalHost().getHostName());
        return response;
    }

    @GetMapping("/greeting/whoami")
    public Map<String, String> whoami(Authentication auth) throws UnknownHostException {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("user", auth.getName());
        response.put("roles", auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(",")));
        response.put("host", InetAddress.getLocalHost().getHostName());
        return response;
    }
}
