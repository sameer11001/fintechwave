package com.fintechwave.media.dto;

import lombok.Data;

import java.util.List;

@Data
public class CompleteUploadRequest {
    private List<CompletedPart> parts;

    @Data
    public static class CompletedPart {
        private int partNumber;
        private String eTag;
    }
}
