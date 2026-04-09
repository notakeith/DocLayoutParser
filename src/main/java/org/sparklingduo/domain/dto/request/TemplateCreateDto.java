package org.sparklingduo.domain.dto.request;

import lombok.Data;
import org.sparklingduo.domain.dto.response.EntryDto;

import java.util.List;

@Data
public class TemplateCreateDto {
    private String name;
    private int baseWidth;
    private int baseHeight;
    private List<EntryDto> entries;
}