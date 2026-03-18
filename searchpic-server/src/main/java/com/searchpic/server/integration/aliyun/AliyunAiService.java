package com.searchpic.server.integration.aliyun;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.searchpic.server.config.AiProperties;
import com.searchpic.server.common.exception.BusinessException;
import com.searchpic.server.integration.model.LlmParsingResult;
import com.searchpic.server.integration.model.VlmExtractionResult;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.core.ParameterizedTypeReference;

import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
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
        String normalizedImageUrl = normalizeImageInput(imageUrl);

        // Construct compatible OpenAI vision payload for Qwen-VL-Max
        Map<String, Object> payload = Map.of(
            "model", aiProperties.getAliyun().getDashscope().getModels().getVlm(),
            "response_format", Map.of("type", "json_object"),
            "messages", List.of(
                Map.of(
                    "role", "user",
                    "content", List.of(
                        Map.of("type", "text", "text", prompt),
                        Map.of("type", "image_url", "image_url", Map.of("url", normalizedImageUrl))
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

            if (Boolean.TRUE.equals(aiProperties.getDebugLogEnabled())) {
                log.info("DashScope VLM raw response: {}", objectMapper.writeValueAsString(response));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> firstChoice = choices.get(0);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
            String content = (String) message.get("content");

            if (Boolean.TRUE.equals(aiProperties.getDebugLogEnabled())) {
                log.info("DashScope VLM parsed JSON output: {}", content);
            }

            return objectMapper.readValue(content, VlmExtractionResult.class);
        } catch (Exception e) {
            log.error("Failed to parse image from DashScope: {}", e.getMessage());
            throw new BusinessException(5002, "VLM API Invocation Failed: " + e.getMessage());
        }
    }

    public LlmParsingResult parseSearchQuery(String userQuery, String timezone, List<String> availableCameras) {
        String promptTemplate = aiProperties.getPrompts().getLlmQueryAnalyze();
        String prompt = String.format(promptTemplate,
                java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                timezone,
                availableCameras == null ? List.of() : availableCameras,
                userQuery);

        Map<String, Object> payload = Map.of(
                "model", aiProperties.getAliyun().getDashscope().getModels().getSearch(),
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", List.of(
                                        Map.of("type", "text", "text", prompt)
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

            if (Boolean.TRUE.equals(aiProperties.getDebugLogEnabled())) {
                log.info("DashScope search raw response: {}", objectMapper.writeValueAsString(response));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> firstChoice = choices.get(0);

            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
            String content = (String) message.get("content");

            if (Boolean.TRUE.equals(aiProperties.getDebugLogEnabled())) {
                log.info("DashScope search parsed JSON output: {}", content);
            }

            return objectMapper.readValue(content, LlmParsingResult.class);
        } catch (Exception e) {
            log.error("Failed to parse query from DashScope: {}", e.getMessage());
            throw new BusinessException(5001, "Aliyun search LLM API Invocation Failed: " + e.getMessage());
        }
    }

    private String normalizeImageInput(String imageUrl) {
        if (!org.springframework.util.StringUtils.hasText(imageUrl)) {
            throw new BusinessException(400, "image_url is required");
        }

        URI uri;
        try {
            uri = URI.create(imageUrl.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(400, "image_url must be a valid URI");
        }

        String scheme = uri.getScheme();
        if (!org.springframework.util.StringUtils.hasText(scheme)) {
            throw new BusinessException(400, "image_url must include a URI scheme");
        }

        if ("data".equalsIgnoreCase(scheme)) {
            return imageUrl.trim();
        }

        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new BusinessException(400, "image_url must use http(s) or data URI");
        }

        if (isPublicHttpUrl(uri)) {
            return imageUrl.trim();
        }

        return downloadAsDataUrl(imageUrl.trim());
    }

    private boolean isPublicHttpUrl(URI uri) {
        String host = uri.getHost();
        if (!org.springframework.util.StringUtils.hasText(host)) {
            return false;
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalizedHost)
                || "host.docker.internal".equals(normalizedHost)
                || "0.0.0.0".equals(normalizedHost)
                || "::1".equals(normalizedHost)) {
            return false;
        }

        if (normalizedHost.startsWith("127.")
                || normalizedHost.startsWith("10.")
                || normalizedHost.startsWith("192.168.")
                || normalizedHost.endsWith(".local")
                || !normalizedHost.contains(".")) {
            return false;
        }

        if (normalizedHost.startsWith("172.")) {
            String[] parts = normalizedHost.split("\\.");
            if (parts.length > 1) {
                try {
                    int secondOctet = Integer.parseInt(parts[1]);
                    if (secondOctet >= 16 && secondOctet <= 31) {
                        return false;
                    }
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }

        return true;
    }

    private String downloadAsDataUrl(String imageUrl) {
        try {
            ResponseEntity<byte[]> response = restClient.get()
                    .uri(imageUrl)
                    .retrieve()
                    .toEntity(byte[].class);

            byte[] body = response.getBody();
            if (body == null || body.length == 0) {
                throw new BusinessException(5002, "Image download returned empty content");
            }

            MediaType mediaType = response.getHeaders().getContentType();
            String mimeType = inferMimeType(mediaType, imageUrl);
            String base64 = Base64.getEncoder().encodeToString(body);
            log.info("Converted non-public image URL to data URI for DashScope: {}", imageUrl);
            return "data:" + mimeType + ";base64," + base64;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to download image for DashScope: {}", ex.getMessage());
            throw new BusinessException(5002, "Unable to access image_url from server side: " + ex.getMessage());
        }
    }

    private String inferMimeType(MediaType mediaType, String imageUrl) {
        if (mediaType != null && mediaType.getType() != null && "image".equalsIgnoreCase(mediaType.getType())) {
            return mediaType.toString();
        }

        String lowerUrl = imageUrl.toLowerCase(Locale.ROOT);
        if (lowerUrl.endsWith(".png")) {
            return MediaType.IMAGE_PNG_VALUE;
        }
        if (lowerUrl.endsWith(".gif")) {
            return MediaType.IMAGE_GIF_VALUE;
        }
        if (lowerUrl.endsWith(".webp")) {
            return "image/webp";
        }

        return MediaType.IMAGE_JPEG_VALUE;
    }

    @SneakyThrows
    public List<Float> generateEmbedding(String text) {
        Map<String, Object> payload = Map.of(
            "model", aiProperties.getAliyun().getDashscope().getModels().getEmbedding(),
            "input", Map.of("texts", List.of(text)),
            "parameters", Map.of("text_type", "document")
        );

        try {
            Map<String, Object> response = restClient.post()
                    .uri(EMBEDDING_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + aiProperties.getAliyun().getDashscope().getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (Boolean.TRUE.equals(aiProperties.getDebugLogEnabled())) {
                log.info("DashScope embedding raw response: {}", objectMapper.writeValueAsString(response));
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> output = (Map<String, Object>) response.get("output");
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> embeddings = (List<Map<String, Object>>) output.get("embeddings");
            Map<String, Object> embeddingNode = embeddings.get(0);
            
            @SuppressWarnings("unchecked")
            List<Float> embeddingResult = (List<Float>) embeddingNode.get("embedding");
            if (Boolean.TRUE.equals(aiProperties.getDebugLogEnabled())) {
                log.info("DashScope embedding vector size: {}", embeddingResult == null ? 0 : embeddingResult.size());
            }
            return embeddingResult;
        } catch (RuntimeException e) {
            log.error("Failed to generate embedding: {}", e.getMessage());
            throw new BusinessException(5003, "Embedding API Invocation Failed: " + e.getMessage());
        }
    }
}
