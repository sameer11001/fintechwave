package com.fintechwave.kyc.mapper;

import com.fintechwave.kyc.domain.entity.KycApplication;
import com.fintechwave.kyc.domain.enums.KycStatus;
import com.fintechwave.kyc.domain.enums.KycTier;
import com.fintechwave.kyc.dto.response.AdminKycApplicationResponse;
import com.fintechwave.kyc.dto.response.KycApplicationResponse;
import com.fintechwave.kyc.query.entity.KycApplicationView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = { KycDocumentMapper.class }, imports = { KycStatus.class, KycTier.class })
public interface KycApplicationMapper {

    KycApplicationResponse toResponse(KycApplication entity);

    @Mapping(target = "status", expression = "java(view.getStatus() != null ? KycStatus.valueOf(view.getStatus()) : null)")
    @Mapping(target = "currentTier", expression = "java(view.getCurrentTier() != null ? KycTier.valueOf(view.getCurrentTier()) : null)")
    @Mapping(target = "requestedTier", expression = "java(view.getRequestedTier() != null ? KycTier.valueOf(view.getRequestedTier()) : null)")
    KycApplicationResponse toResponseQuery(KycApplicationView view);

    @Mapping(target = "status", expression = "java(view.getStatus() != null ? KycStatus.valueOf(view.getStatus()) : null)")
    @Mapping(target = "currentTier", expression = "java(view.getCurrentTier() != null ? KycTier.valueOf(view.getCurrentTier()) : null)")
    @Mapping(target = "requestedTier", expression = "java(view.getRequestedTier() != null ? KycTier.valueOf(view.getRequestedTier()) : null)")
    AdminKycApplicationResponse toAdminResponse(KycApplicationView view);
}
