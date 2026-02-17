package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import com.example.service.TransferService;
import com.example.service.UPITransferService;

@Configuration
@ComponentScan(basePackages = "com.example")
@EnableAutoConfiguration
@EnableAspectJAutoProxy
public class Application {

    private static Logger logger = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {

        // ---------------------------------------
        // Init / boot phasee
        // ---------------------------------------
        logger.info("-".repeat(50));
        // by configuration, components must be initialized in a specific order,
        ConfigurableApplicationContext context = null;
        context = SpringApplication.run(Application.class, args);

        logger.info("-".repeat(50));
        // ----------------------------------------
        // Run phase
        // ----------------------------------------
        // the application is now running, and components are doing their work,
        // TransferService upiTransferService =
        // context.getBean(UPITransferService.class);
        // System.out.println(upiTransferService.getClass().getName());
        // upiTransferService.transfer("11", "22", 100.0);

        logger.info("-".repeat(50));
        // ----------------------------------------
        // Shutdown phase
        // ----------------------------------------
        // logger.info("-".repeat(50));
        // context.close();
        // logger.info("-".repeat(50));

    }
}