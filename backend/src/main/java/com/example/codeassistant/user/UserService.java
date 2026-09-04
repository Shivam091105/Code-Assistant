package com.example.codeassistant.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates or updates the local User record whenever someone signs in via
 * GitHub OAuth. This is the single place that turns GitHub profile data
 * into our own persisted user.
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User upsertFromGitHub(Long githubId, String username, String email, String avatarUrl) {
        return userRepository.findByGithubId(githubId)
                .map(existing -> {
                    existing.setUsername(username);
                    existing.setEmail(email);
                    existing.setAvatarUrl(avatarUrl);
                    return existing;
                })
                .orElseGet(() -> userRepository.save(new User(githubId, username, email, avatarUrl)));
    }
}
