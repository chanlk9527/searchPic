package com.searchpic.server.repository;

import com.searchpic.server.model.document.AlertEventDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertEventRepository extends ElasticsearchRepository<AlertEventDocument, String> {
}
