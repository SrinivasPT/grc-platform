package com.grcplatform.control;

import org.mapstruct.Mapper;

/**
 * MapStruct mapper for the control slice. Entity-to-DTO direction only.
 */
@Mapper
public interface ControlMapper {

    ControlEffectivenessDto toDto(ControlEffectiveness entity);
}
