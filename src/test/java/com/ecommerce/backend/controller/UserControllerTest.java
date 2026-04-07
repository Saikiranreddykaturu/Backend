package com.ecommerce.backend.controller;

import com.ecommerce.backend.dto.AuthResponseDTO;
import com.ecommerce.backend.dto.LoginRequestDTO;
import com.ecommerce.backend.dto.UserResponseDTO;
import com.ecommerce.backend.exception.GlobalExceptionHandler;
import com.ecommerce.backend.exception.ResourceAlreadyExistsException;
import com.ecommerce.backend.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void registerReturnsConflictWhenEmailAlreadyExists() throws Exception {
        when(userService.registerUser(any()))
                .thenThrow(new ResourceAlreadyExistsException("Email is already registered"));

        String body = """
                {
                  "name": "Alice",
                  "email": "alice@example.com",
                  "password": "secret123"
                }
                """;

        mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email is already registered"))
                .andExpect(jsonPath("$.path").value("/users/register"));
    }

    @Test
    void loginReturnsValidationErrorsForBlankEmail() throws Exception {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail("");
        request.setPassword("secret123");

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.validationErrors.email").value("Email is required"));
    }

    @Test
    void loginReturnsAuthTokensForValidRequest() throws Exception {
        when(userService.login(any())).thenReturn(AuthResponseDTO.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .build());

        String body = """
                {
                  "email": "alice@example.com",
                  "password": "secret123"
                }
                """;

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }
}
