package com.searchpic.server.integration.kafka;

import com.searchpic.server.config.AiProperties;
import com.searchpic.server.controller.EventIngestController;
import com.searchpic.server.integration.aliyun.AliyunAiService;
import com.searchpic.server.integration.model.VlmExtractionResult;
import com.searchpic.server.model.document.AlertEventDocument;
import com.searchpic.server.repository.AlertEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventIngestConsumer {

    private final AliyunAiService aliyunAiService;
    private final AiProperties aiProperties;
    private final AlertEventRepository alertEventRepository;

    @KafkaListener(topics = EventIngestController.INGEST_TOPIC, groupId = "searchpic-worker-group")
    public void consumeIngestEvent(Map<String, Object> message) {
        String eventId = (String) message.get("event_id");
        String tenantId = (String) message.get("tenant_id");
        String userId = (String) message.get("user_id");
        String cameraId = (String) message.get("camera_id");
        Long timestamp = ((Number) message.get("timestamp")).longValue();
        String imageUrl = (String) message.get("image_url");

        log.info("Processing ingest event: {} for tenant: {}", eventId, tenantId);

        try {
            // 1. VLM Call
            VlmExtractionResult vlmResult = aliyunAiService.extractEntitiesFromImage(imageUrl);
            
            // 2. Extract context
            String sceneCaption = vlmResult.getScene_caption();
            
            // Flatmap all entity JSON into raw text for BM25 sparse search
            String entitiesText = flattenEntities(vlmResult);

            // 3. Embedding Call
            List<Float> embeddingVector = null;
            if (StringUtils.hasText(sceneCaption)) {
                embeddingVector = aliyunAiService.generateEmbedding(sceneCaption);
                embeddingVector = normalizeEmbeddingDimensions(eventId, embeddingVector);
            }

            // 4. Persistence to Elasticsearch
            AlertEventDocument doc = new AlertEventDocument();
            doc.setEventId(eventId);
            doc.setTenantId(tenantId);
            doc.setUserId(userId);
            doc.setCameraId(cameraId);
            doc.setTimestamp(timestamp);
            doc.setImageUrl(imageUrl);
            doc.setEntitiesText(entitiesText);
            doc.setCaptionVector(embeddingVector);
            
            alertEventRepository.save(doc);
            
            log.info("Successfully processed and indexed event: {}", eventId);
        } catch (Exception e) {
            log.error("Failed to process ingest pipeline for event {}: {}", eventId, e.getMessage());
            // TODO: In production, send to a Dead Letter Queue (DLQ)
        }
    }

    private String flattenEntities(VlmExtractionResult result) {
        StringBuilder sb = new StringBuilder();
        if (result.getEnvironment() != null) sb.append(result.getEnvironment()).append(" ");
        if (result.getPersons() != null) sb.append(result.getPersons()).append(" ");
        if (result.getVehicles() != null) sb.append(result.getVehicles()).append(" ");
        if (result.getAnimals() != null) sb.append(result.getAnimals()).append(" ");
        if (result.getPackages_and_baggage() != null) sb.append(result.getPackages_and_baggage()).append(" ");
        if (result.getPhysical_security_events() != null) sb.append(result.getPhysical_security_events()).append(" ");
        if (result.getFire_and_smoke() != null) sb.append(result.getFire_and_smoke()).append(" ");
        return sb.toString().trim();
    }

    private List<Float> normalizeEmbeddingDimensions(String eventId, List<Float> embeddingVector) {
        if (embeddingVector == null) {
            return null;
        }

        int expectedDimensions = aiProperties.getEmbeddingDimensions() == null ? 768 : aiProperties.getEmbeddingDimensions();
        if (embeddingVector.size() == expectedDimensions) {
            return embeddingVector;
        }

        log.warn("Embedding dimension mismatch for event {}. expected={}, actual={}. Vector will be resized before indexing.",
                eventId, expectedDimensions, embeddingVector.size());

        List<Float> normalized = new ArrayList<>(embeddingVector);
        if (normalized.size() > expectedDimensions) {
            normalized = new ArrayList<>(normalized.subList(0, expectedDimensions));
        } else {
            normalized.addAll(Collections.nCopies(expectedDimensions - normalized.size(), 0.0f));
        }

        if (Boolean.TRUE.equals(aiProperties.getDebugLogEnabled())) {
            log.info("Normalized embedding vector size for event {}: {}", eventId, normalized.size());
        }

        return normalized;
    }
}
