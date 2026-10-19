package org.example.orderservice.service.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单取消领域事件。
 *
 * 只描述已经发生的业务事实，
 * 不负责真正向 Kafka 发送消息。
 */
public record OrderCancelledEvent(
        String eventId,
        Long orderId,
        String orderNo,
        Long userId,
        Long productId,
        String productName,
        Integer quantity,
        BigDecimal amount,
        String status,
        LocalDateTime occurredAt
) {
}