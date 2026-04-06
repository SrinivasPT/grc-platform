package com.grcplatform.org;

import org.mapstruct.Mapper;

/**
 * MapStruct mapper for the org slice. Entity-to-DTO direction only. Prefer calling
 * {@code entity.toDto()} directly; this interface exists for use-cases that require a
 * Spring-injectable mapper bean (e.g. batch projection).
 */
@Mapper
public interface OrgMapper {

    OrgUnitDto toDto(OrganizationUnit entity);
}
