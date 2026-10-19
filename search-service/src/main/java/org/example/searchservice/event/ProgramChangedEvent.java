package org.example.searchservice.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProgramChangedEvent(
        String eventId,
        String eventType,
        Long programId,
        String title,
        String city,
        String category,
        String venue,
        String description,
        BigDecimal minPrice,
        LocalDateTime startTime,
        Integer heat,
        LocalDateTime eventTime
) {
}