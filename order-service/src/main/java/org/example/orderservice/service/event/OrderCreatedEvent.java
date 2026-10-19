package org.example.orderservice.service.event;


import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderCreatedEvent(
        String eventId,
        Long orderId,
        String orderNo,
        Long userId,
        Long productId,
        String productName,
        Integer quantity,
        BigDecimal amount,
        String status,
        LocalDateTime createdTime
)
{
}
