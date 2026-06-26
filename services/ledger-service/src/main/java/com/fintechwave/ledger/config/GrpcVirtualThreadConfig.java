package com.fintechwave.ledger.config;

import io.grpc.ServerBuilder;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
public class GrpcVirtualThreadConfig {

    @Bean
    public GrpcServerConfigurer grpcVirtualThreadExecutor() {
        return serverBuilder ->
            ((ServerBuilder<?>) serverBuilder)
                .executor(Executors.newVirtualThreadPerTaskExecutor());
    }
}
