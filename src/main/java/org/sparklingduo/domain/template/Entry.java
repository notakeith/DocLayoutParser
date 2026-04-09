package org.sparklingduo.domain.template;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Entry {
    private final String name;
    private final Box box;
    private final FieldType type;

    private final int padding; // а-ля допуск, а то я чего-то не подумал что всё поплыть может в зависимости от того кто какую фотку загрузит...
}
