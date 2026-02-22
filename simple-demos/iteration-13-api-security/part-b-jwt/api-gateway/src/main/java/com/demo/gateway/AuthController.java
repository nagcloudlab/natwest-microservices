package com.demo.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    // In-memory users (same as Part A, but now issues JWT)
    private final Map<String, UserRecord> users;

    record UserRecord(String encodedPassword, List<String> roles) {}

    public AuthController(JwtUtil jwtUtil, PasswordEncoder passwordEncoder) {
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.users = Map.of(
                "user", new UserRecord(passwordEncoder.encode("password"), List.of("ROLE_USER")),
                "admin", new UserRecord(passwordEncoder.encode("admin123"), List.of("ROLE_USER", "ROLE_ADMIN"))
        );
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody LoginRequest request) {
        UserRecord record = users.get(request.username());
        if (record == null || !passwordEncoder.matches(request.password(), record.encodedPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid credentials"));
        }

        String token = jwtUtil.generateToken(request.username(), record.roles());
        return ResponseEntity.ok(Map.of("token", token));
    }

    record LoginRequest(String username, String password) {}
}
