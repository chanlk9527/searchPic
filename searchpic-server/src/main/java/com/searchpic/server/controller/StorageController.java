package com.searchpic.server.controller;

import com.searchpic.server.common.context.TenantContextHolder;
import com.searchpic.server.common.result.Result;
import com.searchpic.server.integration.oss.OssService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
@Tag(name = "Storage", description = "Upload helper APIs for hosting image assets before ingestion.")
public class StorageController {

    private final OssService ossService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload an image to object storage",
            description = "Stores the uploaded file in object storage and returns a public URL that can be reused in the ingest API.",
            security = @SecurityRequirement(name = "bearerAuth"),
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(implementation = UploadImageRequest.class)
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Upload completed"),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid API key", content = @Content)
            }
    )
    public Result<Map<String, String>> uploadImage(@RequestPart("file") MultipartFile file) {
        String currentTenantId = TenantContextHolder.getTenantId();
        
        log.info("Receiving file upload array sizes {} for Tenant {}", file.getSize(), currentTenantId);
        String publicUrl = ossService.uploadFile(file, currentTenantId);

        return Result.success(Map.of("url", publicUrl));
    }

    @Schema(name = "UploadImageRequest", description = "Multipart upload payload")
    static class UploadImageRequest {
        @Schema(description = "Image file to upload", type = "string", format = "binary")
        public MultipartFile file;
    }
}
