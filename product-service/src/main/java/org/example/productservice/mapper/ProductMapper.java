package org.example.productservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.productservice.entity.Product;

@Mapper
public interface ProductMapper {
    @Select("select id,name,price,stock,created_time,updated_time " +
            "from product " +
            "where id = #{id}")
    Product selectById(@Param("id")Long id);

    @Update("update product set stock = stock - #{stock},updated_time = NOW() " +
            "where id = #{id} and stock >= #{stock}")
    int deductStock(@Param("id")Long id, @Param("stock")Integer stock);

    /** 必须与 stock_reservation 状态更新处于相同数据库事务内。 */
    @Update("update product set stock = stock + #{quantity},updated_time = NOW() where id = #{id}")
    int restoreStock(@Param("id") Long id, @Param("quantity") Integer quantity);
}
