package com.fintechwave.media.api.grpc;

import com.fintechwave.media.domain.enums.MediaStatus;
import com.fintechwave.media.domain.entity.MediaUpload;
import com.fintechwave.media.repository.MediaUploadRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class MediaVerificationServiceImpl extends MediaVerificationServiceGrpc.MediaVerificationServiceImplBase {

    private final MediaUploadRepository repository;

    @Override
    @Transactional
    public void claimMedia(ClaimMediaRequest request, StreamObserver<ClaimMediaResponse> responseObserver) {
        try {
            UUID mediaId = UUID.fromString(request.getMediaId());
            UUID userId = UUID.fromString(request.getUserId());

            Optional<MediaUpload> uploadOpt = repository.findByIdAndUserId(mediaId, userId);

            if (uploadOpt.isEmpty()) {
                responseObserver.onNext(ClaimMediaResponse.newBuilder().setValid(false).build());
                responseObserver.onCompleted();
                return;
            }

            MediaUpload upload = uploadOpt.get();

            // Only UPLOADED media can be claimed. If already CLAIMED, reject to prevent
            // reuse.
            if (upload.getStatus() != MediaStatus.UPLOADED) {
                responseObserver.onNext(ClaimMediaResponse.newBuilder().setValid(false).build());
                responseObserver.onCompleted();
                return;
            }

            upload.setStatus(MediaStatus.CLAIMED);
            repository.save(upload);

            responseObserver.onNext(ClaimMediaResponse.newBuilder()
                    .setValid(true)
                    .setObjectKey(upload.getObjectKey())
                    .build());
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID format in ClaimMediaRequest", e);
            responseObserver.onNext(ClaimMediaResponse.newBuilder().setValid(false).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error processing ClaimMediaRequest", e);
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
