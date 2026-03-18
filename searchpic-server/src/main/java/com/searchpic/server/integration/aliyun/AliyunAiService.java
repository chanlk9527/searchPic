package com.searchpic.server.integration.aliyun;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.searchpic.server.config.AiProperties;
import com.searchpic.server.common.exception.BusinessException;
import com.searchpic.server.integration.model.VlmExtractionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.core.ParameterizedTypeReference;

import java.util.List;
import java.util.Map;

/**
 * Client for Aliyun DashScope APIs (Qwen-VL for Images and Text Embedding for Vectors).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AliyunAiService {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    private static final String QWEN_VL_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final String EMBEDDING_URL = "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding";

    public VlmExtractionResult extractEntitiesFromImage(String imageUrl) {
        String prompt = aiProperties.getPrompts().getVlmImageAnalyze();

        // Construct compatible OpenAI vision payload for Qwen-VL-Max
        Map<String, Object> payload = Map.of(
            "model", "qwen-vl-max",
            "response_format", Map.of("type", "json_object"),
            "messages", List.of(
                Map.of(
                    "role", "user",
                    "content", List.of(
                        Map.of("type", "text", "text", prompt),
                        Map.of("type", "image_url", "image_url", Map.of("url", imageUrl))
                    )
                )
            )
        );

        try {
            Map<String, Object> response = restClient.post()
                    .uri(QWEN_VL_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + aiProperties.getAliyun().getDashscope().getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> firstChoice = choices.get(0);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
            String content = (String) message.get("content");

            return objectMapper.readValue(content, VlmExtractionResult.class);
        } catch (Exception e) {
            log.error("Failed to parse image from DashScope: {}", e.getMessage());
            throw new BusinessException(5002, "VLM API Invocation Failed: " + e.getMessage());
        }
    }

    public List<Float> generateEmbedding(String text) {
        Map<String, Object> payload = Map.of(
            "model", "text-embedding-v2",
            "input", Map.of("texts", List.of(text)),
            "parameters", Map.of("text_type", "document")
        );

        try {
            Map<String, Object> response = restClient.post()
                    .uri(EMBEDDING_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + aiProperties.getAliyun().getDashscope().getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-DashScope-Async", "enable")
                    .body(payload)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            @SuppressWarnings("unchecked")
            Map<String, Object> output = (Map<String, Object>) response.get("output");
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> embeddings = (List<Map<String, Object>>) output.get("embeddings");
            Map<String, Object> embeddingNode = embeddings.get(0);
            
            @SuppressWarnings("unchecked")
            List<Float> embeddingResult = (List<Float>) embeddingNode.get("embedding");
            return embeddingResult;
        } catch (RuntimeException e) {
            log.error("Failed to generate embedding: {}", e.getMessage());
            throw new BusinessException(5003, "Embedding API Invocation Failed: " + e.getMessage());
        }
    }
}
