package com.ecommerce.backend.service;

import com.ecommerce.backend.config.JwtUtil;
import com.ecommerce.backend.dto.AuthResponseDTO;
import com.ecommerce.backend.dto.LoginRequestDTO;
import com.ecommerce.backend.dto.UserRequestDTO;
import com.ecommerce.backend.dto.UserResponseDTO;
import com.ecommerce.backend.entity.RefreshToken;
import com.ecommerce.backend.entity.Role;
import com.ecommerce.backend.entity.User;
import com.ecommerce.backend.exception.InvalidCredentialsException;
import com.ecommerce.backend.exception.ResourceAlreadyExistsException;
import com.ecommerce.backend.exception.ResourceNotFoundException;
import com.ecommerce.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtUtil jwtUtil;

    private final RefreshTokenService refreshTokenService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.refreshTokenService=refreshTokenService;
    }

    public AuthResponseDTO login(LoginRequestDTO request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid password");
        }

        String accessToken = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return AuthResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .build();
    }
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }


    public UserResponseDTO registerUser(UserRequestDTO request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ResourceAlreadyExistsException("Email is already registered");
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER) // 👈 default role
                .build();

        User savedUser = userRepository.save(user);
        logger.info("Registering user with email: {}", request.getEmail());

        return UserResponseDTO.builder()
                .id(savedUser.getId())
                .name(savedUser.getName())
                .email(savedUser.getEmail())
                .build();
    }


}


