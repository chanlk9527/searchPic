package com.searchpic.server.model.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "camera_device")
public class CameraDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "camera_id", nullable = false, length = 64)
    private String cameraId;

    @Column(name = "device_name", nullable = false, length = 128)
    private String deviceName;

    @Column(name = "location_name", length = 128)
    private String locationName;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
