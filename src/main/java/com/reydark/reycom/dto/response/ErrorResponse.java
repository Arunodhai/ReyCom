package com.reydark.reycom.dto.response;

import java.util.List;

public record ErrorResponse(
        boolean success,
        String message,
        List<String> errors
) {

    public static ErrorResponse of(String message) {
        return new ErrorResponse(false, message, List.of());
    }

    public static ErrorResponse of(String message, List<String> errors) {
        return new ErrorResponse(false, message, errors);
    }
}
