package com.reydark.reycom.dto.response;

public record AuthResponse(
        String token,
        String tokenType,
        UserResponse user
) {
}
