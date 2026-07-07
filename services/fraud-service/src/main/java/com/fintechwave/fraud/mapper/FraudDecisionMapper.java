package com.fintechwave.fraud.mapper;

import com.fintechwave.fraud.domain.entity.FraudDecision;
import com.fintechwave.fraud.dto.FraudDecisionResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FraudDecisionMapper {

    FraudDecisionResponse toResponse(FraudDecision entity);
}
