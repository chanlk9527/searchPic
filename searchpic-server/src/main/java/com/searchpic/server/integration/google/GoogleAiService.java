package com.searchpic.server.integration.google;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.searchpic.server.config.AiProperties;
import com.searchpic.server.common.exception.BusinessException;
import com.searchpic.server.integration.model.LlmParsingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Client for Google Gemini AI (LLM API for Query Understanding).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAiService {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=";

    public LlmParsingResult parseSearchQuery(String userQuery, String timezone, List<String> availableCameras) {
        String currentTime = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        
        String promptTemplate = aiProperties.getPrompts().getLlmQueryAnalyze();
        String prompt = String.format(promptTemplate, currentTime, timezone, availableCameras.toString(), userQuery);

        Map<String, Object> payload = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(Map.of("text", prompt)))
            ),
            "generationConfig", Map.of(
                "responseMimeType", "application/json"
            )
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(GEMINI_API_URL + aiProperties.getGoogle().getGemini().getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> firstCandidate = candidates.get(0);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> content = (Map<String, Object>) firstCandidate.get("content");
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            Map<String, Object> textPart = parts.get(0);
            String jsonOutput = (String) textPart.get("text");

            return objectMapper.readValue(jsonOutput, LlmParsingResult.class);
        } catch (Exception e) {
            log.error("Failed to parse query via Gemini: {}", e.getMessage());
            throw new BusinessException(5001, "LLM API Invocation Failed: " + e.getMessage());
        }
    }
}
