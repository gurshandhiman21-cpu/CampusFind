package com.campusfind.service.impl;

import com.campusfind.dto.LoginRequest;
import com.campusfind.dto.RegisterRequest;
import com.campusfind.dto.UserResponse;
import com.campusfind.entity.User;
import com.campusfind.entity.enums.AccountStatus;
import com.campusfind.entity.enums.UserRole;
import com.campusfind.exception.EmailAlreadyExistsException;
import com.campusfind.exception.LoginRateLimitExceededException;
import com.campusfind.exception.ResourceNotFoundException;
import com.campusfind.repository.UserRepository;
import com.campusfind.service.AuthService;
import com.campusfind.service.LoginAttemptService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Service implementation managing user registration, BCrypt password hashing,
 * credential verification, rate limiting, and session security context lifecycle.
 */
@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final LoginAttemptService loginAttemptService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager,
                           LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public UserResponse register(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Registration request cannot be null.");
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException("An account with this email address already exists.");
        }

        User user = new User();
        user.setFullName(request.getFullName().trim());
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(UserRole.STUDENT);
        user.setStatus(AccountStatus.ACTIVE);

        User saved = userRepository.save(user);
        return UserResponse.fromEntity(saved);
    }

    @Override
    public UserResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        if (request == null) {
            throw new IllegalArgumentException("Login request cannot be null.");
        }

        String normalizedEmail = request.getEmail() != null ? request.getEmail().trim().toLowerCase(Locale.ROOT) : "";
        String clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : "unknown";

        // Enforce rate limiting on IP address or normalized email target
        if (loginAttemptService.isBlocked(clientIp) || (!normalizedEmail.isEmpty() && loginAttemptService.isBlocked(normalizedEmail))) {
            throw new LoginRateLimitExceededException("Too many failed login attempts. Please try again in 5 minutes.");
        }

        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
            );
            // Authentication succeeded: clear failure counts
            loginAttemptService.recordSuccess(clientIp);
            if (!normalizedEmail.isEmpty()) {
                loginAttemptService.recordSuccess(normalizedEmail);
            }
        } catch (AuthenticationException ex) {
            loginAttemptService.recordFailure(clientIp);
            if (!normalizedEmail.isEmpty()) {
                loginAttemptService.recordFailure(normalizedEmail);
            }
            if (ex instanceof DisabledException || ex instanceof LockedException) {
                throw ex;
            }
            throw new BadCredentialsException("Invalid email or password.");
        }

        // Establish authenticated security context and session
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        if (httpRequest != null) {
            HttpSession session = httpRequest.getSession(true);
            httpRequest.changeSessionId(); // Prevent session fixation attacks
            if (httpResponse != null) {
                securityContextRepository.saveContext(context, httpRequest, httpResponse);
            }
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password."));

        return UserResponse.fromEntity(user);
    }

    @Override
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (httpRequest != null && httpResponse != null && auth != null) {
            new SecurityContextLogoutHandler().logout(httpRequest, httpResponse, auth);
        }
        SecurityContextHolder.clearContext();
        if (httpRequest != null) {
            HttpSession session = httpRequest.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        }
        if (httpResponse != null) {
            Cookie cookie = new Cookie("JSESSIONID", "");
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setMaxAge(0);
            httpResponse.addCookie(cookie);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new InsufficientAuthenticationException("User is not authenticated.");
        }
        String email = authentication.getName();
        if (email == null || email.trim().isEmpty()) {
            throw new InsufficientAuthenticationException("User identity could not be verified.");
        }
        User user = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResourceNotFoundException("User account not found: " + email));

        if (user.getStatus() == AccountStatus.SUSPENDED) {
            throw new LockedException("Account is suspended.");
        }
        if (user.getStatus() == AccountStatus.INACTIVE) {
            throw new DisabledException("Account is inactive.");
        }

        return UserResponse.fromEntity(user);
    }
}
