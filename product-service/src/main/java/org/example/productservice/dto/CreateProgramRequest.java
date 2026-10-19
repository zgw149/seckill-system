package org.example.productservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateProgramRequest(
        String title,
        String city,
        String category,
        String venue,
        String description,
        BigDecimal minPrice,
        Integer stock,
        Integer heat,
        LocalDateTime startTime
) {
}
