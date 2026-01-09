/**
 * Created: Jun 04, 2025 2:27:57 PM
 * Copyright © 2025 by A4B. All rights reserved
 */
package com.a4b.dqes.base;

import java.util.List;

import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.MapperConfig;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import com.a4b.lib.shared.deserializer.NullFields;
import com.a4b.lib.shared.deserializer.PartialMapperUtils;


@MapperConfig(
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
    componentModel = "spring"
)
public interface BaseMapper<E, D, CreateCmd, UpdateCmd extends NullFields> {

    D toDto(E entity);

    List<D> toDtos(List<E> entities);

    void toEntity(@MappingTarget E entity, CreateCmd cmd);

    UpdateCmd toUpdateCmd(E entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    void toUpdatePartialEntity(@MappingTarget E entity, UpdateCmd cmd);

    @AfterMapping
    default void afterUpdatePartialEntity(@MappingTarget E entity, UpdateCmd cmd) {
        PartialMapperUtils.applyNull(entity, cmd);
    }
}