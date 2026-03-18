package com.searchpic.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /**
     * Keep authentication disabled by default for the current development stage.
     * Turn this on in production or when end-to-end tenant isolation is ready to test.
     */
    private boolean enabled = false;

    /**
     * Fallback tenant id used when auth is disabled.
     */
    private String defaultTenantId = "tenant_local_dev";
}
