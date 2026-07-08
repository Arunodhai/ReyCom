package com.reydark.reycom.controller;

import com.reydark.reycom.dto.response.ApiResponse;
import com.reydark.reycom.dto.response.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.success(
                "Operation successful",
                new HealthResponse("UP", "ReyCom API")
        );
    }
}
