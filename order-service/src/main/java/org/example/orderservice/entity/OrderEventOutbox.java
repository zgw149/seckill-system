package org.example.orderservice.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrderEventOutbox {

    private Long id;

    private String eventId;

    private String topic;

    private String eventKey;

    private String payload;

    // NEW / SENDING / FAILED / SENT / DEAD
    private String status;

    private Integer retryCount;

    private String errorMessage;

    private String claimToken;

    private LocalDateTime leaseUntil;

    private LocalDateTime nextRetryAt;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}