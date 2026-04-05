package com.ecommerce.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CartResponseDTO {

    private UUID cartId;
    private UUID userId;
    private List<CartItemDTO> items;
    private BigDecimal totalPrice;
}