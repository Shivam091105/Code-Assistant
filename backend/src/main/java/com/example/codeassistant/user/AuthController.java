package com.example.codeassistant.user;

import com.example.codeassistant.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final CurrentUser currentUser;

    public AuthController(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    /** Returns the signed-in user, or 401 (via Spring Security's entry point) if not authenticated. */
    @GetMapping("/api/auth/me")
    public ResponseEntity<UserDto> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        User user = currentUser.resolve(authentication);
        return ResponseEntity.ok(UserDto.from(user));
    }
}
