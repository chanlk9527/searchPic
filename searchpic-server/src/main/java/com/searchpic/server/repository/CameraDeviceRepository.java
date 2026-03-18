package com.searchpic.server.repository;

import com.searchpic.server.model.entity.CameraDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CameraDeviceRepository extends JpaRepository<CameraDevice, Long> {
    Optional<CameraDevice> findByTenantIdAndCameraId(String tenantId, String cameraId);
    List<CameraDevice> findAllByTenantIdOrderByDeviceNameAsc(String tenantId);
    List<CameraDevice> findAllByTenantIdAndUserIdOrderByDeviceNameAsc(String tenantId, String userId);
}
