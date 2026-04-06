package org.sparklingduo.domain.template;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class Template {
    private UUID id;
    private String name;

    // для документа какого размера создавался шаблон
    private final int baseWidth;
    private final int baseHeight;

    private List<Entry> entries;
}
