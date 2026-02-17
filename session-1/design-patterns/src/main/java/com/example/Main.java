package com.example;

// Aspect
class Logger {
    // Advice - Before
    public void log(String message) {
        System.out.println("Log: " + message);
    }
}

// Aspect
class Authorizer {
    // Advice - Before
    public boolean authorize(String user) {
        // Authorization logic
        System.out.println("Authorizing user: " + user);
        return true; // Assume authorization is successful
    }
}

class Trainer {

    // Join point
    public void getTraining() {
        // Logging
        // Authorization
        System.out.println("Training...");
    }
}

// Proxy
class TrainerProxy {

    private Trainer trainer = new Trainer();
    private Logger logger = new Logger();
    private Authorizer authorizer = new Authorizer();

    public void getTraining(String user) {
        logger.log("User " + user + " is requesting training.");
        if (authorizer.authorize(user)) {
            trainer.getTraining();
        } else {
            logger.log("Authorization failed for user: " + user);
        }
    }
}

// AOP -> using proxies add enterprise-level features like logging and
// authorization without modifying the core business logic in Trainer class.

public class Main {
    public static void main(String[] args) {
        TrainerProxy trainerProxy = new TrainerProxy();
        trainerProxy.getTraining("user123");
    }
}