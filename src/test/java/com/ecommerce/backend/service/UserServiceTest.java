package com.ecommerce.backend.service;

import com.ecommerce.backend.config.JwtUtil;
import com.ecommerce.backend.dto.AuthResponseDTO;
import com.ecommerce.backend.dto.LoginRequestDTO;
import com.ecommerce.backend.dto.UserRequestDTO;
import com.ecommerce.backend.entity.RefreshToken;
import com.ecommerce.backend.entity.Role;
import com.ecommerce.backend.entity.User;
import com.ecommerce.backend.exception.InvalidCredentialsException;
import com.ecommerce.backend.exception.ResourceAlreadyExistsException;
import com.ecommerce.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private UserService userService;

    @Test
    void registerUserThrowsWhenEmailAlreadyExists() {
        UserRequestDTO request = new UserRequestDTO();
        request.setName("Alice");
        request.setEmail("alice@example.com");
        request.setPassword("secret");

        when(userRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(User.builder().build()));

        assertThrows(ResourceAlreadyExistsException.class, () -> userService.registerUser(request));
    }

    @Test
    void loginReturnsAccessAndRefreshTokensForValidCredentials() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail("alice@example.com");
        request.setPassword("secret");

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("alice@example.com")
                .password("encoded")
                .role(Role.USER)
                .build();
        RefreshToken refreshToken = RefreshToken.builder()
                .token("refresh-token")
                .expiryDate(Instant.now().plusSeconds(3600))
                .user(user)
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "encoded")).thenReturn(true);
        when(jwtUtil.generateToken("alice@example.com", "USER")).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(user)).thenReturn(refreshToken);

        AuthResponseDTO response = userService.login(request);

        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
    }

    @Test
    void loginThrowsWhenPasswordDoesNotMatch() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail("alice@example.com");
        request.setPassword("wrong");

        User user = User.builder()
                .email("alice@example.com")
                .password("encoded")
                .role(Role.USER)
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> userService.login(request));
        verify(refreshTokenService, never()).createRefreshToken(user);
    }
}
