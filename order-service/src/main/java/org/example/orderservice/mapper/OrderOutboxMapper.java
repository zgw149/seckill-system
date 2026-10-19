package org.example.orderservice.mapper;

import org.apache.ibatis.annotations.*;
import org.example.orderservice.entity.OrderEventOutbox;

import java.util.List;

@Mapper
public interface OrderOutboxMapper {

    @Insert("""
            INSERT INTO order_event_outbox (
                event_id,
                topic,
                event_key,
                payload,
                status,
                retry_count,
                error_message,
                created_time,
                updated_time
            )
            VALUES (
                #{eventId},
                #{topic},
                #{eventKey},
                #{payload},
                #{status},
                #{retryCount},
                #{errorMessage},
                NOW(),
                NOW()
            )
            """)
    @Options(useGeneratedKeys = true,keyProperty = "id")
    int insert(OrderEventOutbox outbox);
    /**
     * 2. 查询可以领取的消息。
     *
     * NEW：尚未发送
     * FAILED：到了重试时间
     * SENDING：租约已经过期，可以恢复
     */
    @Select("""
            SELECT id,
                   event_id AS eventId,
                   topic,
                   event_key AS eventKey,
                   payload,
                   status,
                   retry_count AS retryCount,
                   error_message AS errorMessage,
                   claim_token AS claimToken,
                   lease_until AS leaseUntil,
                   next_retry_at AS nextRetryAt,
                   created_time AS createdTime,
                   updated_time AS updatedTime
            FROM order_event_outbox
            WHERE retry_count < #{maxAttempts}
          AND (
                status = 'NEW'
                OR (
                    status = 'FAILED'
                    AND (
                        next_retry_at IS NULL
                        OR next_retry_at <= NOW(3)
                    )
                )
                OR (
                    status = 'SENDING'
                    AND (
                        lease_until IS NULL
                        OR lease_until <= NOW(3)
                    )
                )
          )
        ORDER BY id ASC
        LIMIT #{limit}
        """)
    List<OrderEventOutbox> selectCandidates(
            @Param("limit") int limit,
            @Param("maxAttempts") int maxAttempts
    );
    /**
     * 3. 原子领取消息。
     *
     * 不使用先 SELECT 再无条件 UPDATE 的写法。
     * 只有当前仍符合领取条件的记录才能更新成功。
     */
    @Update("""
        UPDATE order_event_outbox
        SET status = 'SENDING',
            retry_count = retry_count + 1,
            claim_token = #{token},
            lease_until = DATE_ADD(
                NOW(3),
                INTERVAL #{leaseSeconds} SECOND
            ),
            next_retry_at = NULL,
            updated_time = NOW(3)
        WHERE id = #{id}
          AND retry_count < #{maxAttempts}
          AND (
                status = 'NEW'
                OR (
                    status = 'FAILED'
                    AND (
                        next_retry_at IS NULL
                        OR next_retry_at <= NOW(3)
                    )
                )
                OR (
                    status = 'SENDING'
                    AND (
                        lease_until IS NULL
                        OR lease_until <= NOW(3)
                    )
                )
          )
        """)
    int claim(
            @Param("id") Long id,
            @Param("token") String token,
            @Param("leaseSeconds") int leaseSeconds,
            @Param("maxAttempts") int maxAttempts
    );


    /**
     * 4. 读取自己已经成功领取的消息。
     *
     * 必须重新读取 retry_count，
     * 避免使用领取前可能已经过期的旧对象。
     */
    @Select("""
        SELECT
            id,
            event_id AS eventId,
            topic,
            event_key AS eventKey,
            payload,
            status,
            retry_count AS retryCount,
            error_message AS errorMessage,
            claim_token AS claimToken,
            lease_until AS leaseUntil,
            next_retry_at AS nextRetryAt,
            created_time AS createdTime,
            updated_time AS updatedTime
        FROM order_event_outbox
        WHERE id = #{id}
          AND status = 'SENDING'
          AND claim_token = #{token}
        """)
    OrderEventOutbox selectClaimed(
            @Param("id") Long id,
            @Param("token") String token
    );


    /**
     * 5. Kafka 确认成功之后，标记为 SENT。
     *
     * 只有当前令牌持有者才能执行。
     */
    @Update("""
        UPDATE order_event_outbox
        SET status = 'SENT',
            claim_token = NULL,
            lease_until = NULL,
            next_retry_at = NULL,
            error_message = NULL,
            updated_time = NOW(3)
        WHERE id = #{id}
          AND status = 'SENDING'
          AND claim_token = #{token}
        """)
    int markSent(
            @Param("id") Long id,
            @Param("token") String token
    );


    /**
     * 6. 发送失败，等待下次重试。
     *
     * retry_count 在领取时已经增加，
     * 这里不能再增加。
     */
    @Update("""
        UPDATE order_event_outbox
        SET status = 'FAILED',
            claim_token = NULL,
            lease_until = NULL,
            next_retry_at = DATE_ADD(
                NOW(3),
                INTERVAL #{delaySeconds} SECOND
            ),
            error_message = #{errorMessage},
            updated_time = NOW(3)
        WHERE id = #{id}
          AND status = 'SENDING'
          AND claim_token = #{token}
        """)
    int markFailed(
            @Param("id") Long id,
            @Param("token") String token,
            @Param("delaySeconds") int delaySeconds,
            @Param("errorMessage") String errorMessage
    );

    /**
     * 7. 达到最大尝试次数，进入 DEAD。
     */
    @Update("""
        UPDATE order_event_outbox
        SET status = 'DEAD',
            claim_token = NULL,
            lease_until = NULL,
            next_retry_at = NULL,
            error_message = #{errorMessage},
            updated_time = NOW(3)
        WHERE id = #{id}
          AND status = 'SENDING'
          AND claim_token = #{token}
        """)
    int markDead(
            @Param("id") Long id,
            @Param("token") String token,
            @Param("errorMessage") String errorMessage
    );

    /**
     * 8. 清理已经耗尽尝试次数的遗留任务。
     *
     * 特别处理：
     * 第三次发送时进程直接崩溃，
     * 没机会主动执行 markDead()。
     */
    @Update("""
        UPDATE order_event_outbox
        SET status = 'DEAD',
            claim_token = NULL,
            lease_until = NULL,
            next_retry_at = NULL,
            error_message = COALESCE(
                error_message,
                '发送租约过期，已达到最大尝试次数'
            ),
            updated_time = NOW(3)
        WHERE retry_count >= #{maxAttempts}
          AND (
                status = 'FAILED'

                OR (
                    status = 'SENDING'
                    AND (
                        lease_until IS NULL
                        OR lease_until <= NOW(3)
                    )
                )
          )
        """)
    int retireExhausted(
            @Param("maxAttempts") int maxAttempts
    );
}
