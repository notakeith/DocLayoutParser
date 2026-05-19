package org.sparklingduo.infrastructure.pdf;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.sparklingduo.domain.exception.ImageProcessingException;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class PdfPageExtractor {

    private static final float RENDER_DPI = 200f;

    public List<byte[]> extractPages(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            List<byte[]> pages = new ArrayList<>(pageCount);
            log.debug("Конвертация PDF: {} страниц", pageCount);
            for (int i = 0; i < pageCount; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, RENDER_DPI, ImageType.RGB);
                pages.add(toJpegBytes(image));
            }
            return pages;
        } catch (IOException e) {
            throw new ImageProcessingException("Не удалось обработать PDF: " + e.getMessage());
        }
    }

    public byte[] extractPage(byte[] pdfBytes, int pageIndex) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (pageIndex >= document.getNumberOfPages()) {
                throw new IllegalArgumentException("Страница " + pageIndex + " не существует");
            }
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, RENDER_DPI, ImageType.RGB);
            return toJpegBytes(image);
        } catch (IOException e) {
            throw new ImageProcessingException("Не удалось извлечь страницу PDF: " + e.getMessage());
        }
    }

    public int getPageCount(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return document.getNumberOfPages();
        } catch (IOException e) {
            throw new ImageProcessingException("Не удалось прочитать PDF: " + e.getMessage());
        }
    }

    private byte[] toJpegBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "JPEG", out);
        return out.toByteArray();
    }
}