package org.example.orderservice.dto;

public record CreateOrderRequest(
        Long userId,
        Long productId,
        Integer quantity,
        String requestNo
) {
}