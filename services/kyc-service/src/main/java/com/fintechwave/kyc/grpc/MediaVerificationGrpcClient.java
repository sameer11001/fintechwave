package com.fintechwave.kyc.grpc;

import com.fintechwave.media.api.grpc.ClaimMediaRequest;
import com.fintechwave.media.api.grpc.ClaimMediaResponse;
import com.fintechwave.media.api.grpc.MediaVerificationServiceGrpc;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class MediaVerificationGrpcClient {

    @GrpcClient("media-service")
    private MediaVerificationServiceGrpc.MediaVerificationServiceBlockingStub stub;

    public ClaimMediaResponse claimMedia(UUID mediaId, UUID userId) {
        ClaimMediaRequest request = ClaimMediaRequest.newBuilder()
                .setMediaId(mediaId.toString())
                .setUserId(userId.toString())
                .build();

        return stub.claimMedia(request);
    }
}
