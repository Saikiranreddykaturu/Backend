package com.ecommerce.backend.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class UpdateQuantityDTO {
    private UUID productId;
    private Integer quantity;
}