package com.fintechwave.media.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class InitiateUploadResponse {
    private UUID mediaId;
    private String objectKey;
    private boolean multipart;
    private List<PresignedPart> parts;

    @Data
    @Builder
    public static class PresignedPart {
        private int partNumber;
        private String url;
    }
}
