package com.demo.greeting.exception;

public class GreetingNotFoundException extends RuntimeException {
    public GreetingNotFoundException(String name) {
        super("Cannot greet '" + name + "' — name contains invalid characters");
    }
}
