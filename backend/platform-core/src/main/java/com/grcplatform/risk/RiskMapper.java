package com.grcplatform.risk;

import org.mapstruct.Mapper;

/**
 * MapStruct mapper for the risk slice. Entity-to-DTO direction only.
 */
@Mapper
public interface RiskMapper {

    RiskScoreDto toDto(RiskScore entity);
}
