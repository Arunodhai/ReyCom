package com.reydark.reycom.service;

import com.reydark.reycom.dto.request.LoginRequest;
import com.reydark.reycom.dto.request.RegisterRequest;
import com.reydark.reycom.dto.response.AuthResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}
