package com.example.codeassistant.security;

import com.example.codeassistant.user.User;
import com.example.codeassistant.user.UserRepository;
import com.example.codeassistant.common.NotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * Resolves the authenticated GitHub OAuth2 principal into our local User
 * entity. Controllers depend on this instead of touching the Authentication
 * object directly.
 */
@Component
public class CurrentUser {

    private final UserRepository userRepository;

    public CurrentUser(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User resolve(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OAuth2User oAuth2User)) {
            throw new NotFoundException("No authenticated user");
        }
        Long githubId = Long.valueOf(oAuth2User.getName());
        return userRepository.findByGithubId(githubId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
