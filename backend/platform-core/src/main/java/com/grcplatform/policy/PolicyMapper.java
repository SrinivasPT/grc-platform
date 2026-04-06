package com.grcplatform.policy;

import org.mapstruct.Mapper;

/**
 * MapStruct mapper for the policy slice. Entity-to-DTO direction only.
 */
@Mapper
public interface PolicyMapper {

    PolicyAcknowledgmentDto toDto(PolicyAcknowledgment entity);
}
