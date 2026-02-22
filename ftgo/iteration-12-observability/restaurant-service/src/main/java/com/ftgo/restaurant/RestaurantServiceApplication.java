package com.ftgo.restaurant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FTGO Restaurant Service — Extracted Microservice
 *
 * This service owns ALL restaurant and menu item data.
 * It is the single source of truth for:
 *   - Restaurant details (name, address, phone, open/closed)
 *   - Menu items (name, description, price)
 *
 * The monolith no longer has these tables — it calls this service via REST.
 */
@SpringBootApplication
public class RestaurantServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestaurantServiceApplication.class, args);
    }
}
