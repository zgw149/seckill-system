package org.example.searchservice.repository;

import org.example.searchservice.document.ProgramDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ProgramSearchRepository extends ElasticsearchRepository<ProgramDocument, Long> {
}
