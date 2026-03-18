package com.searchpic.server.controller;

import com.searchpic.server.common.exception.BusinessException;
import com.searchpic.server.common.result.Result;
import com.searchpic.server.integration.oss.OssService;
import com.searchpic.server.repository.CameraDeviceRepository;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.searchpic.server.common.context.TenantContextHolder;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Event Ingestion", description = "Accept monitoring image events and enqueue them for asynchronous AI processing.")
public class EventIngestController {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CameraDeviceRepository cameraDeviceRepository;
    private final OssService ossService;

    // The Kafka topic used for queuing the raw images
    public static final String INGEST_TOPIC = "searchpic-ingest-topic";

    @Data
    @Schema(description = "Image ingestion request")
    public static class IngestRequest {
        @Schema(description = "Caller-generated unique event ID", example = "evt_987654321")
        private String event_id;

        @Schema(description = "Physical camera ID", example = "cam_front_door_01")
        private String camera_id;

        @Schema(description = "Optional owning user ID. If omitted, the system resolves it from the registered camera.", example = "user_alice")
        private String user_id;

        @Schema(description = "Capture time in epoch milliseconds", example = "1715423800000")
        private Long timestamp;

        @Schema(description = "Publicly accessible image URL", example = "https://oss.example.com/alerts/evt_987654321.jpg")
        private String image_url;
    }

    @PostMapping("/ingest")
    @Operation(
            summary = "Submit an image event for asynchronous analysis",
            description = "Pushes the original monitoring event into Kafka. The worker later extracts visual entities and embeddings, then indexes the result into Elasticsearch.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Event accepted for processing"),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid API key", content = @Content)
            }
    )
    public Result<Map<String, Object>> ingestEvent(@RequestBody IngestRequest request) {
        log.info("Received ingest request for event: {}", request.getEvent_id());

        return Result.success(queueIngestion(
                request.getEvent_id(),
                request.getCamera_id(),
                request.getUser_id(),
                request.getTimestamp(),
                request.getImage_url(),
                false
        ));
    }

    @PostMapping(value = "/ingest-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload an image file and ingest it directly",
            description = "Convenience endpoint for the web console and manual debugging. The server uploads the image to object storage, then immediately enqueues the event for asynchronous AI processing.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Upload accepted for ingestion"),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid API key", content = @Content)
            }
    )
    public Result<Map<String, Object>> ingestUploadedEvent(
            @Parameter(description = "Image file to upload", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Caller-generated unique event ID", example = "evt_upload_debug_001")
            @RequestParam("event_id") String eventId,
            @Parameter(description = "Physical camera ID", example = "cam_front_door_01")
            @RequestParam("camera_id") String cameraId,
            @Parameter(description = "Optional owning user ID. If omitted, the system resolves it from the registered camera.", example = "user_alice")
            @RequestParam(value = "user_id", required = false) String userId,
            @Parameter(description = "Capture time in epoch milliseconds. If omitted, current server time is used.", example = "1715423800000")
            @RequestParam(value = "timestamp", required = false) Long timestamp) {

        log.info("Received direct upload ingest request for event: {}", eventId);

        String currentTenantId = TenantContextHolder.getTenantId();
        String imageUrl = ossService.uploadFile(file, currentTenantId);
        return Result.success(queueIngestion(
                eventId,
                cameraId,
                userId,
                timestamp == null ? System.currentTimeMillis() : timestamp,
                imageUrl,
                true
        ));
    }

    private Map<String, Object> queueIngestion(String eventId, String cameraId, String userId, Long timestamp, String imageUrl, boolean uploadedFromConsole) {
        if (!StringUtils.hasText(eventId)) {
            throw new BusinessException(400, "event_id is required");
        }
        if (!StringUtils.hasText(cameraId)) {
            throw new BusinessException(400, "camera_id is required");
        }
        if (!StringUtils.hasText(imageUrl)) {
            throw new BusinessException(400, "image_url is required");
        }

        String currentTenantId = TenantContextHolder.getTenantId();
        String resolvedUserId = resolveUserId(currentTenantId, cameraId, userId);
        long effectiveTimestamp = timestamp == null ? System.currentTimeMillis() : timestamp;

        Map<String, Object> message = Map.of(
                "tenant_id", currentTenantId,
                "event_id", eventId,
                "camera_id", cameraId,
                "user_id", resolvedUserId,
                "timestamp", effectiveTimestamp,
                "image_url", imageUrl
        );

        kafkaTemplate.send(INGEST_TOPIC, currentTenantId, message);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "PROCESSING");
        response.put("received_at", System.currentTimeMillis());
        response.put("tenant_id", currentTenantId);
        response.put("event_id", eventId);
        response.put("camera_id", cameraId);
        response.put("user_id", resolvedUserId);
        response.put("timestamp", effectiveTimestamp);
        response.put("image_url", imageUrl);
        response.put("uploaded_from_console", uploadedFromConsole);
        return response;
    }

    private String resolveUserId(String tenantId, String cameraId, String userId) {
        if (userId != null && !userId.isBlank()) {
            return userId;
        }

        return cameraDeviceRepository.findByTenantIdAndCameraId(tenantId, cameraId)
                .map(device -> device.getUserId())
                .orElseThrow(() -> new BusinessException(400, "Unknown camera_id. Register the device first or provide user_id explicitly."));
    }
}
