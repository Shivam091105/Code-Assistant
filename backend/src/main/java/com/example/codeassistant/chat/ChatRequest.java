package com.example.codeassistant.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank(message = "question must not be blank")
        @Size(max = 4000, message = "question is too long")
        String question
) {
}
