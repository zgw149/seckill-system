package org.example.productservice.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Program {
    private Long id;

    private String title;

    private String city;

    private String category;

    private String venue;

    private String description;

    private BigDecimal minPrice;

    private Integer stock;

    private Integer heat;

    private LocalDateTime startTime;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
