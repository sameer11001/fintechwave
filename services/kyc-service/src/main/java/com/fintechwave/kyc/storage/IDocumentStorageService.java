package com.fintechwave.kyc.storage;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Port for document storage operations.
 * The MinioStorageService provides the S3-compatible implementation.
 * A mock or local-disk adapter can be swapped in for testing.
 */
public interface IDocumentStorageService {

    /**
     * Uploads a document to the storage backend.
     *
     * @param applicationId KYC application ID (used in the object key path)
     * @param userId        Owner user ID (used in the object key path)
     * @param documentType  Discriminator for the object key path
     * @param file          Uploaded multipart file
     * @return Storage reference with bucket and object key
     */
    StorageReference upload(UUID applicationId, UUID userId, String documentType, MultipartFile file);

    /**
     * Generates a pre-signed URL for temporary document access.
     * URL expires in 15 minutes.
     *
     * @param bucket     MinIO bucket name
     * @param objectKey  Object key within the bucket
     * @return Pre-signed URL string
     */
    String generatePresignedUrl(String bucket, String objectKey);

    /**
     * Deletes a document from storage.
     * Only called on application cancellation — records are otherwise immutable.
     */
    void delete(String bucket, String objectKey);

    record StorageReference(String bucket, String objectKey) {}
}
