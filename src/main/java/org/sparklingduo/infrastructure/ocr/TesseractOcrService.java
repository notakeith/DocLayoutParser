package org.sparklingduo.infrastructure.ocr;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.sparklingduo.domain.port.OcrProvider;
import org.sparklingduo.domain.template.FieldType;
import org.sparklingduo.infrastructure.config.AppProperties;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

@Service
@RequiredArgsConstructor
@Slf4j
public class TesseractOcrService implements OcrProvider {

    private final AppProperties appProperties;

    static {
        System.setProperty("jna.library.path", "/opt/homebrew/lib:/usr/local/lib");
    }

    @Override
    public String extractText(byte[] imageContent, FieldType type) throws URISyntaxException {
        ITesseract tesseract = new Tesseract();

        URL resource = getClass().getClassLoader().getResource("tessdata");
        if (resource != null) {
            String path = new File(resource.toURI()).getAbsolutePath();
            tesseract.setDatapath(path);
        } else {
            tesseract.setDatapath(appProperties.getOcr().getTessdataPath());
        }

        tesseract.setLanguage(appProperties.getOcr().getLanguage());
        tesseract.setPageSegMode(appProperties.getOcr().getPageSegMode());

        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(imageContent);
            BufferedImage image = ImageIO.read(bis);

            String result = tesseract.doOCR(image);

            return postProcess(result, type);
        } catch (Exception e) {
            log.error("OCR Error", e);
            return "";
        }
    }

    private String postProcess(String text, FieldType type) {
        if (text == null) return "";
        String cleanText = text.trim();

        return switch (type) {
            case NUMERIC -> cleanText.replaceAll("[^0-9]", "");
            case DATE -> cleanText.replaceAll("[^0-9./-]", "");
            default -> cleanText;
        };
    }

    @Override
    public boolean supports(String providerName) {
        return "TESSERACT".equalsIgnoreCase(providerName);
    }
}