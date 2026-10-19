package org.example.notificationservice.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ConsumedMessageRepository {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 获取指定 eventId 的处理权限。
     *
     * 必须由带 @Transactional 的业务方法调用。
     *
     * true  = 需要处理
     * false = 已处理成功，直接跳过
     */
    public boolean begin(
            String eventId,
            String topic,
            String consumerGroup) {
        // 第一步：先保证数据库中存在这条消费记录。
        // 若唯一键冲突，执行无实际变化的 UPDATE。
        jdbcTemplate.update("""
            INSERT INTO consumed_message (
                event_id,
                topic,
                consumer_group,
                status,
                error_message,
                created_time,
                updated_time
            )
            VALUES (
                ?, ?, ?, 'PROCESSING', NULL, NOW(), NOW()
            )
            ON DUPLICATE KEY UPDATE
                event_id = event_id
            """,
                eventId,
                topic,
                consumerGroup
        );
        // 第二步：对本事件加数据库行锁
        String status = jdbcTemplate.queryForObject("""
            SELECT status
            FROM consumed_message
            WHERE event_id = ?
              AND consumer_group = ?
            FOR UPDATE
            """,
                String.class,
                eventId,
                consumerGroup
        );
        // 已经成功处理过，直接跳过
        return !"SUCCESS".equals(status);
    }
    /**
     * 标记本事件已经完成所有数据库业务处理。
     *
     * 与业务数据更新必须在同一个事务内。
     */
    public void markSuccess(
            String eventId,
            String consumerGroup) {
        int rows = jdbcTemplate.update("""
            UPDATE consumed_message
            SET status = 'SUCCESS',
                error_message = NULL,
                updated_time = NOW()
            WHERE event_id = ?
              AND consumer_group = ?
            """,
                eventId,
                consumerGroup
        );
        if (rows != 1) {
            throw new IllegalStateException(
                    "更新消费成功状态失败，eventId=" + eventId
            );
        }
    }
}
