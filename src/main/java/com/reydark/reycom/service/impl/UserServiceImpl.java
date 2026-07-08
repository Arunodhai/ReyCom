package com.reydark.reycom.service.impl;

import com.reydark.reycom.dto.response.UserResponse;
import com.reydark.reycom.entity.User;
import com.reydark.reycom.exception.UnauthorizedException;
import com.reydark.reycom.mapper.UserMapper;
import com.reydark.reycom.security.UserPrincipal;
import com.reydark.reycom.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    @Override
    public UserResponse getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException("Authentication is required");
        }

        User user = principal.getUser();
        return UserMapper.toResponse(user);
    }
}
