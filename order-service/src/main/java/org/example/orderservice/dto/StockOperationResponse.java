package org.example.orderservice.dto;

public record StockOperationResponse(boolean success,
                                     String status,
                                     String message
) {
}
