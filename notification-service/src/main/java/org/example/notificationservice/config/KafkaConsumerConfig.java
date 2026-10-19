package org.example.notificationservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;

@Configuration
public class KafkaConsumerConfig {
    /**
     * 订单创建事件死信主题。
     *
     * 与原 order.created 保持相同分区数。
     */
    @Bean
    public NewTopic orderCreatedDlt() {
        return TopicBuilder.name("order.created.DLT").partitions(3).replicas(1).build();
    }
    /**
     * 订单取消事件死信主题。
     */
    @Bean
    public NewTopic orderCancelledDlt() {
        return TopicBuilder.name("order.cancelled.DLT").partitions(3).replicas(1).build();
    }

    /**
     * Kafka 消费失败处理器。
     *
     * 首次处理失败后，间隔 2 秒重试。
     * 最多额外重试 3 次。
     * 仍失败则转入 DLT。
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition())
                );
        // 死信发送失败时不能当作已恢复
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(Duration.ofSeconds(10));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 3L));
    }
}
