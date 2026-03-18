package com.searchpic.server.controller;

import com.searchpic.server.common.result.Result;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.searchpic.server.common.context.TenantContextHolder;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventIngestController {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // The Kafka topic used for queuing the raw images
    public static final String INGEST_TOPIC = "searchpic-ingest-topic";

    @Data
    public static class IngestRequest {
        private String event_id;
        private String camera_id;
        private Long timestamp;
        private String image_url;
    }

    @PostMapping("/ingest")
    public Result<Map<String, Object>> ingestEvent(@RequestBody IngestRequest request) {
        log.info("Received ingest request for event: {}", request.getEvent_id());

        // Get the real tenant_id filtered from the API Key Interceptor
        String currentTenantId = TenantContextHolder.getTenantId();

        // Create the message payload for Kafka
        Map<String, Object> message = Map.of(
                "tenant_id", currentTenantId,
                "event_id", request.getEvent_id(),
                "camera_id", request.getCamera_id(),
                "timestamp", request.getTimestamp(),
                "image_url", request.getImage_url()
        );

        // Async dispatch into Kafka
        kafkaTemplate.send(INGEST_TOPIC, currentTenantId, message);

        // Return immediately acknowledging receipt
        return Result.success(Map.of(
                "status", "PROCESSING",
                "received_at", System.currentTimeMillis()
        ));
    }
}
