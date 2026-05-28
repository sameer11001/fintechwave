package com.fintechwave.iam.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.jwt")
@Validated
@Data
public class JwtProperties {

    @NotBlank
    private String secret;

    @Min(1)
    @Max(60)
    private int accessTokenTtlMinutes = 15;

    @Min(1)
    @Max(90)
    private int refreshTokenTtlDays = 7;
}
