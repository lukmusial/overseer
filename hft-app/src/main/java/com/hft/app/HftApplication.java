package com.hft.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.hft")
public class HftApplication {

    public static void main(String[] args) {
        SpringApplication.run(HftApplication.class, args);
    }
}
