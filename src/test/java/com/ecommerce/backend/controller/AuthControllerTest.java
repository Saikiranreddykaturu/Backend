package com.ecommerce.backend.controller;

import com.ecommerce.backend.config.JwtUtil;
import com.ecommerce.backend.dto.LogoutRequestDTO;
import com.ecommerce.backend.dto.RefreshRequestDTO;
import com.ecommerce.backend.entity.RefreshToken;
import com.ecommerce.backend.entity.Role;
import com.ecommerce.backend.entity.User;
import com.ecommerce.backend.exception.InvalidCredentialsException;
import com.ecommerce.backend.exception.ResourceNotFoundException;
import com.ecommerce.backend.repository.RefreshTokenRepository;
import com.ecommerce.backend.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private JwtUtil jwtUtil;

    @Test
    void refreshThrowsWhenTokenIsUnknown() {
        AuthController authController = new AuthController(refreshTokenRepository, refreshTokenService, jwtUtil);
        RefreshRequestDTO request = new RefreshRequestDTO();
        request.setRefreshToken("missing-token");

        when(refreshTokenRepository.findByToken("missing-token")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authController.refresh(request));
    }

    @Test
    void logoutRejectsRefreshTokenFromAnotherUser() {
        AuthController authController = new AuthController(refreshTokenRepository, refreshTokenService, jwtUtil);
        User tokenOwner = User.builder()
                .email("bob@example.com")
                .role(Role.USER)
                .build();
        RefreshToken refreshToken = RefreshToken.builder()
                .token("refresh-token")
                .expiryDate(Instant.now().plusSeconds(3600))
                .user(tokenOwner)
                .build();

        when(refreshTokenRepository.findByToken("refresh-token"))
                .thenReturn(Optional.of(refreshToken));

        LogoutRequestDTO request = new LogoutRequestDTO();
        request.setRefreshToken("refresh-token");

        assertThrows(ResourceNotFoundException.class, () -> authController.logout(
                request,
                new UsernamePasswordAuthenticationToken("alice@example.com", null)
        ));
    }

    @Test
    void logoutDeletesTokenForAuthenticatedOwner() {
        AuthController authController = new AuthController(refreshTokenRepository, refreshTokenService, jwtUtil);
        User tokenOwner = User.builder()
                .email("alice@example.com")
                .role(Role.USER)
                .build();
        RefreshToken refreshToken = RefreshToken.builder()
                .token("refresh-token")
                .expiryDate(Instant.now().plusSeconds(3600))
                .user(tokenOwner)
                .build();

        when(refreshTokenRepository.findByToken("refresh-token"))
                .thenReturn(Optional.of(refreshToken));

        LogoutRequestDTO request = new LogoutRequestDTO();
        request.setRefreshToken("refresh-token");

        String response = authController.logout(
                request,
                new UsernamePasswordAuthenticationToken("alice@example.com", null)
        );

        assertEquals("Logged out successfully", response);
        verify(refreshTokenRepository).delete(refreshToken);
    }
}
