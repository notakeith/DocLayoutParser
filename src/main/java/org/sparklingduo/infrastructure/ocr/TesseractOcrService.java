package org.sparklingduo.infrastructure.ocr;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.sparklingduo.domain.port.OcrProvider;
import org.sparklingduo.domain.template.FieldType;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

@Service
@Slf4j
public class TesseractOcrService implements OcrProvider {

    @Override
    public String extractText(byte[] imageContent, FieldType type) {
        ITesseract tesseract = new Tesseract();
        tesseract.setDatapath("src/main/resources/tessdata");
        tesseract.setLanguage("rus");

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
}