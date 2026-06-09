package com.fintechwave.kyc.storage;

import com.fintechwave.kyc.exception.DocumentStorageException;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioStorageService implements IDocumentStorageService {

    private static final String KYC_BUCKET = "kyc-documents";
    private static final int PRESIGNED_URL_EXPIRY_MINUTES = 15;

    private final MinioClient minioClient;

    @Override
    public StorageReference upload(UUID applicationId, UUID userId, String documentType, MultipartFile file) {
        ensureBucketExists(KYC_BUCKET);

        // Format: {userId}/{applicationId}/{documentType}/{uuid}
        // No original file name stored — avoids PII leakage via object keys in logs
        String extension = extractExtension(file.getOriginalFilename());
        String objectKey = userId + "/" + applicationId + "/" + documentType.toLowerCase()
                + "/" + UUID.randomUUID() + extension;

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(KYC_BUCKET)
                            .object(objectKey)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build());

            log.info("Document uploaded: bucket={} applicationId={} documentType={}",
                    KYC_BUCKET, applicationId, documentType);
            return new StorageReference(KYC_BUCKET, objectKey);

        } catch (Exception e) {
            log.error("MinIO upload failed: applicationId={} documentType={}", applicationId, documentType);
            throw new DocumentStorageException("Failed to upload document: " + e.getMessage(), e);
        }
    }

    @Override
    public String generatePresignedUrl(String bucket, String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(PRESIGNED_URL_EXPIRY_MINUTES, TimeUnit.MINUTES)
                            .build());
        } catch (Exception e) {
            log.error("Failed to generate pre-signed URL: bucket={}", bucket);
            throw new DocumentStorageException("Failed to generate document URL: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String bucket, String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build());
            log.info("Document deleted from storage: bucket={}", bucket);
        } catch (Exception e) {
            log.error("MinIO delete failed: bucket={}", bucket);
            throw new DocumentStorageException("Failed to delete document: " + e.getMessage(), e);
        }
    }

    private void ensureBucketExists(String bucketName) {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Created MinIO bucket: {}", bucketName);
            }
        } catch (Exception e) {
            throw new DocumentStorageException("Failed to ensure bucket exists: " + e.getMessage(), e);
        }
    }

    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return "." + fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}
