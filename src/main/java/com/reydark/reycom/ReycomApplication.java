package com.reydark.reycom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class ReycomApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReycomApplication.class, args);
    }

}
