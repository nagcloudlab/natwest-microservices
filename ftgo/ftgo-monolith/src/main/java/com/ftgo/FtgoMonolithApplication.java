package com.ftgo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FTGO — Food To Go
 *
 * This is a MONOLITHIC application. All modules run in a single process:
 *   - Order Module        (com.ftgo.order)
 *   - Kitchen Module      (com.ftgo.kitchen)
 *   - Delivery Module     (com.ftgo.delivery)
 *   - Restaurant Module   (com.ftgo.restaurant)
 *   - Accounting Module   (com.ftgo.accounting)
 *   - Notification Module (com.ftgo.notification)
 *
 * All modules share ONE database, ONE deployment, ONE process.
 */
@SpringBootApplication
public class FtgoMonolithApplication {

    public static void main(String[] args) {
        SpringApplication.run(FtgoMonolithApplication.class, args);
    }
}
