package org.example.notificationservice.model;
/**
 * Kafka 订单事件解析后的内部数据结构。
 *
 * eventType:
 * order.created
 * order.cancelled
 */
public record OrderEventMessage(
        String eventId,
        String orderNo,
        Long userId,
        String productName,
        Integer quantity,
        String eventType
) {
}
