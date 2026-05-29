package com.fintechwave.security.config;

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

    @NotBlank(message = "app.jwt.secret must not be blank")
    private String secret;

    /** Access token lifetime in minutes. Default: 15. Range: 1–60. */
    @Min(1)
    @Max(60)
    private int accessTokenTtlMinutes = 15;

    /** Refresh token lifetime in days. Default: 7. Range: 1–90. */
    @Min(1)
    @Max(90)
    private int refreshTokenTtlDays = 7;
}
