package com.searchpic.server.integration.oss;

import com.searchpic.server.common.exception.BusinessException;
import com.searchpic.server.config.OssProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OssService {

    private final MinioClient minioClient;
    private final OssProperties ossProperties;

    @PostConstruct
    public void initBucket() {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(ossProperties.getBucketName()).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(ossProperties.getBucketName()).build());
                // Set bucket as fully public for easy fetching for VLM models
                String publicPolicy = "{\n" +
                        "  \"Version\": \"2012-10-17\",\n" +
                        "  \"Statement\": [\n" +
                        "    {\n" +
                        "      \"Effect\": \"Allow\",\n" +
                        "      \"Principal\": \"*\",\n" +
                        "      \"Action\": [\"s3:GetObject\"],\n" +
                        "      \"Resource\": [\"arn:aws:s3:::" + ossProperties.getBucketName() + "/*\"]\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}";
                minioClient.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(ossProperties.getBucketName()).config(publicPolicy).build());
                log.info("MinIO bucket '{}' created and set to public.", ossProperties.getBucketName());
            }
        } catch (Exception e) {
            log.warn("Failed to initialize MinIO bucket on startup. It might be already configured. Reason: {}", e.getMessage());
        }
    }

    public String uploadFile(MultipartFile file, String tenantId) {
        if (file.isEmpty()) {
            throw new BusinessException(400, "Cannot upload empty file.");
        }
        
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        
        // Isolate paths purely by Tenant for neatness
        String objectName = tenantId + "/" + UUID.randomUUID().toString() + extension;

        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(ossProperties.getBucketName())
                            .object(objectName)
                            .stream(is, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            // Construct accessible URL assuming endpoint is publicly reachable 
            // In a real prod domain, this could be CDN endpoint
            String fileUrl = String.format("%s/%s/%s", ossProperties.getEndpoint(), ossProperties.getBucketName(), objectName);
            log.info("Successfully uploaded file. URL: {}", fileUrl);
            return fileUrl;
        } catch (Exception e) {
            log.error("Failed to upload file to OSS: {}", e.getMessage());
            throw new BusinessException(500, "Failed to upload file to storage.");
        }
    }
}
