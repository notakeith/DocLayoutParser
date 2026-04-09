package org.sparklingduo.domain.template;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Box {
    private int x;
    private int y;
    private int width;
    private int height;
    private int pageNumber;
}
