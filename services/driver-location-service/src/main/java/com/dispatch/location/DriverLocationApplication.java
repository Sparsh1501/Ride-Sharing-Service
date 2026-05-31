package com.dispatch.location;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DriverLocationApplication {
    public static void main(String[] args) {
        SpringApplication.run(DriverLocationApplication.class, args);
    }
}
