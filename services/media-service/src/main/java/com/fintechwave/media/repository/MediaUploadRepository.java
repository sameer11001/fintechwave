package com.fintechwave.media.repository;

import com.fintechwave.media.domain.enums.MediaStatus;
import com.fintechwave.media.domain.entity.MediaUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MediaUploadRepository extends JpaRepository<MediaUpload, UUID> {
    Optional<MediaUpload> findByIdAndUserId(UUID id, UUID userId);
    List<MediaUpload> findByStatusAndCreatedAtBefore(MediaStatus status, LocalDateTime dateTime);
}
