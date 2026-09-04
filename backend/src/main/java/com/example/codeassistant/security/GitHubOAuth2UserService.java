package com.example.codeassistant.security;

import com.example.codeassistant.user.UserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads the GitHub profile after OAuth2 login and upserts a local User
 * record. The OAuth2User "name" attribute is set to the GitHub numeric id
 * (as a string) so downstream code has a stable, non-guessable key -
 * we never store or expose the GitHub access token itself.
 */
@Service
public class GitHubOAuth2UserService extends DefaultOAuth2UserService {

    private final UserService userService;

    public GitHubOAuth2UserService(UserService userService) {
        this.userService = userService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User githubUser = super.loadUser(userRequest);

        Map<String, Object> attributes = githubUser.getAttributes();
        Long githubId = ((Number) attributes.get("id")).longValue();
        String username = (String) attributes.get("login");
        String email = (String) attributes.get("email");
        String avatarUrl = (String) attributes.get("avatar_url");

        userService.upsertFromGitHub(githubId, username, email, avatarUrl);

        Map<String, Object> normalizedAttributes = new LinkedHashMap<>(attributes);
        normalizedAttributes.put("id", String.valueOf(githubId));

        return new DefaultOAuth2User(githubUser.getAuthorities(), normalizedAttributes, "id");
    }
}
