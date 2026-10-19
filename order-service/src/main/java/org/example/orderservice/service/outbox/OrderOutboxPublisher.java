package org.example.orderservice.service.outbox;

import org.example.orderservice.entity.OrderEventOutbox;
import org.example.orderservice.mapper.OrderOutboxMapper;
import org.mybatis.logging.Logger;
import org.mybatis.logging.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class OrderOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OrderOutboxPublisher.class);
    @Value("${app.outbox.batch-size:20}")
    private  int batchSize;
    @Value("${app.outbox.max-attempts:3}")
    private  int maxAttempts;
    @Value("${app.outbox.lease-seconds:90}")
    private  int leaseSeconds;
    @Value("${app.outbox.ack-timeout-seconds:10}")
    private  int ackTimeoutSeconds;
    @Autowired
    private OrderOutboxMapper orderOutboxMapper;
    @Autowired
    private KafkaTemplate<String,String> kafkaTemplate;

    @Scheduled(initialDelayString =
            "${app.outbox.initial-delay-ms:5000}",
            fixedDelayString =
                    "${app.outbox.poll-ms:3000}")
    public void publishPendingEvents(){
        List<OrderEventOutbox> candidates;
        try{
            int retired = orderOutboxMapper.retireExhausted(maxAttempts);
            candidates = orderOutboxMapper.selectCandidates(batchSize,maxAttempts);
        }catch(RuntimeException ex){
            return;
        }
        if (candidates.isEmpty()) {
            return;
        }
        for(OrderEventOutbox event:candidates){
            if(Thread.currentThread().isInterrupted()){
                return;
            }
            try {
                publishOne(event);
            }catch (RuntimeException e){

            }
        }
    }
    private void publishOne(OrderEventOutbox event){
        Long id = event.getId();
        String token = UUID.randomUUID().toString().replace("-","");
        int claimed = orderOutboxMapper.claim(id,token,leaseSeconds,maxAttempts);
        if(claimed != 1){
            return;
        }
        OrderEventOutbox claimedEvent = orderOutboxMapper.selectClaimed(id,token);
        if(claimedEvent == null){
            return;
        }
        try{
            kafkaTemplate.send(claimedEvent.getTopic(),claimedEvent.getEventKey(),claimedEvent.getPayload())
                    .get(ackTimeoutSeconds, TimeUnit.SECONDS);
        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
            return;
        }catch (Exception ex) {
            // Kafka 明确报错或确认超时
            handleSendFailure(event, token, ex);
            return;
        }
        try{
            int updated = orderOutboxMapper.markSent(id, token);
            if(updated ==1){
                System.out.println("Outbox 发送确认完成，eventId={}, attempt={}");
            }else {
                System.out.println("Outbox 已获得 Kafka 确认，但状态更新未成功");
            }
        }catch (RuntimeException e){
            /*
             * Kafka 已确认，但数据库更新失败。
             *
             * 不能假装消息没有发送。
             * 保留 SENDING，等待租约过期后重试。
             *
             * 再次投递可能产生重复消息。
             */
            System.out.println("Outbox Kafka 已确认，但标记 SENT 失败，等待恢复");
        }
    }
    /**
     * Kafka 发送异常后的重试或终止处理。
     */
    private void handleSendFailure(OrderEventOutbox event, String token, Exception e) {
        int attempts = event.getRetryCount();
        String errorMessage = buildErrorMessage(e);
        if (attempts >= maxAttempts) {
            int changed = orderOutboxMapper.markDead(
                    event.getId(),
                    token,
                    errorMessage
            );
            System.out.println("Outbox 发送尝试耗尽");
            return;
        }
        // 计算下次尝试的退避时间
        int delaySeconds = calculateBackoff(attempts);
        int changed = orderOutboxMapper.markFailed(
                event.getId(),
                token,
                delaySeconds,
                errorMessage
        );
    }
    /**
     * 指数退避：
     *
     * 第一次失败：2 秒
     * 第二次失败：4 秒
     * 第三次失败：8 秒
     *
     * 最大不超过 60 秒。
     */
    private int calculateBackoff(int attempts) {
        int exponent = Math.min(
                Math.max(attempts - 1, 0),
                5);
        return Math.min(
                60,
                2 * (1 << exponent));
    }
    private String buildErrorMessage(Exception ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getClass().getSimpleName() + ": " + cause.getMessage();
        if (message.length() > 900) {
            return message.substring(0, 900);
        }
        return message;
    }
}
