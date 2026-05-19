package org.sparklingduo.domain.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sparklingduo.domain.dto.request.TemplateCreateDto;
import org.sparklingduo.domain.dto.response.EntryDto;
import org.sparklingduo.domain.template.Entry;
import org.sparklingduo.domain.template.Template;

@Mapper(componentModel = "spring")
public interface TemplateMapper {

    @Mapping(target = "id",        ignore = true)
    @Mapping(target = "pageCount", ignore = true)
    @Mapping(target = "pages",     ignore = true)
    Template toEntity(TemplateCreateDto dto);

    @Mapping(target = "id", ignore = true)
    Entry toEntry(EntryDto dto);
}