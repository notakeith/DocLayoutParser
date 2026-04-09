package org.sparklingduo.domain.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sparklingduo.domain.dto.request.TemplateCreateDto;
import org.sparklingduo.domain.template.Template;

@Mapper(componentModel = "spring")
public interface TemplateMapper {
    @Mapping(target = "id", ignore = true)
    Template toEntity(TemplateCreateDto dto);
}