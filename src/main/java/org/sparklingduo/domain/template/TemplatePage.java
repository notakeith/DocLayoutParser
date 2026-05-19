package org.sparklingduo.domain.template;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "template_pages")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
public class TemplatePage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "width", nullable = false)
    private int width;

    @Column(name = "height", nullable = false)
    private int height;
}