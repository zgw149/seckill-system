package org.example.productservice.dto;

/** 同一笔业务重试必须使用同一个 reservationNo。 */
public record ReserveStockRequest(String reservationNo, Integer quantity) { }
