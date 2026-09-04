package com.example.codeassistant.user;

public record UserDto(Long id, String username, String email, String avatarUrl) {
    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getUsername(), user.getEmail(), user.getAvatarUrl());
    }
}
