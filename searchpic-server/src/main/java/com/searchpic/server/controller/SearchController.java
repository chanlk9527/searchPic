package com.searchpic.server.controller;

import com.searchpic.server.common.result.Result;
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
public class SearchController {

    private final SearchService searchService;

    @Data
    public static class SearchQueryRequest {
        private String query;
        private String timezone;
        private List<String> camera_ids;
    }

    @PostMapping("/query")
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
}
