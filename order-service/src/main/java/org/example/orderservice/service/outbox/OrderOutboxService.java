package org.example.orderservice.service.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.orderservice.config.KafkaTopicConfig;
import org.example.orderservice.entity.OrderEventOutbox;
import org.example.orderservice.entity.OrderInfo;
import org.example.orderservice.mapper.OrderOutboxMapper;
import org.example.orderservice.service.event.OrderCancelledEvent;
import org.example.orderservice.service.event.OrderCreatedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class OrderOutboxService {
    @Autowired
    private OrderOutboxMapper orderOutboxMapper;
    @Autowired
    private ObjectMapper objectMapper;

    public void saveOrderCreatedEvent(OrderInfo orderInfo){
        String eventId = generateEventId();
        OrderCreatedEvent event = new OrderCreatedEvent(
                eventId,
                orderInfo.getId(),
                orderInfo.getOrderNo(),
                orderInfo.getUserId(),
                orderInfo.getProductId(),
                orderInfo.getProductName(),
                orderInfo.getQuantity(),
                orderInfo.getAmount(),
                orderInfo.getStatus(),
                LocalDateTime.now()
        );
        saveEvent(
                eventId,
                orderInfo.getOrderNo(),
                KafkaTopicConfig.ORDER_CREATED_TOPIC,
                event
        );
    }
    /**
     * 本阶段新增：
     * 保存订单取消事件。
     */
    public void saveOrderCancelledEvent(OrderInfo order){
        if(!"CANCELLED".equals(order.getStatus())){
            throw new IllegalStateException("订单状态不是已取消，无法保存事件");
        }
        String eventId = generateEventId();
        OrderCancelledEvent event = new OrderCancelledEvent(
                eventId,
                order.getId(),
                order.getOrderNo(),
                order.getUserId(),
                order.getProductId(),
                order.getProductName(),
                order.getQuantity(),
                order.getAmount(),
                order.getStatus(),
                LocalDateTime.now()
        );
        saveEvent(
                eventId,
                order.getOrderNo(),
                KafkaTopicConfig.ORDER_CANCELLED_TOPIC,
                event
        );
    }
    /**
     * 统一将业务事件写入 Outbox。
     *
     * 本方法只负责写数据库，
     * 不直接调用 Kafka。
     */
    private void saveEvent(
            String eventId,
            String orderNo,
            String topic,
            Object event) {
        try{
            String payload = objectMapper.writeValueAsString(event);
            OrderEventOutbox outbox = new OrderEventOutbox();
            outbox.setEventId(eventId);
            outbox.setTopic(topic);
            outbox.setEventKey(orderNo);
            outbox.setPayload(payload);
            // 新事件等待后台发布
            outbox.setStatus("NEW");
            outbox.setRetryCount(0);
            outbox.setErrorMessage(null);
            // 3. 写入订单数据库
            int rows = orderOutboxMapper.insert(outbox);
            if (rows != 1) {
                throw new IllegalStateException("Outbox 事件保存失败");
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("业务事件 JSON 序列化失败", e);
        }

    }

    /**
     * 每次新业务事件生成独立的 eventId。
     */
    private String generateEventId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
