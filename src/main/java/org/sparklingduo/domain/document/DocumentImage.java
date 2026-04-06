package org.sparklingduo.domain.document;

public record DocumentImage(
        byte[] content,
        ImageFormat format,
        String filelName
) {}
