package org.example.notificationservice.service;

import org.example.notificationservice.model.OrderEventMessage;
import org.example.notificationservice.repository.ConsumedMessageRepository;
import org.example.notificationservice.repository.NotificationOrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationEventService {
    @Autowired
    private ConsumedMessageRepository consumedRepository;
    @Autowired
    private NotificationOrderRepository orderRepository;
    @Value("${spring.kafka.consumer.group-id}")
    private String consumerGroup;
    /**
     * 返回 true：
     * 本次成功推进通知服务的订单状态。
     *
     * 返回 false：
     * 重复事件或已过时事件，正常确认消费即可。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean processEvent(OrderEventMessage event){
        // 1. 原子检查本事件是否已经消费成功
        boolean shouldProcess = consumedRepository.begin(event.eventId(), event.eventType(), consumerGroup);
        if(!shouldProcess){
            return false;
        }
        // 2. 推进订单状态并创建对应通知任务
        boolean applied = orderRepository.applyEvent(event);
        // 3. 所有数据库处理成功后才标记事件成功
        consumedRepository.markSuccess(event.eventId(), consumerGroup);
        return applied;
    }
}
