package org.example.searchservice.controller;

import org.example.searchservice.document.ProgramDocument;
import org.example.searchservice.service.ProgramSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/search/programs")
public class ProgramSearchController {
    @Autowired
    private ProgramSearchService programSearchService;

    @PostMapping("/init")
    public String initTestData(){
        programSearchService.save(new ProgramDocument(
                1L,
                "周杰伦2026世界巡回演唱会",
                "上海",
                "演唱会",
                "上海体育场",
                "经典歌曲现场演出，热门演唱会门票限量发售",
                new BigDecimal("599"),
                LocalDateTime.of(2026, 9, 1, 19, 30),
                100
        ));
        programSearchService.save(new ProgramDocument(
                2L,
                "林俊杰演唱会",
                "南京",
                "演唱会",
                "南京奥体中心",
                "流行音乐现场演出",
                new BigDecimal("499"),
                LocalDateTime.of(2026, 10, 1, 19, 30),
                90
        ));
        return "初始化成功";
    }
    @GetMapping
    public List<ProgramDocument> search(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        SearchHits<ProgramDocument> searchHits = programSearchService.search(
                keyword,
                city,
                category,
                page,
                size
        );
        return searchHits.stream()
                .map(SearchHit::getContent)
                .toList();
    }
}
