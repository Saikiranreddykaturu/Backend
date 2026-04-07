package com.ecommerce.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class OrderResponseDTO {

    private UUID orderId;
    private Instant createdAt;
    private String status;
    private List<OrderItemDTO> items;
    private BigDecimal totalAmount;
}