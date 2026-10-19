package org.example.searchservice.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;
import org.springframework.data.elasticsearch.annotations.Field;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor      // 1. 生成无参构造器（必须保留！ES 反序列化要用）
//@AllArgsConstructor     // 2. 生成全参构造器（你 new ProgramDocument(...) 要用）
@Document(indexName = "program_index")
public class ProgramDocument {
    @Id
    private Long id;
    @Field(type = FieldType.Text)
    private String title;
    @Field(type = FieldType.Keyword)
    private String city;
    @Field(type = FieldType.Keyword)
    private String category;
    @Field(type = FieldType.Text)
    private String venue;
    @Field(type = FieldType.Text)
    private String description;
    @Field(type = FieldType.Double)
    private BigDecimal minPrice;
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime startTime;
    private Integer heat;
    public ProgramDocument(Long id, String title, String city, String category,
                           String venue, String description, BigDecimal minPrice,
                           LocalDateTime startTime, Integer heat) {
        this.id = id;
        this.title = title;
        this.city = city;
        this.category = category;
        this.venue = venue;
        this.description = description;
        this.minPrice = minPrice;
        this.startTime = startTime;
        this.heat = heat;
    }
}
