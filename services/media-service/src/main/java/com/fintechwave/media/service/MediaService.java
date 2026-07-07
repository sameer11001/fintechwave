package com.fintechwave.media.service;

import com.fintechwave.core.exception.ResourceNotFoundException;
import com.fintechwave.media.config.AwsConfig;
import com.fintechwave.media.dto.CompleteUploadRequest;
import com.fintechwave.media.dto.InitiateUploadRequest;
import com.fintechwave.media.dto.InitiateUploadResponse;
import com.fintechwave.media.domain.enums.MediaStatus;
import com.fintechwave.media.domain.entity.MediaUpload;
import com.fintechwave.media.repository.MediaUploadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

        private final S3Client s3Client;
        private final S3Presigner s3Presigner;
        private final AwsConfig awsConfig;
        private final MediaUploadRepository repository;

        private static final long MULTIPART_THRESHOLD = 5 * 1024 * 1024; // 5 MB
        private static final long PART_SIZE = 5 * 1024 * 1024; // 5 MB per part

        @Transactional
        public InitiateUploadResponse initiateUpload(UUID userId, InitiateUploadRequest request) {
                UUID mediaId = UUID.randomUUID();
                String objectKey = userId.toString() + "/" + mediaId + "-" + System.currentTimeMillis();

                boolean isMultipart = request.getSizeBytes() > MULTIPART_THRESHOLD;

                MediaUpload upload = MediaUpload.builder()
                                .id(mediaId)
                                .userId(userId)
                                .objectKey(objectKey)
                                .status(MediaStatus.INITIATED)
                                .mimeType(request.getMimeType())
                                .sizeBytes(request.getSizeBytes())
                                .build();

                List<InitiateUploadResponse.PresignedPart> presignedParts = new ArrayList<>();

                if (isMultipart) {
                        // Initiate Multipart
                        var createRequest = CreateMultipartUploadRequest.builder()
                                        .bucket(awsConfig.getBucketName())
                                        .key(objectKey)
                                        .contentType(request.getMimeType())
                                        .build();
                        var createResponse = s3Client.createMultipartUpload(createRequest);
                        String uploadId = createResponse.uploadId();
                        upload.setS3UploadId(uploadId);

                        int numParts = (int) Math.ceil((double) request.getSizeBytes() / PART_SIZE);
                        for (int i = 1; i <= numParts; i++) {
                                var uploadPartRequest = UploadPartRequest.builder()
                                                .bucket(awsConfig.getBucketName())
                                                .key(objectKey)
                                                .uploadId(uploadId)
                                                .partNumber(i)
                                                .build();

                                var presignRequest = UploadPartPresignRequest.builder()
                                                .signatureDuration(Duration.ofHours(1))
                                                .uploadPartRequest(uploadPartRequest)
                                                .build();

                                PresignedUploadPartRequest presignedRequest = s3Presigner
                                                .presignUploadPart(presignRequest);
                                presignedParts.add(InitiateUploadResponse.PresignedPart.builder()
                                                .partNumber(i)
                                                .url(presignedRequest.url().toString())
                                                .build());
                        }
                } else {
                        // Single PUT
                        var putObjectRequest = PutObjectRequest.builder()
                                        .bucket(awsConfig.getBucketName())
                                        .key(objectKey)
                                        .contentType(request.getMimeType())
                                        .contentLength(request.getSizeBytes())
                                        .build();

                        var presignRequest = PutObjectPresignRequest.builder()
                                        .signatureDuration(Duration.ofHours(1))
                                        .putObjectRequest(putObjectRequest)
                                        .build();

                        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
                        presignedParts.add(InitiateUploadResponse.PresignedPart.builder()
                                        .partNumber(1)
                                        .url(presignedRequest.url().toString())
                                        .build());
                }

                repository.save(upload);

                return InitiateUploadResponse.builder()
                                .mediaId(mediaId)
                                .objectKey(objectKey)
                                .multipart(isMultipart)
                                .parts(presignedParts)
                                .build();
        }

        @Transactional
        public void completeUpload(UUID userId, UUID mediaId, CompleteUploadRequest request) {
                MediaUpload upload = repository.findByIdAndUserId(mediaId, userId)
                                .orElseThrow(() -> new ResourceNotFoundException("Media not found"));

                if (upload.getStatus() != MediaStatus.INITIATED) {
                        throw new IllegalStateException("Upload is not in INITIATED state");
                }

                if (upload.getS3UploadId() != null) {
                        // Complete Multipart
                        var completedParts = request.getParts().stream()
                                        .map(part -> software.amazon.awssdk.services.s3.model.CompletedPart.builder()
                                                        .partNumber(part.getPartNumber())
                                                        .eTag(part.getETag())
                                                        .build())
                                        .collect(Collectors.toList());

                        var completedMultipartUpload = software.amazon.awssdk.services.s3.model.CompletedMultipartUpload
                                        .builder()
                                        .parts(completedParts)
                                        .build();

                        var completeRequest = CompleteMultipartUploadRequest.builder()
                                        .bucket(awsConfig.getBucketName())
                                        .key(upload.getObjectKey())
                                        .uploadId(upload.getS3UploadId())
                                        .multipartUpload(completedMultipartUpload)
                                        .build();

                        s3Client.completeMultipartUpload(completeRequest);
                }

                upload.setStatus(MediaStatus.UPLOADED);
                repository.save(upload);
        }

        @Transactional
        public void abortUpload(UUID userId, UUID mediaId) {
                MediaUpload upload = repository.findByIdAndUserId(mediaId, userId)
                                .orElseThrow(() -> new ResourceNotFoundException("Media not found"));

                if (upload.getStatus() != MediaStatus.INITIATED) {
                        throw new IllegalStateException("Can only abort INITIATED uploads");
                }

                if (upload.getS3UploadId() != null) {
                        var abortRequest = AbortMultipartUploadRequest.builder()
                                        .bucket(awsConfig.getBucketName())
                                        .key(upload.getObjectKey())
                                        .uploadId(upload.getS3UploadId())
                                        .build();
                        s3Client.abortMultipartUpload(abortRequest);
                }

                upload.setStatus(MediaStatus.ABORTED);
                repository.save(upload);
        }

        public String getDownloadUrl(UUID mediaId) {
                // Find media across all users (for admin download) or we could enforce user
                // context check
                MediaUpload upload = repository.findById(mediaId)
                                .orElseThrow(() -> new ResourceNotFoundException("Media not found"));

                if (upload.getStatus() == MediaStatus.INITIATED || upload.getStatus() == MediaStatus.ABORTED) {
                        throw new IllegalStateException("Media is not ready for download");
                }

                var getObjectRequest = GetObjectRequest.builder()
                                .bucket(awsConfig.getBucketName())
                                .key(upload.getObjectKey())
                                .build();

                var presignRequest = GetObjectPresignRequest.builder()
                                .signatureDuration(Duration.ofMinutes(15))
                                .getObjectRequest(getObjectRequest)
                                .build();

                PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
                return presignedRequest.url().toString();
        }

        @Scheduled(fixedRate = 3600000) // Hourly
        @Transactional
        public void cleanupOrphanedUploads() {
                LocalDateTime threshold = LocalDateTime.now().minusHours(24);
                List<MediaUpload> orphaned = repository.findByStatusAndCreatedAtBefore(MediaStatus.INITIATED,
                                threshold);

                for (MediaUpload upload : orphaned) {
                        try {
                                if (upload.getS3UploadId() != null) {
                                        var abortRequest = AbortMultipartUploadRequest.builder()
                                                        .bucket(awsConfig.getBucketName())
                                                        .key(upload.getObjectKey())
                                                        .uploadId(upload.getS3UploadId())
                                                        .build();
                                        s3Client.abortMultipartUpload(abortRequest);
                                }
                                upload.setStatus(MediaStatus.ABORTED);
                                repository.save(upload);
                                log.info("Aborted orphaned upload: {}", upload.getId());
                        } catch (Exception e) {
                                log.error("Failed to abort orphaned upload: {}", upload.getId(), e);
                        }
                }
        }
}
