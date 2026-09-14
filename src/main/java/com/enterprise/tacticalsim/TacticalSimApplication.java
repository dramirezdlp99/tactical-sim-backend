package com.enterprise.tacticalsim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class TacticalSimApplication {

    public static void main(String[] args) {
        SpringApplication.run(TacticalSimApplication.class, args);
    }
}