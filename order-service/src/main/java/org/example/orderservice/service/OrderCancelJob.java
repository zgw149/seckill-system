package org.example.orderservice.service;

import org.example.orderservice.entity.OrderInfo;
import org.example.orderservice.mapper.OrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class OrderCancelJob {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderService orderService;
    // 超过多少分钟视为待关闭订单
    @Value("${app.order.expire-minutes:15}")
    private int expireMinutes;
    // 是否启用自动超时关闭
    @Value("${app.order.expire-enable:false}")
    private boolean expireEnabled;
    @Scheduled(initialDelayString = "${app.order.cancel-initial-delay-ms:12000}",
            fixedDelayString = "${app.order.cancel-delay-ms:5000}")
    public void cancelExpiredAndRecover() {
        // 第一部分：自动关闭超时订单
        if (expireEnabled) {
            LocalDateTime cutoff = LocalDateTime.now().minusMinutes(expireMinutes);
            for (OrderInfo order : orderMapper.selectExpiredOpen(cutoff, 20)) {
                try {
                    orderService.cancelAndRecover(order);
                } catch (RuntimeException ex) {
                    throw (ex);
                }
            }
        }
        // 第二部分：恢复已经进入取消流程的订单
        for (OrderInfo order : orderMapper.selectCancelPending(20)) {
            try {
                orderService.reconcileCancellation(order.getId());
            } catch (RuntimeException ex) {
                throw(ex);
            }
        }
    }
}
