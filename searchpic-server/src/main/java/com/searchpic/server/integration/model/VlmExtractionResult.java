package com.searchpic.server.integration.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class VlmExtractionResult {
    private String environment;
    private List<Map<String, Object>> persons;
    private List<Map<String, Object>> vehicles;
    private List<Map<String, Object>> animals;
    private List<Map<String, Object>> packages_and_baggage;
    private List<Map<String, Object>> physical_security_events;
    private List<Map<String, Object>> fire_and_smoke;
    private String scene_caption;
}
