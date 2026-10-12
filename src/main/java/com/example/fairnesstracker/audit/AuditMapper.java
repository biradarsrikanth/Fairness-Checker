package com.example.fairnesstracker.audit;

import com.example.fairnesstracker.audit.dto.AuditEntryResponse;
import com.example.fairnesstracker.common.mapping.MappingConfig;
import org.mapstruct.Mapper;

@Mapper(config = MappingConfig.class)
public interface AuditMapper {

    AuditEntryResponse toResponse(AuditLog entry);
}
