package org.example.orderservice.dto;

public record CreateOrderResponse(
        Long orderId,
        String orderNo,
        String status
) {
}