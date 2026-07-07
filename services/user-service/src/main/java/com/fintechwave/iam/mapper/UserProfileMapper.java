package com.fintechwave.iam.mapper;

import com.fintechwave.iam.domain.entity.UserProfile;
import com.fintechwave.iam.dto.response.UserProfileResponse;
import com.fintechwave.iam.query.entity.UserProfileView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserProfileMapper {

    @Mapping(target = "status", expression = "java(entity.getStatus().name())")
    @Mapping(target = "kycTier", expression = "java(entity.getKycTier().name())")
    @Mapping(target = "stripeLinked", expression = "java(entity.isStripeLinked())")
    UserProfileResponse toResponse(UserProfile entity);

    @Mapping(target = "stripeLinked", ignore = true)
    UserProfileResponse toResponseQuery(UserProfileView view);
}
