package com.c2java;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * C2JAVA 자동화 프로그램 메인 애플리케이션
 * C 언어 소스코드를 Java(Spring Boot 3.2.5)로 자동 변환하는 시스템
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class C2JavaApplication {

    public static void main(String[] args) {
        SpringApplication.run(C2JavaApplication.class, args);
    }
}
