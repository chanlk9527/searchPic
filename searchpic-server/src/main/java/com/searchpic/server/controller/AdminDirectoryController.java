package com.searchpic.server.controller;

import com.searchpic.server.common.context.TenantContextHolder;
import com.searchpic.server.common.result.Result;
import com.searchpic.server.service.AdminDirectoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Directory", description = "Manage tenant users/devices and view user/device-level upload statistics.")
public class AdminDirectoryController {

    private final AdminDirectoryService adminDirectoryService;

    @Data
    @Schema(description = "Create tenant user request")
    public static class CreateUserRequest {
        @Schema(description = "Optional tenant override in development mode", example = "tenant_local_dev")
        private String tenant_id;

        @Schema(description = "Tenant-scoped user ID", example = "user_charlie")
        private String user_id;

        @Schema(description = "User display name", example = "Charlie")
        private String display_name;

        @Schema(description = "User email", example = "charlie@example.com")
        private String email;
    }

    @Data
    @Schema(description = "Create camera device request")
    public static class CreateDeviceRequest {
        @Schema(description = "Optional tenant override in development mode", example = "tenant_local_dev")
        private String tenant_id;

        @Schema(description = "Owner user ID", example = "user_charlie")
        private String user_id;

        @Schema(description = "Camera/device ID", example = "cam_backyard_03")
        private String camera_id;

        @Schema(description = "Human-readable device name", example = "Backyard Camera")
        private String device_name;

        @Schema(description = "Optional location label", example = "Backyard")
        private String location_name;
    }

    @PostMapping("/users")
    @Operation(
            summary = "Create a tenant user",
            description = "Registers a tenant-scoped user so events and devices can be grouped under a person/account.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "User created"),
                    @ApiResponse(responseCode = "409", description = "User already exists")
            }
    )
    public Result<Map<String, Object>> createUser(@RequestBody CreateUserRequest request) {
        String effectiveTenantId = resolveTenantId(request.getTenant_id());
        return Result.success(adminDirectoryService.createUser(
                effectiveTenantId,
                request.getUser_id(),
                request.getDisplay_name(),
                request.getEmail()
        ));
    }

    @GetMapping("/users")
    @Operation(
            summary = "List tenant users with upload stats",
            description = "Returns each user under a tenant, plus device count, total uploaded images, and latest upload time.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = @ApiResponse(responseCode = "200", description = "User list returned")
    )
    public Result<List<Map<String, Object>>> listUsers(
            @RequestParam(value = "tenant_id", required = false) String tenantId,
            @RequestParam(value = "start_timestamp", required = false) Long startTimestamp,
            @RequestParam(value = "end_timestamp", required = false) Long endTimestamp) {

        String effectiveTenantId = resolveTenantId(tenantId);
        return Result.success(adminDirectoryService.listUsers(effectiveTenantId, startTimestamp, endTimestamp));
    }

    @PostMapping("/devices")
    @Operation(
            summary = "Create a camera device",
            description = "Registers a camera/device under a tenant user so event ownership can be resolved automatically during ingestion.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Device created"),
                    @ApiResponse(responseCode = "409", description = "Device already exists")
            }
    )
    public Result<Map<String, Object>> createDevice(@RequestBody CreateDeviceRequest request) {
        String effectiveTenantId = resolveTenantId(request.getTenant_id());
        return Result.success(adminDirectoryService.createDevice(
                effectiveTenantId,
                request.getUser_id(),
                request.getCamera_id(),
                request.getDevice_name(),
                request.getLocation_name()
        ));
    }

    @GetMapping("/devices")
    @Operation(
            summary = "List devices with upload stats",
            description = "Returns all devices under a tenant or a specific user, plus upload count and latest upload time.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = @ApiResponse(responseCode = "200", description = "Device list returned")
    )
    public Result<List<Map<String, Object>>> listDevices(
            @RequestParam(value = "tenant_id", required = false) String tenantId,
            @RequestParam(value = "user_id", required = false) String userId,
            @RequestParam(value = "start_timestamp", required = false) Long startTimestamp,
            @RequestParam(value = "end_timestamp", required = false) Long endTimestamp) {

        String effectiveTenantId = resolveTenantId(tenantId);
        return Result.success(adminDirectoryService.listDevices(effectiveTenantId, userId, startTimestamp, endTimestamp));
    }

    private String resolveTenantId(String tenantId) {
        if (StringUtils.hasText(tenantId)) {
            return tenantId;
        }
        return TenantContextHolder.getTenantId();
    }
}
