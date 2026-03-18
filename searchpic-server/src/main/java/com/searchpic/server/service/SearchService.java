package com.searchpic.server.service;

import com.searchpic.server.common.exception.BusinessException;
import com.searchpic.server.integration.aliyun.AliyunAiService;
import com.searchpic.server.integration.google.GoogleAiService;
import com.searchpic.server.integration.model.LlmParsingResult;
import com.searchpic.server.model.document.AlertEventDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final GoogleAiService googleAiService;
    private final AliyunAiService aliyunAiService;
    private final ElasticsearchOperations elasticsearchOperations;

    public List<Map<String, Object>> searchEvents(String tenantId, String userQuery, String timezone, List<String> cameraIds) {
        
        // 1. Understand Query via LLM
        LlmParsingResult parsedIntent = googleAiService.parseSearchQuery(userQuery, timezone, cameraIds);
        log.info("Parsed Search Intent: {}", parsedIntent);

        if (!StringUtils.hasText(parsedIntent.getSearch_intent_caption()) && 
            (parsedIntent.getSearch_terms() == null || parsedIntent.getSearch_terms().isEmpty())) {
            throw new BusinessException(400, "Query too vague, could not extract intent or visual elements.");
        }

        // 2. Vectorize the intent caption (if available for kNN)
        // List<Float> queryVector = null;
        if (StringUtils.hasText(parsedIntent.getSearch_intent_caption())) {
            // queryVector = aliyunAiService.generateEmbedding(parsedIntent.getSearch_intent_caption());
        }

        // 3. Build Elasticsearch Hybrid Query
        NativeQueryBuilder queryBuilder = NativeQuery.builder();
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        // 3.1 Hard Filters: Tenant Isolation (MANDATORY), time range, cameras
        boolBuilder.filter(f -> f.term(t -> t.field("tenantId").value(tenantId)));
        
        if (parsedIntent.getCamera_ids() != null && !parsedIntent.getCamera_ids().isEmpty()) {
            List<FieldValue> cameraValues = parsedIntent.getCamera_ids().stream().map(FieldValue::of).toList();
            boolBuilder.filter(f -> f.terms(t -> t.field("cameraId").terms(ts -> ts.value(cameraValues))));
        }

        if (StringUtils.hasText(parsedIntent.getStart_time()) && StringUtils.hasText(parsedIntent.getEnd_time())) {
            try {
                long startMs = ZonedDateTime.parse(parsedIntent.getStart_time()).toInstant().toEpochMilli();
                long endMs = ZonedDateTime.parse(parsedIntent.getEnd_time()).toInstant().toEpochMilli();
                boolBuilder.filter(f -> f.range(r -> r.field("timestamp")
                    .gte(co.elastic.clients.json.JsonData.of(startMs))
                    .lte(co.elastic.clients.json.JsonData.of(endMs))));
            } catch (Exception e) {
                log.warn("Failed to parse time range from LLM output, skipping time filter.", e);
            }
        }

        // 3.2 BM25 Sparse Search
        if (parsedIntent.getSearch_terms() != null && !parsedIntent.getSearch_terms().isEmpty()) {
            String combinedTerms = String.join(" ", parsedIntent.getSearch_terms());
            boolBuilder.must(m -> m.match(ma -> ma.field("entitiesText").query(combinedTerms)));
        }

        queryBuilder.withQuery(boolBuilder.build()._toQuery());

        // 3.3 kNN Dense Search (TODO later pending proper Elasticsearch Java Client mapping checks)

        queryBuilder.withPageable(PageRequest.of(0, 50)); // Top 50 results
        NativeQuery nativeQuery = queryBuilder.build();

        SearchHits<AlertEventDocument> searchHits = elasticsearchOperations.search(nativeQuery, AlertEventDocument.class);

        // 4. Map Results
        List<Map<String, Object>> results = new ArrayList<>();
        for (SearchHit<AlertEventDocument> hit : searchHits) {
            AlertEventDocument doc = hit.getContent();
            Map<String, Object> map = new HashMap<>();
            map.put("event_id", doc.getEventId());
            map.put("camera_id", doc.getCameraId());
            map.put("timestamp", doc.getTimestamp());
            map.put("image_url", doc.getImageUrl());
            map.put("relevance_score", hit.getScore());
            results.add(map);
        }

        return results;
    }
}
