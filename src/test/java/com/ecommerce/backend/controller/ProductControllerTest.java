package com.ecommerce.backend.controller;

import com.ecommerce.backend.dto.ProductResponseDTO;
import com.ecommerce.backend.exception.GlobalExceptionHandler;
import com.ecommerce.backend.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductService productService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProductController(productService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void partialUpdateReturnsValidationErrorForNegativeStock() throws Exception {
        UUID productId = UUID.randomUUID();
        String body = """
                {
                  "stock": -1
                }
                """;

        mockMvc.perform(patch("/products/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.stock").value("Stock cannot be negative"));
    }

    @Test
    void deleteInvokesServiceAndReturnsSuccessMessage() throws Exception {
        UUID productId = UUID.randomUUID();

        mockMvc.perform(delete("/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("Product deleted Successfully"));

        verify(productService).deleteProductById(productId);
    }
}
