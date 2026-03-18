package com.searchpic.server.controller;

import com.searchpic.server.common.context.TenantContextHolder;
import com.searchpic.server.common.result.Result;
import com.searchpic.server.service.EventBrowseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Event Browse", description = "Browse uploaded event images and view tenant/device-level statistics.")
public class EventBrowseController {

    private final EventBrowseService eventBrowseService;

    @GetMapping
    @Operation(
            summary = "List event images",
            description = "Returns paged event/image records for the current tenant. In the current development phase, you can also override tenant_id manually for debugging.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = @ApiResponse(responseCode = "200", description = "Event list returned")
    )
    public Result<Map<String, Object>> listEvents(
            @Parameter(description = "Optional tenant ID override for development/debug", example = "tenant_local_dev")
            @RequestParam(value = "tenant_id", required = false) String tenantId,
            @Parameter(description = "Optional user ID filter", example = "user_alice")
            @RequestParam(value = "user_id", required = false) String userId,
            @Parameter(description = "Optional camera/device ID filter", example = "cam_front_door_01")
            @RequestParam(value = "camera_id", required = false) String cameraId,
            @Parameter(description = "Optional start time in epoch milliseconds", example = "1715423800000")
            @RequestParam(value = "start_timestamp", required = false) Long startTimestamp,
            @Parameter(description = "Optional end time in epoch milliseconds", example = "1715510200000")
            @RequestParam(value = "end_timestamp", required = false) Long endTimestamp,
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(value = "page", defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(value = "size", defaultValue = "20") int size) {

        String effectiveTenantId = resolveTenantId(tenantId);
        return Result.success(eventBrowseService.listEvents(effectiveTenantId, userId, cameraId, startTimestamp, endTimestamp, page, size));
    }

    @GetMapping("/latest")
    @Operation(
            summary = "Get latest event images",
            description = "Returns the newest event/image records sorted by timestamp descending. Useful for building a latest upload feed during debugging or admin review.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = @ApiResponse(responseCode = "200", description = "Latest events returned")
    )
    public Result<Map<String, Object>> latestEvents(
            @Parameter(description = "Optional tenant ID override for development/debug", example = "tenant_local_dev")
            @RequestParam(value = "tenant_id", required = false) String tenantId,
            @Parameter(description = "Optional user ID filter", example = "user_alice")
            @RequestParam(value = "user_id", required = false) String userId,
            @Parameter(description = "Optional camera/device ID filter", example = "cam_front_door_01")
            @RequestParam(value = "camera_id", required = false) String cameraId,
            @Parameter(description = "Optional start time in epoch milliseconds", example = "1715423800000")
            @RequestParam(value = "start_timestamp", required = false) Long startTimestamp,
            @Parameter(description = "Optional end time in epoch milliseconds", example = "1715510200000")
            @RequestParam(value = "end_timestamp", required = false) Long endTimestamp,
            @Parameter(description = "Max number of latest records", example = "20")
            @RequestParam(value = "limit", defaultValue = "20") int limit) {

        String effectiveTenantId = resolveTenantId(tenantId);
        return Result.success(eventBrowseService.latestEvents(effectiveTenantId, userId, cameraId, startTimestamp, endTimestamp, limit));
    }

    @GetMapping("/{eventId}")
    @Operation(
            summary = "Get single event detail",
            description = "Returns the detailed fields for a single event under the current tenant. Used by the web console detail drawer.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = @ApiResponse(responseCode = "200", description = "Event detail returned")
    )
    public Result<Map<String, Object>> eventDetail(
            @PathVariable("eventId") String eventId,
            @RequestParam(value = "tenant_id", required = false) String tenantId) {

        String effectiveTenantId = resolveTenantId(tenantId);
        return Result.success(eventBrowseService.getEventDetail(effectiveTenantId, eventId));
    }

    @GetMapping("/stats/overview")
    @Operation(
            summary = "Get tenant/device overview stats",
            description = "Returns total uploaded image count, unique device count, and latest upload time under the selected filters.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = @ApiResponse(responseCode = "200", description = "Overview stats returned")
    )
    public Result<Map<String, Object>> overviewStats(
            @Parameter(description = "Optional tenant ID override for development/debug", example = "tenant_local_dev")
            @RequestParam(value = "tenant_id", required = false) String tenantId,
            @Parameter(description = "Optional user ID filter", example = "user_alice")
            @RequestParam(value = "user_id", required = false) String userId,
            @Parameter(description = "Optional camera/device ID filter", example = "cam_front_door_01")
            @RequestParam(value = "camera_id", required = false) String cameraId,
            @Parameter(description = "Optional start time in epoch milliseconds", example = "1715423800000")
            @RequestParam(value = "start_timestamp", required = false) Long startTimestamp,
            @Parameter(description = "Optional end time in epoch milliseconds", example = "1715510200000")
            @RequestParam(value = "end_timestamp", required = false) Long endTimestamp) {

        String effectiveTenantId = resolveTenantId(tenantId);
        return Result.success(eventBrowseService.overviewStats(effectiveTenantId, userId, cameraId, startTimestamp, endTimestamp));
    }

    @GetMapping("/stats/by-camera")
    @Operation(
            summary = "Get event counts grouped by device",
            description = "Returns per-camera upload counts, suitable for answering how many pictures each device uploaded in a time range.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = @ApiResponse(responseCode = "200", description = "Camera stats returned")
    )
    public Result<Map<String, Object>> cameraStats(
            @Parameter(description = "Optional tenant ID override for development/debug", example = "tenant_local_dev")
            @RequestParam(value = "tenant_id", required = false) String tenantId,
            @Parameter(description = "Optional user ID filter", example = "user_alice")
            @RequestParam(value = "user_id", required = false) String userId,
            @Parameter(description = "Optional start time in epoch milliseconds", example = "1715423800000")
            @RequestParam(value = "start_timestamp", required = false) Long startTimestamp,
            @Parameter(description = "Optional end time in epoch milliseconds", example = "1715510200000")
            @RequestParam(value = "end_timestamp", required = false) Long endTimestamp,
            @Parameter(description = "Maximum number of devices returned", example = "100")
            @RequestParam(value = "limit", defaultValue = "100") int limit) {

        String effectiveTenantId = resolveTenantId(tenantId);
        return Result.success(eventBrowseService.cameraStats(effectiveTenantId, userId, startTimestamp, endTimestamp, limit));
    }

    private String resolveTenantId(String tenantId) {
        if (StringUtils.hasText(tenantId)) {
            return tenantId;
        }
        return TenantContextHolder.getTenantId();
    }
}
