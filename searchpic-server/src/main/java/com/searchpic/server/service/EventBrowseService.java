package com.searchpic.server.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.searchpic.server.common.exception.BusinessException;
import com.searchpic.server.model.document.AlertEventDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventBrowseService {

    private static final String INDEX_NAME = "searchpic_alert_events";

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchClient elasticsearchClient;

    public Map<String, Object> listEvents(String tenantId, String userId, String cameraId, Long startTimestamp, Long endTimestamp, int page, int size) {
        Query baseQuery = buildFilterQuery(tenantId, userId, cameraId, startTimestamp, endTimestamp);

        NativeQuery query = NativeQuery.builder()
                .withQuery(baseQuery)
                .withPageable(PageRequest.of(page, size))
                .withSort(s -> s.field(f -> f.field("timestamp").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)))
                .build();

        SearchHits<AlertEventDocument> searchHits = elasticsearchOperations.search(query, AlertEventDocument.class);

        List<Map<String, Object>> items = new ArrayList<>();
        for (SearchHit<AlertEventDocument> hit : searchHits) {
            AlertEventDocument doc = hit.getContent();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("event_id", doc.getEventId());
            row.put("tenant_id", doc.getTenantId());
            row.put("user_id", doc.getUserId());
            row.put("camera_id", doc.getCameraId());
            row.put("timestamp", doc.getTimestamp());
            row.put("image_url", doc.getImageUrl());
            row.put("entities_text", doc.getEntitiesText());
            items.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("page", page);
        result.put("size", size);
        result.put("total", searchHits.getTotalHits());
        result.put("items", items);
        return result;
    }

    public Map<String, Object> latestEvents(String tenantId, String userId, String cameraId, Long startTimestamp, Long endTimestamp, int limit) {
        Query baseQuery = buildFilterQuery(tenantId, userId, cameraId, startTimestamp, endTimestamp);

        NativeQuery query = NativeQuery.builder()
                .withQuery(baseQuery)
                .withPageable(PageRequest.of(0, limit))
                .withSort(s -> s.field(f -> f.field("timestamp").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)))
                .build();

        SearchHits<AlertEventDocument> searchHits = elasticsearchOperations.search(query, AlertEventDocument.class);
        List<Map<String, Object>> items = new ArrayList<>();
        for (SearchHit<AlertEventDocument> hit : searchHits) {
            AlertEventDocument doc = hit.getContent();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("event_id", doc.getEventId());
            row.put("tenant_id", doc.getTenantId());
            row.put("user_id", doc.getUserId());
            row.put("camera_id", doc.getCameraId());
            row.put("timestamp", doc.getTimestamp());
            row.put("image_url", doc.getImageUrl());
            row.put("entities_text", doc.getEntitiesText());
            items.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenant_id", tenantId);
        result.put("user_id", userId);
        result.put("camera_id", cameraId);
        result.put("limit", limit);
        result.put("items", items);
        return result;
    }

    public Map<String, Object> getEventDetail(String tenantId, String eventId) {
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
        boolBuilder.filter(f -> f.term(t -> t.field("tenantId").value(tenantId)));
        boolBuilder.filter(f -> f.term(t -> t.field("eventId").value(eventId)));

        NativeQuery query = NativeQuery.builder()
                .withQuery(boolBuilder.build()._toQuery())
                .withPageable(PageRequest.of(0, 1))
                .build();

        SearchHits<AlertEventDocument> searchHits = elasticsearchOperations.search(query, AlertEventDocument.class);
        if (searchHits.isEmpty()) {
            throw new BusinessException(404, "Event not found under current tenant");
        }

        AlertEventDocument doc = searchHits.getSearchHit(0).getContent();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("event_id", doc.getEventId());
        detail.put("tenant_id", doc.getTenantId());
        detail.put("user_id", doc.getUserId());
        detail.put("camera_id", doc.getCameraId());
        detail.put("timestamp", doc.getTimestamp());
        detail.put("image_url", doc.getImageUrl());
        detail.put("entities_text", doc.getEntitiesText());
        detail.put("caption_vector_size", doc.getCaptionVector() == null ? 0 : doc.getCaptionVector().size());
        return detail;
    }

    public Map<String, Object> overviewStats(String tenantId, String userId, String cameraId, Long startTimestamp, Long endTimestamp) {
        Query baseQuery = buildFilterQuery(tenantId, userId, cameraId, startTimestamp, endTimestamp);
        long totalEvents = elasticsearchOperations.count(NativeQuery.builder().withQuery(baseQuery).build(), AlertEventDocument.class);

        SearchRequest request = new SearchRequest.Builder()
                .index(INDEX_NAME)
                .size(0)
                .query(baseQuery)
                .aggregations("camera_count", Aggregation.of(a -> a.cardinality(c -> c.field("cameraId"))))
                .aggregations("latest_timestamp", Aggregation.of(a -> a.max(m -> m.field("timestamp"))))
                .build();

        try {
            SearchResponse<Void> response = elasticsearchClient.search(request, Void.class);
            long uniqueCameraCount = response.aggregations().get("camera_count").cardinality().value();
            Double latestTimestampValue = response.aggregations().get("latest_timestamp").max().value();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("tenant_id", tenantId);
            result.put("user_id", userId);
            result.put("camera_id", cameraId);
            result.put("start_timestamp", startTimestamp);
            result.put("end_timestamp", endTimestamp);
            result.put("total_events", totalEvents);
            result.put("unique_camera_count", uniqueCameraCount);
            result.put("latest_event_timestamp", latestTimestampValue == null ? null : latestTimestampValue.longValue());
            return result;
        } catch (IOException e) {
            log.error("Failed to query overview stats from Elasticsearch", e);
            throw new IllegalStateException("Failed to query overview stats", e);
        }
    }

    public Map<String, Object> cameraStats(String tenantId, String userId, Long startTimestamp, Long endTimestamp, int limit) {
        Query baseQuery = buildFilterQuery(tenantId, userId, null, startTimestamp, endTimestamp);

        SearchRequest request = new SearchRequest.Builder()
                .index(INDEX_NAME)
                .size(0)
                .query(baseQuery)
                .aggregations("by_camera", Aggregation.of(a -> a.terms(t -> t
                                .field("cameraId")
                                .size(limit))
                        .aggregations("latest_timestamp", sub -> sub.max(m -> m.field("timestamp")))))
                .build();

        try {
            SearchResponse<Void> response = elasticsearchClient.search(request, Void.class);
            List<Map<String, Object>> cameras = new ArrayList<>();
            response.aggregations().get("by_camera").sterms().buckets().array().forEach(bucket -> {
                Map<String, Object> row = new HashMap<>();
                row.put("camera_id", bucket.key().stringValue());
                row.put("event_count", bucket.docCount());
                Double latestTimestampValue = bucket.aggregations().get("latest_timestamp").max().value();
                row.put("latest_event_timestamp", latestTimestampValue == null ? null : latestTimestampValue.longValue());
                cameras.add(row);
            });

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("tenant_id", tenantId);
            result.put("user_id", userId);
            result.put("start_timestamp", startTimestamp);
            result.put("end_timestamp", endTimestamp);
            result.put("limit", limit);
            result.put("items", cameras);
            return result;
        } catch (IOException e) {
            log.error("Failed to query camera stats from Elasticsearch", e);
            throw new IllegalStateException("Failed to query camera stats", e);
        }
    }

    public long countEvents(String tenantId, String userId, String cameraId, Long startTimestamp, Long endTimestamp) {
        Query baseQuery = buildFilterQuery(tenantId, userId, cameraId, startTimestamp, endTimestamp);
        return elasticsearchOperations.count(NativeQuery.builder().withQuery(baseQuery).build(), AlertEventDocument.class);
    }

    public Long findLatestTimestamp(String tenantId, String userId, String cameraId, Long startTimestamp, Long endTimestamp) {
        Query baseQuery = buildFilterQuery(tenantId, userId, cameraId, startTimestamp, endTimestamp);
        SearchRequest request = new SearchRequest.Builder()
                .index(INDEX_NAME)
                .size(0)
                .query(baseQuery)
                .aggregations("latest_timestamp", Aggregation.of(a -> a.max(m -> m.field("timestamp"))))
                .build();

        try {
            SearchResponse<Void> response = elasticsearchClient.search(request, Void.class);
            Double latestTimestampValue = response.aggregations().get("latest_timestamp").max().value();
            return latestTimestampValue == null ? null : latestTimestampValue.longValue();
        } catch (IOException e) {
            log.error("Failed to query latest timestamp from Elasticsearch", e);
            throw new IllegalStateException("Failed to query latest timestamp", e);
        }
    }

    private Query buildFilterQuery(String tenantId, String userId, String cameraId, Long startTimestamp, Long endTimestamp) {
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        if (StringUtils.hasText(tenantId)) {
            boolBuilder.filter(f -> f.term(t -> t.field("tenantId").value(tenantId)));
        }
        if (StringUtils.hasText(userId)) {
            boolBuilder.filter(f -> f.term(t -> t.field("userId").value(userId)));
        }
        if (StringUtils.hasText(cameraId)) {
            boolBuilder.filter(f -> f.term(t -> t.field("cameraId").value(cameraId)));
        }
        if (startTimestamp != null || endTimestamp != null) {
            boolBuilder.filter(f -> f.range(r -> {
                r.field("timestamp");
                if (startTimestamp != null) {
                    r.gte(co.elastic.clients.json.JsonData.of(startTimestamp));
                }
                if (endTimestamp != null) {
                    r.lte(co.elastic.clients.json.JsonData.of(endTimestamp));
                }
                return r;
            }));
        }

        return boolBuilder.build()._toQuery();
    }
}
