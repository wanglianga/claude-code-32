package com.community.vax;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class VaccinePlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(VaccinePlatformApplication.class, args);
    }
}
