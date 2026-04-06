package org.sparklingduo.domain.port;

import org.sparklingduo.domain.template.Box;

public interface ImageProcessor {
    byte[] prepare(byte[] rawImage);

    byte[] crop(byte[] preparedImage, Box box);
}
