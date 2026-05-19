package org.sparklingduo.domain.template;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "templates")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
public class Template {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;

    @Column(name = "base_width")
    private int baseWidth;
    @Column(name = "base_height")
    private int baseHeight;

    @Column(name = "page_count")
    @Builder.Default
    private int pageCount = 1;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "template_id")
    @Builder.Default
    private List<Entry> entries = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "template_id")
    @OrderBy("pageNumber ASC")
    @Builder.Default
    private List<TemplatePage> pages = new ArrayList<>();

    public boolean hasReferencePages() {
        return pages != null && !pages.isEmpty();
    }

    public TemplatePage getPage(int pageNumber) {
        if (pages == null) return null;
        return pages.stream()
                .filter(p -> p.getPageNumber() == pageNumber)
                .findFirst()
                .orElse(null);
    }
}