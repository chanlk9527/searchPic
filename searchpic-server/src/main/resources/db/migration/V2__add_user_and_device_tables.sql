CREATE TABLE `tenant_user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `tenant_id` VARCHAR(64) NOT NULL,
    `user_id` VARCHAR(64) NOT NULL,
    `display_name` VARCHAR(128) NOT NULL,
    `email` VARCHAR(128) NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_tenant_user` (`tenant_id`, `user_id`),
    INDEX `idx_tenant_user_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Tenant users';

CREATE TABLE `camera_device` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `tenant_id` VARCHAR(64) NOT NULL,
    `user_id` VARCHAR(64) NOT NULL,
    `camera_id` VARCHAR(64) NOT NULL,
    `device_name` VARCHAR(128) NOT NULL,
    `location_name` VARCHAR(128) NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_tenant_camera` (`tenant_id`, `camera_id`),
    INDEX `idx_camera_device_user` (`tenant_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Tenant camera devices';

INSERT INTO `tenant_user` (`tenant_id`, `user_id`, `display_name`, `email`, `status`)
VALUES
('tenant_local_dev', 'user_alice', 'Alice', 'alice@example.com', 'ACTIVE'),
('tenant_local_dev', 'user_bob', 'Bob', 'bob@example.com', 'ACTIVE');

INSERT INTO `camera_device` (`tenant_id`, `user_id`, `camera_id`, `device_name`, `location_name`, `status`)
VALUES
('tenant_local_dev', 'user_alice', 'cam_front_door_01', 'Front Door Camera', 'Front Door', 'ACTIVE'),
('tenant_local_dev', 'user_bob', 'cam_garage_02', 'Garage Camera', 'Garage', 'ACTIVE');
