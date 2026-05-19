package org.sparklingduo.infrastructure.image;

import lombok.extern.slf4j.Slf4j;
import org.sparklingduo.domain.port.DocumentAligner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(name = "openCvDocumentAligner")
@Slf4j
public class NoOpDocumentAligner implements DocumentAligner {

    @Override
    public byte[] align(byte[] incoming, byte[] reference) {
        log.debug("NoOpDocumentAligner: выравнивание пропущено");
        return incoming;
    }
}