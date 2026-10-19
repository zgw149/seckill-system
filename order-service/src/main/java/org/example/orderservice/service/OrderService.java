package org.example.orderservice.service;

import org.example.orderservice.client.ProductClient;
import org.example.orderservice.dto.*;
import org.example.orderservice.entity.OrderInfo;
import org.example.orderservice.exception.BusinessException;
import org.example.orderservice.mapper.OrderMapper;
import org.example.orderservice.service.outbox.OrderOutboxService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class OrderService {
    private static final String ORDER_LOCK_KEY_PREFIX = "lock:order:";
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private ProductClient productClient;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private OrderOutboxService orderOutboxService;
    @Autowired
    private OrderTransactionService transactions;

    @Transactional
    public CreateOrderResponse createOrder(CreateOrderRequest request) {
        checkCreateOrderRequest(request);
//        String lockKey = buildOrderLockKey(request.productId(), request.userId());
//        RLock lock = redissonClient.getLock(lockKey);
//        try {
//            locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
//            if (!locked) {
//                throw new BusinessException("系统繁忙，请稍后重试");
//            }
//            return doCreateOrder(request);
//        } catch (Exception e) {
//            throw new BusinessException("下单被中断，请稍后重试");
//        } finally {
//            if (locked) {
//                try {
//                    // 先清除当前线程的中断标记，避免 Redisson unlock 内部 get() 直接抛 InterruptedException
//                    Thread.interrupted();
//                    lock.unlock();
//                } catch (IllegalMonitorStateException e) {
//                    System.out.println("释放锁失败，当前线程不持有锁：" + lockKey);
//                } catch (Exception e) {
//                    System.out.println("释放锁异常：" + e.getMessage());
//                }
//            }
//        }
        OrderInfo order = orderMapper.selectByRequestNo(request.requestNo());
        if(order==null){
            ProductDTO product = productClient.getProduct(request.productId());
            try{
                order = transactions.createPending(
                        request,product
                );
            }catch(DuplicateKeyException e){
                order = orderMapper.selectByRequestNo(request.requestNo());
                if(order==null){
                    throw new BusinessException("下单失败，请稍后重试");
                }
            }
        }
        ensureSameBusinessRequest(order, request);
        if ("PENDING_STOCK".equals(order.getStatus())) {
            try{
                reconcile(order);
            }catch (RuntimeException e){

            }
        }
        OrderInfo latest = orderMapper.selectById(order.getId());
        return new CreateOrderResponse(
                latest.getId(),
                latest.getOrderNo(),
                latest.getStatus()
        );
    }

    public void reconcile(OrderInfo order) {
        OrderInfo latest = orderMapper.selectById(order.getId());
        if (latest == null) {
            return;
        }
        if (!"PENDING_STOCK".equals(latest.getStatus())) {
            return;
        }
        ReserveStockRequest reserveRequest = new ReserveStockRequest(latest.getOrderNo(),latest.getQuantity());
        org.example.productservice.dto.StockOperationResponse result = productClient.reserveStock(latest.getProductId(),reserveRequest);
        if (result == null || result.status() == null) {
            throw new IllegalStateException("商品服务没有返回明确库存状态");
        }
        switch (result.status()){
            case "RESERVED" -> {
                if (!result.success()) {
                    throw new IllegalStateException(
                            "商品服务返回矛盾的 RESERVED 结果"
                    );
                }
                // 本地事务：CREATED + Outbox
                transactions.markCreated(latest);
            }
            case "REJECTED" -> {
                if (result.success()) {
                    throw new IllegalStateException(
                            "商品服务返回矛盾的 REJECTED 结果"
                    );
                }
                // 库存明确不足
                transactions.markFailed(latest.getId());
            }
            case "RELEASED" -> {
                // 在本阶段，RELEASED 不允许重新创建订单
                transactions.markFailed(latest.getId());
            }
            default -> throw new IllegalStateException(
                    "未知库存状态: " + result.status()
            );
        }
    }
    public CreateOrderResponse cancelOrder(Long orderId,Long userId){
        if(userId==null||userId<=0||orderId==null||orderId<=0){
            throw new BusinessException("订单ID或用户ID非法");
        }
        OrderInfo order = getOrder(orderId);
        if (!userId.equals(order.getUserId())) {
            throw new BusinessException("不能取消其他用户的订单");
        }
        if ("FAILED".equals(order.getStatus())) {
            throw new BusinessException("订单已经失败，无需取消");
        }
        if ("CANCELLED".equals(order.getStatus())) {
            return new CreateOrderResponse(
                    order.getId(),
                    order.getOrderNo(),
                    order.getStatus()
            );
        }
        cancelAndRecover(order);
        OrderInfo latest = getOrder(orderId);
        return new CreateOrderResponse(latest.getId(),latest.getOrderNo(),latest.getStatus());
    }
    /**
     * 将订单推进到取消流程。
     *
     * 先写订单库，再调用商品服务。
     * 该方法不使用跨服务 @Transactional。
     */
    public void cancelAndRecover(OrderInfo snapshot) {
        // 第一步：重新查询数据库状态
        OrderInfo latest = orderMapper.selectById(snapshot.getId());
        if (latest == null) {
            return;
        }
        // 已结束的订单无需重复处理
        if ("CANCELLED".equals(latest.getStatus()) || "FAILED".equals(latest.getStatus())) {
            return;
        }
        // 第二步：先持久化取消意图
        if ("PENDING_STOCK".equals(latest.getStatus()) || "CREATED".equals(latest.getStatus())) {
            int changed = transactions.beginCancellation(latest.getId());
        }
        // 第三步：检查是否确实进入取消状态
        OrderInfo current =orderMapper.selectById(latest.getId());
        if (current == null || !"CANCEL_PENDING".equals(current.getStatus())) {
            return;
        }
        // 第四步：远程取消库存
        try {
            reconcileCancellation(current.getId());
        } catch (RuntimeException ex) {
            // 不能把未知的远程结果当作取消完成
            // CANCEL_PENDING 留在数据库中等待恢复
            throw ex;
        }
    }
    /**
     * 对 CANCEL_PENDING 订单执行库存取消协调。
     *
     * 可被用户请求调用，也可被后台恢复任务重复调用。
     */
    public void reconcileCancellation(Long orderId) {
        // 第一步：重新读取订单
        OrderInfo current = orderMapper.selectById(orderId);
        if (current == null || !"CANCEL_PENDING".equals(current.getStatus())) {
            return;
        }
        // 第二步：调用商品服务取消接口
        StockOperationResponse result = productClient.cancelReservation(current.getProductId(), new ReserveStockRequest(current.getOrderNo(), current.getQuantity()));
        // 第三步：检查商品服务响应
        if (result == null || result.status() == null) {
            throw new IllegalStateException("商品服务取消结果未知");
        }
        // 第四步：只有商品服务确认取消成功才完成订单取消
        if (result.success() && ("CANCELLED".equals(result.status()) || "RELEASED".equals(result.status()))) {
            int changed = transactions.finishCancellation(orderId);
            return;
        }
        // 第五步：不明确的结果继续保留 CANCEL_PENDING
        throw new IllegalStateException("库存取消尚未完成，status=" + result.status());
    }
    public OrderInfo getOrder(Long id) {
        OrderInfo orderInfo = orderMapper.selectById(id);
        if (orderInfo == null) {
            throw new IllegalArgumentException("订单不存在，orderId=" + id);
        }
        return orderInfo;
    }
    private void ensureSameBusinessRequest(OrderInfo order, CreateOrderRequest request) {
        if (!order.getUserId().equals(request.userId())
                || !order.getProductId().equals(request.productId())
                || !order.getQuantity().equals(request.quantity())) {
            throw new BusinessException("requestNo 不允许对应不同用户、商品或数量");
        }
    }

    private void checkCreateOrderRequest(CreateOrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        if (request.userId() == null || request.userId() <= 0) {
            throw new IllegalArgumentException("userId不合法");
        }
        if (request.productId() == null || request.productId() <= 0) {
            throw new IllegalArgumentException("productId不合法");
        }
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new IllegalArgumentException("quantity必须大于0");
        }
    }
}
