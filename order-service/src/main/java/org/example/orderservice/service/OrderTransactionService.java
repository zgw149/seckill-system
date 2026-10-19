package org.example.orderservice.service;

import org.example.orderservice.dto.CreateOrderRequest;
import org.example.orderservice.dto.ProductDTO;
import org.example.orderservice.entity.OrderInfo;
import org.example.orderservice.exception.BusinessException;
import org.example.orderservice.mapper.OrderMapper;
import org.example.orderservice.mapper.OrderOutboxMapper;
import org.example.orderservice.service.outbox.OrderOutboxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class OrderTransactionService {
    @Autowired
    private OrderMapper mapper;
    @Autowired
    private OrderOutboxService orderOutboxService;
    @Transactional
    public OrderInfo createPending(CreateOrderRequest request, ProductDTO product){
        OrderInfo existing = mapper.selectByRequestNo(request.requestNo());
        if(existing != null){
            return existing;
        }
        if (product == null || product.id() == null || !product.id().equals(request.productId()) || product.price() == null) {
            throw new BusinessException("商品不存在或价格无效");
        }
        OrderInfo order = new OrderInfo();
        order.setOrderNo(
                "ORDER-" + UUID.randomUUID().toString().replace("-", "")
        );
        order.setRequestNo(request.requestNo());
        order.setUserId(request.userId());
        order.setProductId(request.productId());
        order.setProductName(product.name());
        order.setQuantity(request.quantity());
        // 订单总金额 = 商品单价 × 数量
        order.setAmount(
                product.price().multiply(
                        BigDecimal.valueOf(request.quantity())
                )
        );
        order.setStatus("PENDING_STOCK");
        int rows = mapper.insert(order);
        if(rows!=1){
            throw new BusinessException("创建订单失败");
        }
        return order;
    }
    /**
     * 第一步：
     * 在 order-service 自己的 MySQL 事务中
     * 提交 CANCEL_PENDING 取消意图。
     */
    @Transactional
    public int beginCancellation(Long orderId) {

        return mapper.transitionToCancelPending(orderId);
    }
    /**
     * 第三步：
     * 商品服务明确确认取消成功后，
     * 将订单状态最终转为 CANCELLED。
     */
    @Transactional(rollbackFor = Exception.class)
    public int finishCancellation(Long orderId) {
        int changed = mapper.transitionToCancelled(orderId);
        if (changed==0){
            return 0;
        }
        OrderInfo order = mapper.selectById(orderId);
        orderOutboxService.saveOrderCancelledEvent(order);
        return 1;
    }
    @Transactional
    public void markCreated(OrderInfo order){
        int changed = mapper.transitionPending(order.getId(),"CREATED");
        if(changed!=1){
            return;
        }
        order.setStatus("CREATED");
        orderOutboxService.saveOrderCreatedEvent(order);
    }
    @Transactional
    public void markFailed(Long orderId){
        mapper.transitionPending(orderId,"FAILED");
    }
}
