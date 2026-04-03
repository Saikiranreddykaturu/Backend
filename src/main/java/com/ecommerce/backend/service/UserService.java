package com.ecommerce.backend.service;
import com.ecommerce.backend.entity.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ecommerce.backend.config.JwtUtil;
import com.ecommerce.backend.dto.UserRequestDTO;
import com.ecommerce.backend.dto.UserResponseDTO;
import com.ecommerce.backend.entity.User;
import com.ecommerce.backend.exception.InvalidCredentialsException;
import com.ecommerce.backend.exception.ResourceNotFoundException;
import com.ecommerce.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.ecommerce.backend.dto.LoginRequestDTO;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtUtil jwtUtil;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public String login(LoginRequestDTO request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            logger.error("Invalid password for email: {}", request.getEmail());
            throw new InvalidCredentialsException("Invalid password");
        }
        logger.info("Login attempt for email: {}", request.getEmail());
        return jwtUtil.generateToken(user.getEmail(), user.getRole().name());
    }
//    public UserResponseDTO login(LoginRequestDTO request) {
//
//        User user = userRepository.findByEmail(request.getEmail())
//                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
//
//        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
//            throw new InvalidCredentialsException("Invalid password");
//        }
//
//        return UserResponseDTO.builder()
//                .id(user.getId())
//                .name(user.getName())
//                .email(user.getEmail())
//                .build();
//    }

    public UserResponseDTO registerUser(UserRequestDTO request) {

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


