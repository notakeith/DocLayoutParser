package org.sparklingduo.domain.dto.response;

public record BoxDto(
        int x,
        int y,
        int width,
        int height,
        int pageNumber
) {}