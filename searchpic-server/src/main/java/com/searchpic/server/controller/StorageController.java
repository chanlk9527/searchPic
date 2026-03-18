package com.searchpic.server.controller;

import com.searchpic.server.common.context.TenantContextHolder;
import com.searchpic.server.common.result.Result;
import com.searchpic.server.integration.oss.OssService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
public class StorageController {

    private final OssService ossService;

    @PostMapping("/upload")
    public Result<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        String currentTenantId = TenantContextHolder.getTenantId();
        
        log.info("Receiving file upload array sizes {} for Tenant {}", file.getSize(), currentTenantId);
        String publicUrl = ossService.uploadFile(file, currentTenantId);

        return Result.success(Map.of("url", publicUrl));
    }
}
