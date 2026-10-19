package org.example.notificationservice.repository;

import org.example.notificationservice.model.OrderEventMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationOrderRepository {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    /**
     * 根据消息更新通知服务的本地订单状态。
     *
     * 返回：
     * true  = 状态推进成功，并创建通知任务
     * false = 旧消息或重复业务状态，无须创建任务
     *
     * 必须由外层事务调用。
     */
    public boolean applyEvent(OrderEventMessage event) {
        int targetRank;
        String targetStatus;
        switch (event.eventType()) {
            case "order.created" -> {
                targetRank = 1;
                targetStatus = "CREATED";
            }
            case "order.cancelled" -> {
                targetRank = 2;
                targetStatus = "CANCELLED";
            }
            default -> throw new IllegalArgumentException(
                    "不支持的业务事件：" + event.eventType()
            );
        }
        // 1. 确保本地订单状态视图存在
        jdbcTemplate.update("""
            INSERT INTO notification_order_state (
                order_no,
                status,
                status_rank,
                last_event_id,
                created_time,
                updated_time
            )
            VALUES (?, 'UNKNOWN', 0, NULL, NOW(3), NOW(3))
            ON DUPLICATE KEY UPDATE
                order_no = order_no
            """,
                event.orderNo()
        );
        // 2. 只有优先级增加，才推进状态
        int changed = jdbcTemplate.update("""
            UPDATE notification_order_state
            SET status = ?,
                status_rank = ?,
                last_event_id = ?,
                updated_time = NOW(3)
            WHERE order_no = ?
              AND status_rank < ?
            """,
                targetStatus,
                targetRank,
                event.eventId(),
                event.orderNo(),
                targetRank
        );
        // 旧事件不能覆盖当前状态
        if (changed == 0) {
            return false;
        }
        // 3. 取消事件到达后，停止尚未发送的创建通知
        if ("order.cancelled".equals(event.eventType())) {
            jdbcTemplate.update("""
                UPDATE notification_task
                SET status = 'SUPPRESSED',
                    updated_time = NOW(3)
                WHERE order_no = ?
                  AND event_type = 'order.created'
                  AND status = 'PENDING'
                """,
                    event.orderNo()
            );
        }
        // 4. 生成通知内容
        String content;
        if ("order.created".equals(event.eventType())) {
            content = "订单创建成功："
                    + event.orderNo()
                    + "，商品："
                    + event.productName()
                    + "，数量："
                    + event.quantity();
        } else {
            content = "订单已经取消："
                    + event.orderNo()
                    + "，商品："
                    + event.productName();
        }
        // 5. 将待发送通知持久化
        // 同一订单同一事件类型最多生成一条任务
        jdbcTemplate.update("""
            INSERT INTO notification_task (
                order_no,
                event_id,
                event_type,
                user_id,
                content,
                status,
                created_time,
                updated_time
            )
            VALUES (?, ?, ?, ?, ?, 'PENDING', NOW(3), NOW(3))
            ON DUPLICATE KEY UPDATE
                id = id
            """,
                event.orderNo(),
                event.eventId(),
                event.eventType(),
                event.userId(),
                content
        );
        return true;
    }
}
