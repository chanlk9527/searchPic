package com.searchpic.server.service;

import com.searchpic.server.common.exception.BusinessException;
import com.searchpic.server.model.entity.CameraDevice;
import com.searchpic.server.model.entity.TenantUser;
import com.searchpic.server.repository.CameraDeviceRepository;
import com.searchpic.server.repository.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminDirectoryService {

    private final TenantUserRepository tenantUserRepository;
    private final CameraDeviceRepository cameraDeviceRepository;
    private final EventBrowseService eventBrowseService;

    public Map<String, Object> createUser(String tenantId, String userId, String displayName, String email) {
        tenantUserRepository.findByTenantIdAndUserId(tenantId, userId).ifPresent(existing -> {
            throw new BusinessException(409, "User already exists under this tenant");
        });

        TenantUser user = new TenantUser();
        user.setTenantId(tenantId);
        user.setUserId(userId);
        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setStatus("ACTIVE");
        tenantUserRepository.save(user);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenant_id", tenantId);
        result.put("user_id", userId);
        result.put("display_name", displayName);
        result.put("email", email);
        result.put("status", "ACTIVE");
        return result;
    }

    public Map<String, Object> createDevice(String tenantId, String userId, String cameraId, String deviceName, String locationName) {
        tenantUserRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> new BusinessException(404, "User does not exist under this tenant"));

        cameraDeviceRepository.findByTenantIdAndCameraId(tenantId, cameraId).ifPresent(existing -> {
            throw new BusinessException(409, "Camera already exists under this tenant");
        });

        CameraDevice device = new CameraDevice();
        device.setTenantId(tenantId);
        device.setUserId(userId);
        device.setCameraId(cameraId);
        device.setDeviceName(deviceName);
        device.setLocationName(locationName);
        device.setStatus("ACTIVE");
        cameraDeviceRepository.save(device);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenant_id", tenantId);
        result.put("user_id", userId);
        result.put("camera_id", cameraId);
        result.put("device_name", deviceName);
        result.put("location_name", locationName);
        result.put("status", "ACTIVE");
        return result;
    }

    public List<Map<String, Object>> listUsers(String tenantId, Long startTimestamp, Long endTimestamp) {
        List<TenantUser> users = tenantUserRepository.findAllByTenantIdOrderByDisplayNameAsc(tenantId);
        List<Map<String, Object>> results = new ArrayList<>();

        for (TenantUser user : users) {
            List<CameraDevice> devices = cameraDeviceRepository.findAllByTenantIdAndUserIdOrderByDeviceNameAsc(tenantId, user.getUserId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tenant_id", tenantId);
            row.put("user_id", user.getUserId());
            row.put("display_name", user.getDisplayName());
            row.put("email", user.getEmail());
            row.put("status", user.getStatus());
            row.put("device_count", devices.size());
            row.put("event_count", eventBrowseService.countEvents(tenantId, user.getUserId(), null, startTimestamp, endTimestamp));
            row.put("latest_event_timestamp", eventBrowseService.findLatestTimestamp(tenantId, user.getUserId(), null, startTimestamp, endTimestamp));
            results.add(row);
        }

        return results;
    }

    public List<Map<String, Object>> listDevices(String tenantId, String userId, Long startTimestamp, Long endTimestamp) {
        List<CameraDevice> devices = StringUtils.hasText(userId)
                ? cameraDeviceRepository.findAllByTenantIdAndUserIdOrderByDeviceNameAsc(tenantId, userId)
                : cameraDeviceRepository.findAllByTenantIdOrderByDeviceNameAsc(tenantId);

        List<Map<String, Object>> results = new ArrayList<>();
        for (CameraDevice device : devices) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tenant_id", tenantId);
            row.put("user_id", device.getUserId());
            row.put("camera_id", device.getCameraId());
            row.put("device_name", device.getDeviceName());
            row.put("location_name", device.getLocationName());
            row.put("status", device.getStatus());
            row.put("event_count", eventBrowseService.countEvents(tenantId, device.getUserId(), device.getCameraId(), startTimestamp, endTimestamp));
            row.put("latest_event_timestamp", eventBrowseService.findLatestTimestamp(tenantId, device.getUserId(), device.getCameraId(), startTimestamp, endTimestamp));
            results.add(row);
        }

        return results;
    }
}
