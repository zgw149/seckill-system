package org.example.productservice.service;

import org.example.productservice.dto.ReserveStockRequest;
import org.example.productservice.dto.StockOperationResponse;
import org.example.productservice.entity.StockReservation;
import org.example.productservice.mapper.ProductMapper;
import org.example.productservice.mapper.StockReservationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StockReservationService {
    private static final Logger log = LoggerFactory.getLogger(StockReservationService.class);
    private static final String PRODUCT_CACHE_KEY_PREFIX = "cache:product:";
    private final StockReservationMapper reservationMapper;
    private final ProductMapper productMapper;
    private final StringRedisTemplate redis;

    public StockReservationService(StockReservationMapper reservationMapper,
                                   ProductMapper productMapper,
                                   StringRedisTemplate redis) {
        this.reservationMapper = reservationMapper;
        this.productMapper = productMapper;
        this.redis = redis;
    }

    /** 保留库存：预占日志与库存扣减必须位于 product-service 的同一个 MySQL 事务中。 */
    @Transactional
    public StockOperationResponse reserve(Long productId, ReserveStockRequest request) {
        if (productId == null || productId <= 0 || request == null
                || request.quantity() == null || request.quantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品或数量非法");
        }
        String no = requireReservationNo(request.reservationNo());
        StockReservation old = reservationMapper.find(no);
        if (old != null) {
            if (!old.getProductId().equals(productId)
                    || !old.getQuantity().equals(request.quantity())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "预占号被不同参数复用");
            }
            return new StockOperationResponse("RESERVED".equals(old.getStatus()),
                    old.getStatus(), "重复请求，返回已有结果；已释放或已拒绝不能再次预占");
        }
        // UNIQUE(reservation_no) 兜底；并发首请求冲突会返回 409，客户端按相同预占号重试。
        requireOne(reservationMapper.insert(no, productId, request.quantity()), "预占记录插入失败");
        int changed = productMapper.deductStock(productId, request.quantity());
        if (changed == 0) {
            requireOne(reservationMapper.transition(no, "INIT", "REJECTED"), "标记预占失败状态失败");
            return new StockOperationResponse(false, "REJECTED", "商品不存在或库存不足");
        }
        requireOne(reservationMapper.transition(no, "INIT", "RESERVED"), "更新预占状态失败");
        invalidateAfterCommit(productId);
        return new StockOperationResponse(true, "RESERVED", "库存预占成功");
    }

    /** 释放库存：行锁序列化并发释放；库存归还和状态变更在同一个本地事务中。 */
    @Transactional
    public StockOperationResponse release(String reservationNo) {
        String no = requireReservationNo(reservationNo);
        StockReservation item = reservationMapper.findForUpdate(no);
        if (item == null) {
            return new StockOperationResponse(false, "NOT_FOUND", "预占记录不存在，不能盲目加库存");
        }
        if ("RELEASED".equals(item.getStatus())) {
            return new StockOperationResponse(true, "RELEASED", "已释放，无须重复操作");
        }
        if ("REJECTED".equals(item.getStatus())) {
            return new StockOperationResponse(false, "REJECTED", "原请求未预占成功，无须归还库存");
        }
        if (!"RESERVED".equals(item.getStatus())) {
            throw new IllegalStateException("预占状态异常: " + item.getStatus());
        }
        requireOne(productMapper.restoreStock(item.getProductId(), item.getQuantity()), "归还库存失败");
        requireOne(reservationMapper.transition(no, "RESERVED", "RELEASED"), "记录释放状态失败");
        invalidateAfterCommit(item.getProductId());
        return new StockOperationResponse(true, "RELEASED", "库存释放成功");
    }
    @Transactional
    public StockOperationResponse cancel(Long productId,ReserveStockRequest request){
        if (productId == null || productId <= 0 || request == null
                || request.quantity() == null || request.quantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品或数量非法");
        }
        String no = requireReservationNo(request.reservationNo());
        StockReservation old = reservationMapper.findForUpdate(no);
        if (old == null) {
            int rows = reservationMapper.insertCancelled(no,productId,request.quantity());
            requireOne(rows,"创建取消墓碑失败");
            return new StockOperationResponse(true, "CANCELLED", "取消记录已创建，阻止迟到的库存预占");
        }
        if(!productId.equals(old.getProductId())||!request.quantity().equals(old.getQuantity())){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "预占号被不同参数复用");
        }
        // 第五步：按照已有预占状态处理
        switch (old.getStatus()) {
            // 已经取消或释放：不能再次增加库存
            case "CANCELLED", "RELEASED" -> {
                return new StockOperationResponse(true, old.getStatus(), "该库存预占已经取消或释放");
            }
            // 原来的预占被拒绝，库存没有减少
            case "REJECTED" -> {
                int changed = reservationMapper.transition(request.reservationNo(), "REJECTED", "CANCELLED");
                requireOne(changed, "取消失败预占记录失败");
                return new StockOperationResponse(true, "CANCELLED", "原预占未成功，无需归还库存");
            }
            // 原预占成功，需要真正归还库存
            case "RESERVED" -> {
                // 归还商品库存
                int restored = productMapper.restoreStock(productId, request.quantity());
                requireOne(restored, "归还商品库存失败");
                // 将预占状态改为 CANCELLED
                int changed = reservationMapper.transition(request.reservationNo(), "RESERVED", "CANCELLED");
                requireOne(changed, "更新取消状态失败");
                // 提交数据库事务后，清理商品缓存
                invalidateAfterCommit(productId);
                return new StockOperationResponse(true, "CANCELLED", "订单库存取消成功，库存已归还");
            }
            default -> throw new IllegalStateException("不允许取消的预占状态：" + old.getStatus());
        }
    }

    @Transactional(readOnly = true)
    public StockOperationResponse status(String reservationNo) {
        StockReservation item = reservationMapper.find(requireReservationNo(reservationNo));
        if (item == null) {
            return new StockOperationResponse(false, "NOT_FOUND", "预占记录不存在");
        }
        return new StockOperationResponse(true, item.getStatus(), "当前预占状态");
    }

    private String requireReservationNo(String no) {
        if (no == null || !no.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reservationNo 必须是1-64位字母、数字、下划线或短横线");
        }
        return no;
    }

    private void requireOne(int affected, String message) {
        if (affected != 1) throw new IllegalStateException(message + " affected=" + affected);
    }

    private void invalidateAfterCommit(Long productId) {
        Runnable invalidate = () -> {
            try {
                redis.delete(PRODUCT_CACHE_KEY_PREFIX + productId);
            } catch (RuntimeException ex) {
                // 事务已提交：不能通过抛异常伪装为事务回滚；需要日志、指标和后续缓存修复。
                log.error("库存事务已提交，但商品缓存删除失败: productId={}", productId, ex);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { invalidate.run(); }
            });
        } else {
            // 仅用于独立单元测试调用场景；在真实 Spring 服务中必须由事务代理调用。
            invalidate.run();
        }
    }
}
