package org.example.productservice.mapper;

import org.apache.ibatis.annotations.*;
import org.example.productservice.entity.StockReservation;

@Mapper
public interface StockReservationMapper {
    @Select("""
        SELECT id, reservation_no AS reservationNo, product_id AS productId,
               quantity, status, created_time AS createdTime, updated_time AS updatedTime
        FROM stock_reservation WHERE reservation_no = #{no}
        """)
    StockReservation find(@Param("no") String reservationNo);

    /** 必须在 Spring 本地事务中调用；行锁持续到事务提交。 */
    @Select("""
        SELECT id, reservation_no AS reservationNo, product_id AS productId,
               quantity, status, created_time AS createdTime, updated_time AS updatedTime
        FROM stock_reservation WHERE reservation_no = #{no} FOR UPDATE
        """)
    StockReservation findForUpdate(@Param("no") String reservationNo);

    @Insert("""
        INSERT INTO stock_reservation
        (reservation_no, product_id, quantity, status, created_time, updated_time)
        VALUES (#{no}, #{productId}, #{quantity}, 'INIT', NOW(), NOW())
        """)
    int insert(@Param("no") String reservationNo,
               @Param("productId") Long productId,
               @Param("quantity") Integer quantity);

    @Update("""
        UPDATE stock_reservation SET status = #{target}, updated_time = NOW()
        WHERE reservation_no = #{no} AND status = #{expected}
        """)
    int transition(@Param("no") String reservationNo,
                   @Param("expected") String expected,
                   @Param("target") String target);
    @Insert("""
    INSERT INTO stock_reservation (
        reservation_no,
        product_id,
        quantity,
        status,
        created_time,
        updated_time
    )
    VALUES (
        #{reservationNo},
        #{productId},
        #{quantity},
        'CANCELLED',
        NOW(),
        NOW())""")
    int insertCancelled(
            @Param("reservationNo") String reservationNo,
            @Param("productId") Long productId,
            @Param("quantity") Integer quantity);
}
