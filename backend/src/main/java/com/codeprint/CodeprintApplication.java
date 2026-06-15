// Spring Boot 애플리케이션 진입점
package com.codeprint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableCaching
@EnableScheduling
public class CodeprintApplication {

    // 애플리케이션 진입점
    public static void main(String[] args) {
        SpringApplication.run(CodeprintApplication.class, args);
    }
}
