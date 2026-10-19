package org.example.orderservice.mapper;

import org.apache.ibatis.annotations.*;
import org.example.orderservice.entity.OrderInfo;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper {
    @Insert("""
            INSERT INTO order_info (
                order_no,
                request_no,
                user_id,
                product_id,
                product_name,
                quantity,
                amount,
                status,
                created_time,
                updated_time
            )
            VALUES (
                #{orderNo},
                #{requestNo},
                #{userId},
                #{productId},
                #{productName},
                #{quantity},
                #{amount},
                #{status},
                NOW(),
                NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OrderInfo orderInfo);

    @Select("""
            SELECT id,
                   order_no,
                   request_no,
                   user_id,
                   product_id,
                   product_name,
                   quantity,
                   amount,
                   status,
                   created_time,
                   updated_time
            FROM order_info
            WHERE id = #{id}
            """)
    OrderInfo selectById(@Param("id") Long id);

    // 3. 根据请求编号查询订单
    @Select("""
        SELECT
            id,
            order_no AS orderNo,
            request_no AS requestNo,
            user_id AS userId,
            product_id AS productId,
            product_name AS productName,
            quantity,
            amount,
            status,
            created_time AS createdTime,
            updated_time AS updatedTime
        FROM order_info
        WHERE request_no = #{no}
        """)
    OrderInfo selectByRequestNo(
            @Param("no") String no
    );

    // 4. 扫描等待库存确认的订单
    @Select("""
        SELECT
            id,
            order_no AS orderNo,
            request_no AS requestNo,
            user_id AS userId,
            product_id AS productId,
            product_name AS productName,
            quantity,
            amount,
            status,
            created_time AS createdTime,
            updated_time AS updatedTime
        FROM order_info
        WHERE status = 'PENDING_STOCK'
          AND updated_time < DATE_SUB(
              NOW(), INTERVAL 5 SECOND
          )
        ORDER BY id ASC
        LIMIT #{limit}
        """)
    List<OrderInfo> selectPending(
            @Param("limit") int limit
    );
    // 5. 原子条件更新订单状态
    @Update("""
        UPDATE order_info
        SET status = #{target},
            updated_time = NOW()
        WHERE id = #{id}
          AND status = 'PENDING_STOCK'
        """)
    int transitionPending(
            @Param("id") Long id,
            @Param("target") String target
    );

    @Select("""
            SELECT COUNT(1)
            FROM order_info
            WHERE user_id = #{userId}
              AND product_id = #{productId}
            """)
    int countByUserIdAndProductId(
            @Param("userId") Long userId,
            @Param("productId") Long productId
    );
    /**
     * 仅允许待库存确认或已创建的订单进入取消流程。
     */
    @Update("""
    UPDATE order_info
    SET status = 'CANCEL_PENDING',
        updated_time = NOW()
    WHERE id = #{orderId}
      AND status IN ('PENDING_STOCK', 'CREATED')
    """)
    int transitionToCancelPending(
            @Param("orderId") Long orderId
    );
    /**
     * 商品服务确认取消成功后，才允许最终确认订单。
     */
    @Update("""
    UPDATE order_info
    SET status = 'CANCELLED',
        updated_time = NOW()
    WHERE id = #{orderId}
      AND status = 'CANCEL_PENDING'
    """)
    int transitionToCancelled(
            @Param("orderId") Long orderId
    );
    @Select("""
    SELECT
        id,
        order_no AS orderNo,
        request_no AS requestNo,
        user_id AS userId,
        product_id AS productId,
        product_name AS productName,
        quantity,
        amount,
        status,
        created_time AS createdTime,
        updated_time AS updatedTime
    FROM order_info
    WHERE status = 'CANCEL_PENDING'
    ORDER BY updated_time ASC, id ASC
    LIMIT #{limit}
    """)
    List<OrderInfo> selectCancelPending(
            @Param("limit") int limit
    );
    @Select("""
    SELECT
        id,
        order_no AS orderNo,
        request_no AS requestNo,
        user_id AS userId,
        product_id AS productId,
        product_name AS productName,
        quantity,
        amount,
        status,
        created_time AS createdTime,
        updated_time AS updatedTime
    FROM order_info
    WHERE status IN ('PENDING_STOCK', 'CREATED')
      AND created_time < #{cutoff}
    ORDER BY created_time ASC, id ASC
    LIMIT #{limit}
    """)
    List<OrderInfo> selectExpiredOpen(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit
    );
}
