package com.fintechwave.iam;

import com.fintechwave.security.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
@EnableConfigurationProperties(JwtProperties.class)
public class IamApplication {

    public static void main(String[] args) {
        SpringApplication.run(IamApplication.class, args);
    }
}
