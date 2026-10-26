package com.campusfind.service;

import com.campusfind.dto.LoginRequest;
import com.campusfind.dto.RegisterRequest;
import com.campusfind.dto.UserResponse;
import com.campusfind.entity.User;
import com.campusfind.entity.enums.AccountStatus;
import com.campusfind.entity.enums.UserRole;
import com.campusfind.exception.EmailAlreadyExistsException;
import com.campusfind.exception.LoginRateLimitExceededException;
import com.campusfind.repository.UserRepository;
import com.campusfind.service.impl.AuthServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    private LoginAttemptService loginAttemptService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HttpSession session;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        loginAttemptService = new LoginAttemptService();
        authService = new AuthServiceImpl(
                userRepository,
                passwordEncoder,
                authenticationManager,
                loginAttemptService
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("register creates student user with encoded password and normalized email")
    void testRegisterSuccess() {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail("  Student.One@Example.COM  ");
        registerRequest.setPassword("SecretPassword123!");
        registerRequest.setFullName("Student One");

        when(userRepository.existsByEmail("student.one@example.com")).thenReturn(false);
        when(passwordEncoder.encode("SecretPassword123!")).thenReturn("$2a$12$hashedPasswordExample");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(10L);
            return u;
        });

        UserResponse userResponse = authService.register(registerRequest);

        assertThat(userResponse).isNotNull();
        assertThat(userResponse.getId()).isEqualTo(10L);
        assertThat(userResponse.getEmail()).isEqualTo("student.one@example.com");
        assertThat(userResponse.getFullName()).isEqualTo("Student One");
        assertThat(userResponse.getRole()).isEqualTo(UserRole.STUDENT);
        assertThat(userResponse.getStatus()).isEqualTo(AccountStatus.ACTIVE);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();
        assertThat(savedUser.getPasswordHash()).isEqualTo("$2a$12$hashedPasswordExample");
        assertThat(savedUser.getRole()).isEqualTo(UserRole.STUDENT);
        assertThat(savedUser.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("register throws EmailAlreadyExistsException if email exists")
    void testRegisterDuplicateEmailThrowsConflict() {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail("existing@example.com");
        registerRequest.setPassword("Pass1234");
        registerRequest.setFullName("Existing User");

        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("login succeeds, authenticates via AuthenticationManager, rotates session, and returns UserResponse")
    void testLoginSuccess() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("student@example.com");
        loginRequest.setPassword("CorrectPassword123");

        User user = new User();
        user.setId(1L);
        user.setEmail("student@example.com");
        user.setFullName("Test Student");
        user.setRole(UserRole.STUDENT);
        user.setStatus(AccountStatus.ACTIVE);

        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        Authentication authResult = new UsernamePasswordAuthenticationToken(
                "student@example.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );
        when(authenticationManager.authenticate(any())).thenReturn(authResult);
        when(userRepository.findByEmail("student@example.com")).thenReturn(Optional.of(user));
        when(request.getSession(true)).thenReturn(session);

        UserResponse userResponse = authService.login(loginRequest, request, response);

        assertThat(userResponse).isNotNull();
        assertThat(userResponse.getEmail()).isEqualTo("student@example.com");
        assertThat(loginAttemptService.isBlocked("127.0.0.1")).isFalse();
        verify(request).changeSessionId();
    }

    @Test
    @DisplayName("login throws LoginRateLimitExceededException when client key is blocked")
    void testLoginRateLimitExceeded() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("student@example.com");
        loginRequest.setPassword("password");

        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        // Simulate MAX_ATTEMPTS failures to trigger lockout threshold
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            loginAttemptService.recordFailure("192.168.1.100");
        }

        assertThatThrownBy(() -> authService.login(loginRequest, request, response))
                .isInstanceOf(LoginRateLimitExceededException.class);

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    @DisplayName("login records failure and throws BadCredentialsException on invalid credentials")
    void testLoginBadCredentials() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("student@example.com");
        loginRequest.setPassword("wrong-password");

        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(loginRequest, request, response))
                .isInstanceOf(BadCredentialsException.class);

        // Check that 1 failure was recorded in loginAttemptService
        assertThat(loginAttemptService.isBlocked("127.0.0.1")).isFalse();
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS - 1; i++) {
            loginAttemptService.recordFailure("127.0.0.1");
        }
        assertThat(loginAttemptService.isBlocked("127.0.0.1")).isTrue();
    }

    @Test
    @DisplayName("getCurrentUser returns safe UserResponse when authenticated")
    void testGetCurrentUserSuccess() {
        User user = new User();
        user.setId(2L);
        user.setEmail("user@example.com");
        user.setFullName("Valid User");
        user.setRole(UserRole.STUDENT);
        user.setStatus(AccountStatus.ACTIVE);

        Authentication auth = new UsernamePasswordAuthenticationToken(
                "user@example.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        UserResponse response = authService.getCurrentUser(auth);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(2L);
        assertThat(response.getEmail()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("getCurrentUser throws LockedException when account is suspended")
    void testGetCurrentUserSuspendedAccount() {
        User user = new User();
        user.setId(3L);
        user.setEmail("suspended@example.com");
        user.setStatus(AccountStatus.SUSPENDED);

        Authentication auth = new UsernamePasswordAuthenticationToken(
                "suspended@example.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );

        when(userRepository.findByEmail("suspended@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.getCurrentUser(auth))
                .isInstanceOf(LockedException.class)
                .hasMessageContaining("Account is suspended.");
    }

    @Test
    @DisplayName("getCurrentUser throws InsufficientAuthenticationException when unauthenticated")
    void testGetCurrentUserUnauthenticated() {
        assertThatThrownBy(() -> authService.getCurrentUser(null))
                .isInstanceOf(InsufficientAuthenticationException.class);
    }
}
