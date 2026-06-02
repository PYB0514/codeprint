// Spring Boot 애플리케이션 진입점
package com.codeprint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class CodeprintApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeprintApplication.class, args);
    }
}
