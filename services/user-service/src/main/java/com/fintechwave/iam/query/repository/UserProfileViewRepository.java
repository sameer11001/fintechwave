package com.fintechwave.iam.query.repository;

import com.fintechwave.iam.query.entity.UserProfileView;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserProfileViewRepository extends MongoRepository<UserProfileView, UUID> {
    Optional<UserProfileView> findByKeycloakId(UUID keycloakId);
}
