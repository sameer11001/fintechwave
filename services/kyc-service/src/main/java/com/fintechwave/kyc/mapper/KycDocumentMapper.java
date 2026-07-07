package com.fintechwave.kyc.mapper;

import com.fintechwave.kyc.domain.entity.KycDocument;
import com.fintechwave.kyc.domain.enums.DocumentType;
import com.fintechwave.kyc.dto.response.KycDocumentResponse;
import com.fintechwave.kyc.query.entity.KycDocumentView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", imports = { DocumentType.class })
public interface KycDocumentMapper {

    @Mapping(target = "downloadUrl", expression = "java(\"/api/v1/media/download/\" + entity.getStorageKey())")
    KycDocumentResponse toResponse(KycDocument entity);

    @Mapping(target = "documentType", expression = "java(DocumentType.valueOf(view.documentType()))")
    @Mapping(target = "downloadUrl", expression = "java(\"/api/v1/media/download/\" + view.storageKey())")
    KycDocumentResponse toResponseQuery(KycDocumentView view);

    List<KycDocumentResponse> toResponseQueryList(List<KycDocumentView> views);
}
