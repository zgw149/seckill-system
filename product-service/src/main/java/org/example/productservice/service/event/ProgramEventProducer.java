package org.example.productservice.service.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.productservice.config.KafkaTopicConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

//kafka生产者
@Component
public class ProgramEventProducer {
    @Autowired
    private KafkaTemplate<String,String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;
    public void sendProgramChangedEvent(ProgramChangedEvent event){
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(KafkaTopicConfig.PROGRAM_CHANGED_TOPIC,
                    String.valueOf(event.programId()),
                    json
            ).whenComplete((result,exception)->{
                if(exception!=null){
                    System.out.println("发送消息失败："+event.programId());
                    exception.printStackTrace();
                    return;
                }
                System.out.println("节目变更消息发送成功，programId="
                        + event.programId()
                        + ", topic="
                        + result.getRecordMetadata().topic()
                        + ", partition="
                        + result.getRecordMetadata().partition()
                        + ", offset="
                        + result.getRecordMetadata().offset());
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("节目变更事件序列化失败", e);
        }
    }
}
