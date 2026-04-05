package com.grcplatform.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.grcplatform")
@EnableScheduling
public class GrcPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrcPlatformApplication.class, args);
    }
}
