package org.example.notificationservice.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.example.notificationservice.model.OrderEventMessage;
import org.example.notificationservice.repository.ConsumedMessageRepository;
import org.example.notificationservice.service.NotificationEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.kafka.annotation.KafkaListener;

@Component
public class OrderCreatedConsumer {
    private static final Logger log = LoggerFactory.getLogger(OrderCreatedConsumer.class);
    private static final String TOPIC = "order.created";
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private NotificationEventService eventService;

    @Value("${spring.kafka.consumer.group-id}")
    private String consumerGroup;

    @KafkaListener(
            topics = {
                    "order.created",
                    "order.cancelled"
            },
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(ConsumerRecord<String, String> record) throws Exception {
        // 1. 先解析消息
        OrderEventMessage event = parse(record);
        // 2. 在 Service 事务中处理
        boolean applied = eventService.processEvent(event);
        // 3. 方法正常返回，表示数据库处理已完成
        if (applied) {
            log.info("订单事件处理成功，eventId={}, type={}", event.eventId(), event.eventType());
        }else {
            log.info("重复或过时事件，跳过业务更新，eventId={}", event.eventId());
        }
    }
    /**
     * 解析并校验 JSON。
     *
     * 不使用 orderNo 代替 eventId。
     * 同一订单的 created 和 cancelled 是不同事件。
     */
    private OrderEventMessage parse(ConsumerRecord<String, String> record) throws Exception {
        JsonNode root = objectMapper.readTree(record.value());
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Kafka 消息必须是 JSON 对象");
        }
        String topic = record.topic();
        String expectedStatus = switch (topic) {
            case "order.created" -> "CREATED";
            case "order.cancelled" -> "CANCELLED";
            default -> throw new IllegalArgumentException("未知订单事件主题：" + topic);
        };
        String eventId = requiredText(root, "eventId", 64);
        String orderNo = requiredText(root, "orderNo", 64);
        String status = requiredText(root, "status", 32);
        String productName = requiredText(root, "productName", 128);
        Long userId = requiredLong(root, "userId");
        Integer quantity = requiredInt(root, "quantity");
        if (!expectedStatus.equals(status)) {
            throw new IllegalArgumentException("Topic 与订单事件状态不匹配，topic=" + topic + ", status=" + status);
        }
        if (userId <= 0 || quantity <= 0) {
            throw new IllegalArgumentException("userId 或 quantity 不合法");
        }
        if (record.key() != null && !record.key().equals(orderNo)) {
            throw new IllegalArgumentException("Kafka key 与订单编号不一致");
        }
        return new OrderEventMessage(
                eventId,
                orderNo,
                userId,
                productName,
                quantity,
                topic
        );
    }

    private String requiredText(JsonNode root, String field, int maxLength) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException("缺少合法文本字段：" + field);
        }
        String text = value.asText();
        if (text.isBlank() || text.length() > maxLength) {
            throw new IllegalArgumentException("文本字段内容非法：" + field);
        }
        return text;
    }

    private Long requiredLong(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new IllegalArgumentException("缺少合法整数字段：" + field);
        }
        return value.longValue();
    }

    private Integer requiredInt(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException("缺少合法整数字段：" + field);
        }
        return value.intValue();
    }
}
