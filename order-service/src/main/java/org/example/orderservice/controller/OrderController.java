package org.example.orderservice.controller;

import org.example.orderservice.dto.CreateOrderRequest;
import org.example.orderservice.dto.CreateOrderResponse;
import org.example.orderservice.entity.OrderInfo;
import org.example.orderservice.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {
    @Autowired
    private OrderService orderService;
    @PostMapping
    public CreateOrderResponse createOrder(@RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }
    /**
     * 主动取消订单。
     *
     * 示例：
     * POST /orders/311/cancel?userId=94001
     */
    @PostMapping("/{id}/cancel")
    public CreateOrderResponse cancelOrder(
            @PathVariable("id") Long orderId,
            @RequestParam("userId") Long userId) {

        return orderService.cancelOrder(orderId, userId);
    }
    @GetMapping("/{id}")
    public OrderInfo getOrder(@PathVariable("id") Long id) {
        return orderService.getOrder(id);
    }
}
