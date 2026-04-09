package org.sparklingduo.domain.template;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "template_entries")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
public class Entry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;

    @Enumerated(EnumType.STRING)
    private FieldType type;

    private int padding; // а-ля допуск, а то я чего-то не подумал что всё поплыть может в зависимости от того кто какую фотку загрузит...

    @Embedded
    private Box box;
}
