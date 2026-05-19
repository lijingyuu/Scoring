package com.scoring.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.scoring.backend.mapper")
public class ScoringBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScoringBackendApplication.class, args);
    }
}
