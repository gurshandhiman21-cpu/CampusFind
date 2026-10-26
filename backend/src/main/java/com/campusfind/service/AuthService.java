package com.campusfind.service;

import com.campusfind.dto.LoginRequest;
import com.campusfind.dto.RegisterRequest;
import com.campusfind.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;

/**
 * Service contract for user authentication, registration, session management,
 * and current identity resolution.
 */
public interface AuthService {

    /**
     * Registers a new student account.
     * Always assigns UserRole.STUDENT and AccountStatus.ACTIVE server-side.
     */
    UserResponse register(RegisterRequest request);

    /**
     * Authenticates user credentials, sets up secure session, and rotates session ID.
     */
    UserResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse);

    /**
     * Invalidates the active user session and clears the security context.
     */
    void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse);

    /**
     * Resolves the profile of the currently authenticated principal.
     */
    UserResponse getCurrentUser(Authentication authentication);
}
