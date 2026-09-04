package com.example.codeassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class CodeAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeAssistantApplication.class, args);
    }
}
