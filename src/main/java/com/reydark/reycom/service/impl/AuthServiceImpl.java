package com.reydark.reycom.service.impl;

import com.reydark.reycom.dto.request.LoginRequest;
import com.reydark.reycom.dto.request.RegisterRequest;
import com.reydark.reycom.dto.response.AuthResponse;
import com.reydark.reycom.entity.User;
import com.reydark.reycom.enums.Role;
import com.reydark.reycom.exception.BadRequestException;
import com.reydark.reycom.exception.UnauthorizedException;
import com.reydark.reycom.mapper.UserMapper;
import com.reydark.reycom.repository.UserRepository;
import com.reydark.reycom.security.JwtService;
import com.reydark.reycom.security.UserPrincipal;
import com.reydark.reycom.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BadRequestException("Email is already registered");
        }

        User user = User.builder()
                .fullName(request.fullName().trim())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.password()))
                .role(Role.CUSTOMER)
                .build();

        User savedUser = userRepository.save(user);
        String token = jwtService.generateToken(new UserPrincipal(savedUser));

        return new AuthResponse(token, TOKEN_TYPE, UserMapper.toResponse(savedUser));
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    normalizedEmail,
                    request.password()
            ));
        } catch (AuthenticationException ex) {
            throw new UnauthorizedException("Invalid email or password");
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        String token = jwtService.generateToken(new UserPrincipal(user));

        return new AuthResponse(token, TOKEN_TYPE, UserMapper.toResponse(user));
    }
}
