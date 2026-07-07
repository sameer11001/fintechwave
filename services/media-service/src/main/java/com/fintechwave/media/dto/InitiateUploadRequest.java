package com.fintechwave.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class InitiateUploadRequest {
    @NotBlank
    private String mimeType;
    @NotNull
    @Positive
    private Long sizeBytes;
}
