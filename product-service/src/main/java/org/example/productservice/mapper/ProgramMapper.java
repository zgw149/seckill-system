package org.example.productservice.mapper;
import org.apache.ibatis.annotations.*;
import org.example.productservice.entity.Program;

import java.util.List;

@Mapper
public interface ProgramMapper {

    @Insert("""
            INSERT INTO program (
                title,
                city,
                category,
                venue,
                description,
                min_price,
                stock,
                heat,
                start_time,
                created_time,
                updated_time
            )
            VALUES (
                #{title},
                #{city},
                #{category},
                #{venue},
                #{description},
                #{minPrice},
                #{stock},
                #{heat},
                #{startTime},
                NOW(),
                NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Program program);

    @Update("""
            UPDATE program
            SET title = #{title},
                city = #{city},
                category = #{category},
                venue = #{venue},
                description = #{description},
                min_price = #{minPrice},
                stock = #{stock},
                heat = #{heat},
                start_time = #{startTime},
                updated_time = NOW()
            WHERE id = #{id}
            """)
    int update(Program program);

    @Delete("""
            DELETE FROM program
            WHERE id = #{id}
            """)
    int deleteById(@Param("id") Long id);

    @Select("""
            SELECT id,
                   title,
                   city,
                   category,
                   venue,
                   description,
                   min_price AS minPrice,
                   stock,
                   heat,
                   start_time AS startTime,
                   created_time AS createdTime,
                   updated_time AS updatedTime
            FROM program
            WHERE id = #{id}
            """)
    Program selectById(@Param("id") Long id);

    @Select("""
            SELECT id,
                   title,
                   city,
                   category,
                   venue,
                   description,
                   min_price AS minPrice,
                   stock,
                   heat,
                   start_time AS startTime,
                   created_time AS createdTime,
                   updated_time AS updatedTime
            FROM program
            ORDER BY id DESC
            """)
    List<Program> selectAll();
}