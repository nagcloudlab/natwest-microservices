package com.demo.greeting.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GreetingNotFoundException.class)
    public ProblemDetail handleGreetingNotFound(GreetingNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Greeting Not Found");
        problem.setType(URI.create("https://api.example.com/errors/greeting-not-found"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("service", "greeting-service");
        return problem;
    }
}
