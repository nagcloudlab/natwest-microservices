package com.demo.time.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RestClientException.class)
    public ProblemDetail handleServiceUnavailable(RestClientException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Greeting service is not reachable: " + ex.getMessage());
        problem.setTitle("Downstream Service Unavailable");
        problem.setType(URI.create("https://api.example.com/errors/service-unavailable"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("service", "time-service");
        return problem;
    }
}
