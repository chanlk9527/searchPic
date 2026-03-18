package com.searchpic.server.integration.model;

import lombok.Data;
import java.util.List;

@Data
public class LlmParsingResult {
    private String start_time;
    private String end_time;
    private List<String> camera_ids;
    private List<String> search_terms;
    private String search_intent_caption;
}
