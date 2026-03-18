package com.searchpic.server.integration.model;

import lombok.Data;

@Data
public class VlmExtractionResult {
    private Object environment;
    private Object persons;
    private Object vehicles;
    private Object animals;
    private Object packages_and_baggage;
    private Object physical_security_events;
    private Object fire_and_smoke;
    private String scene_caption;
}
