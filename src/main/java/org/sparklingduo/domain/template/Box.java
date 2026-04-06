package org.sparklingduo.domain.template;

public record Box(
    int x,
    int y,
    int width,
    int height,
    int pageNumber // 0 для первой страницы
) {}
