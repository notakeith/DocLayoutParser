package org.sparklingduo.domain.port;

public interface DocumentAligner {
    byte[] align(byte[] incoming, byte[] reference);
}