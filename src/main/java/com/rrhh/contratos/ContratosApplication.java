package com.rrhh.contratos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ContratosApplication {
    public static void main(String[] args) {
        SpringApplication.run(ContratosApplication.class, args);
    }
}
