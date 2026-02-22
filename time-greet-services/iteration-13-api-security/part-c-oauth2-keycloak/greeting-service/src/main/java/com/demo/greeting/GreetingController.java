package com.demo.greeting;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class GreetingController {

    @GetMapping("/greeting")
    public Map<String, String> greeting(@AuthenticationPrincipal Jwt jwt) throws UnknownHostException {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("message", "Hello from Greeting Service!");
        response.put("authenticatedUser", jwt.getClaimAsString("preferred_username"));
        response.put("host", InetAddress.getLocalHost().getHostName());
        return response;
    }

    @GetMapping("/greeting/whoami")
    public Map<String, Object> whoami(@AuthenticationPrincipal Jwt jwt) throws UnknownHostException {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("user", jwt.getClaimAsString("preferred_username"));
        response.put("email", jwt.getClaimAsString("email"));
        response.put("realmAccess", jwt.getClaimAsMap("realm_access"));
        response.put("issuer", jwt.getIssuer().toString());
        response.put("host", InetAddress.getLocalHost().getHostName());
        return response;
    }
}
