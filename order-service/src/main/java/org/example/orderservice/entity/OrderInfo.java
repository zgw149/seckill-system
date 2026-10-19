package org.example.orderservice.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderInfo {
    private Long id;
    private String orderNo;
    @TableField("request_no")
    private String requestNo;
    private Long userId;
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal amount;
    private String status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
