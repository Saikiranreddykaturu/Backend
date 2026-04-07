package com.ecommerce.backend.controller;

import com.ecommerce.backend.dto.LogoutRequestDTO;
import com.ecommerce.backend.dto.RefreshRequestDTO;
import com.ecommerce.backend.exception.InvalidCredentialsException;
import com.ecommerce.backend.exception.ResourceNotFoundException;
import com.ecommerce.backend.entity.RefreshToken;
import com.ecommerce.backend.service.RefreshTokenService;
import com.ecommerce.backend.config.JwtUtil;
import com.ecommerce.backend.repository.RefreshTokenRepository;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtUtil jwtUtil;

    public AuthController(RefreshTokenRepository refreshTokenRepository,
                          RefreshTokenService refreshTokenService,
                          JwtUtil jwtUtil) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenService = refreshTokenService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/refresh")
    public Map<String, String> refresh(@Valid @RequestBody RefreshRequestDTO request) {

        String requestToken = request.getRefreshToken();

        RefreshToken refreshToken = refreshTokenRepository.findByToken(requestToken)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        refreshTokenService.verifyExpiration(refreshToken);

        String newAccessToken = jwtUtil.generateToken(
                refreshToken.getUser().getEmail(),
                refreshToken.getUser().getRole().name()
        );

        return Map.of("accessToken", newAccessToken);
    }
    @PostMapping("/logout")
    public String logout(@Valid @RequestBody LogoutRequestDTO request,
                         Authentication authentication) {

        String email = authentication.getName();
        String refreshToken = request.getRefreshToken();

        RefreshToken token = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        if (!token.getUser().getEmail().equals(email)) {
            throw new ResourceNotFoundException("Refresh token not found for user");
        }

        refreshTokenRepository.delete(token);

        return "Logged out successfully";
    }
}
