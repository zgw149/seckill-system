package org.example.searchservice.service;

import org.example.searchservice.document.ProgramDocument;
import org.example.searchservice.repository.ProgramSearchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProgramSearchService {
    @Autowired
    private ProgramSearchRepository programSearchRepository;
    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    // 保存文档到Elasticsearch
    public void save(ProgramDocument document) {
        programSearchRepository.save(document);
    }

    // 根据关键字搜索文档
    public SearchHits<ProgramDocument> search(String keyword,String city,String category,int page,int size){
        int pageIndex = Math.max(page - 1, 0);
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b ->{
                    if(StringUtils.hasText(keyword)){
                        b.must(m->m.multiMatch(mm -> mm
                                .query(keyword)
                                .fields("title","venue","description")
                        ));
                    }
                    if (StringUtils.hasText(city)) {
                        b.filter(f -> f.term(t -> t
                                .field("city")
                                .value(city)
                        ));
                    }
                    if (StringUtils.hasText(category)) {
                        b.filter(f -> f.term(t -> t
                                .field("category")
                                .value(category)
                        ));
                    }
                    return b;
                }))
                .withPageable(PageRequest.of(pageIndex, size))
                .build();
        return elasticsearchOperations.search(query,ProgramDocument.class);
    }
}
