package com.example.fairnesstracker.common.mapping;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.MapperConfig;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * Shared settings for every MapStruct mapper. Mappers only turn entities into responses; turning requests
 * into entities stays in the services, which own the rules (defaults, lookups, duplicate checks).
 * <p>
 * An unmapped response field fails the build, so a new DTO field can't silently ship as null.
 */
@MapperConfig(
        componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        uses = UtcTimes.class
)
public interface MappingConfig {
}
