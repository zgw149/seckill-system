package org.example.searchservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.searchservice.document.ProgramDocument;
import org.example.searchservice.event.ProgramChangedEvent;
import org.example.searchservice.repository.ProgramSearchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ProgramChangedConsumer {
    private static final String TOPIC = "program.changed";

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProgramSearchRepository programSearchRepository;

    @KafkaListener(topics = TOPIC,groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String message){
        try {
            ProgramChangedEvent event = objectMapper.readValue(
                    message,
                    ProgramChangedEvent.class
            );
            if ("DELETE".equalsIgnoreCase(event.eventType())) {
                programSearchRepository.deleteById(event.programId());
                System.out.println("ES 删除节目索引成功，programId=" + event.programId());
                return;
            }
            ProgramDocument document = new ProgramDocument(
                    event.programId(),
                    event.title(),
                    event.city(),
                    event.category(),
                    event.venue(),
                    event.description(),
                    event.minPrice(),
                    event.startTime(),
                    event.heat()
            );
            programSearchRepository.save(document);
            System.out.println("ES 同步节目索引成功，programId="
                    + event.programId()
                    + ", title="
                    + event.title());
        } catch (Exception e) {
            System.out.println("节目变更消息消费失败，message=" + message);
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
