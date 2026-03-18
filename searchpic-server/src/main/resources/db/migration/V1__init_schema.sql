-- Tenant Table
CREATE TABLE `tenant` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `tenant_id` VARCHAR(64) NOT NULL UNIQUE COMMENT 'Global unique tenant identifier',
    `name` VARCHAR(128) NOT NULL COMMENT 'Tenant name',
    `api_key` VARCHAR(128) NOT NULL UNIQUE COMMENT 'API Key for ingestion and search',
    `retention_days` INT NOT NULL DEFAULT 30 COMMENT 'Data retention period in days',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SaaS Tenants';

-- Billing Log Table
CREATE TABLE `billing_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `tenant_id` VARCHAR(64) NOT NULL,
    `api_type` VARCHAR(32) NOT NULL COMMENT 'INGEST or SEARCH',
    `tokens_used` INT NOT NULL DEFAULT 1 COMMENT 'Abstract token or points spent',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_tenant_type` (`tenant_id`, `api_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Billing & Usage Logs';

-- Insert a Mock Default Tenant for Local Testing
INSERT INTO `tenant` (`tenant_id`, `name`, `api_key`, `retention_days`) 
VALUES ('tenant_local_dev', 'Local Dev Tenant', 'sk-local-dev-123456', 365);
