package com.fintechwave.media.api;

import com.fintechwave.media.dto.CompleteUploadRequest;
import com.fintechwave.media.dto.InitiateUploadRequest;
import com.fintechwave.media.dto.InitiateUploadResponse;
import com.fintechwave.media.service.MediaService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
@Tag(name = "Media Service", description = "Direct-to-S3 Presigned Media Uploads")
public class MediaController {

    private final MediaService mediaService;

    @PostMapping("/upload/initiate")
    @Operation(summary = "Initiate an upload", description = "Returns presigned URLs for direct upload")
    public ResponseEntity<InitiateUploadResponse> initiateUpload(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody InitiateUploadRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        InitiateUploadResponse response = mediaService.initiateUpload(userId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/upload/{mediaId}/complete")
    @Operation(summary = "Complete an upload", description = "Marks the upload as completed and combines parts if multipart")
    public ResponseEntity<Void> completeUpload(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID mediaId,
            @Valid @RequestBody CompleteUploadRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        mediaService.completeUpload(userId, mediaId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/upload/{mediaId}/abort")
    @Operation(summary = "Abort an upload", description = "Cancels an initiated upload and cleans up S3")
    public ResponseEntity<Void> abortUpload(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID mediaId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        mediaService.abortUpload(userId, mediaId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/download/{mediaId}")
    @Operation(summary = "Get download URL", description = "Returns a short-lived presigned GET URL for viewing media")
    public ResponseEntity<Map<String, String>> getDownloadUrl(@PathVariable UUID mediaId) {
        String url = mediaService.getDownloadUrl(mediaId);
        return ResponseEntity.ok(Map.of("url", url));
    }
}
