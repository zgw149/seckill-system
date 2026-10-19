package org.example.productservice.dto;

/** status: RESERVED / RELEASED / REJECTED / NOT_FOUND。 */
public record StockOperationResponse(boolean success, String status, String message) { }
