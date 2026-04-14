package com.campus.diet;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.campus.diet.mapper")
public class CampusDietApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusDietApplication.class, args);
    }
}
