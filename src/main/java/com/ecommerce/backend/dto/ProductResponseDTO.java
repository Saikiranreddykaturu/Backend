package com.ecommerce.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ProductResponseDTO {

    private UUID id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private Instant createdAt;
}