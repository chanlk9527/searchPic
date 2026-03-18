package com.searchpic.server.controller;

import com.searchpic.server.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.searchpic.server.common.context.TenantContextHolder;

import com.searchpic.server.service.SearchService;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Natural-language search APIs for surveillance image retrieval.")
public class SearchController {

    private final SearchService searchService;

    @Data
    @Schema(description = "Natural-language search request")
    public static class SearchQueryRequest {
        @Schema(description = "User query in natural language", example = "昨天下午有谁在门口拿走了我的快递贴纸？")
        private String query;

        @Schema(description = "User timezone in IANA format", example = "Asia/Shanghai")
        private String timezone;

        @Schema(description = "Optional camera IDs to narrow the search scope", example = "[\"cam_front_door_01\", \"cam_garage_02\"]")
        private List<String> camera_ids;
    }

    @PostMapping("/query")
    @Operation(
            summary = "Search surveillance images with natural language",
            description = "Uses an LLM to parse time, cameras, and search intent, then queries Elasticsearch with structured filters and text matching.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Search completed"),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
            }
    )
    public Result<Object> performSearch(@RequestBody SearchQueryRequest request) {
        log.info("Received search query: {}", request.getQuery());

        // Retrieve auth tenant ID
        String currentTenantId = TenantContextHolder.getTenantId();
        
        // Call SearchService and return actual Hybrid Search results.
        List<Map<String, Object>> results = searchService.searchEvents(
                currentTenantId,
                request.getQuery(),
                request.getTimezone(),
                request.getCamera_ids()
        );

        return Result.success(Map.of("results", results));
    }

    @PostMapping("/debug")
    @Operation(
            summary = "Debug natural-language search with parsed intent",
            description = "Returns the original request, parsed intent, and matched results so the web console can visualize how the query was interpreted.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Search debug completed"),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
            }
    )
    public Result<Object> debugSearch(@RequestBody SearchQueryRequest request) {
        log.info("Received search debug query: {}", request.getQuery());

        String currentTenantId = TenantContextHolder.getTenantId();
        SearchService.SearchDebugResult debugResult = searchService.searchEventsDebug(
                currentTenantId,
                request.getQuery(),
                request.getTimezone(),
                request.getCamera_ids()
        );

        return Result.success(Map.of(
                "request", debugResult.request(),
                "parsed_intent", debugResult.parsed_intent(),
                "results", debugResult.results()
        ));
    }
}
