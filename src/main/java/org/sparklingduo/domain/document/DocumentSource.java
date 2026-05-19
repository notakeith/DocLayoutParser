package org.sparklingduo.domain.document;

import lombok.Getter;
import org.sparklingduo.infrastructure.pdf.PdfPageExtractor;

import java.util.List;

@Getter
public class DocumentSource {

    private final String originalFileName;
    private final boolean pdf;
    private final List<DocumentImage> pages;

    private DocumentSource(String fileName, boolean pdf, List<DocumentImage> pages) {
        this.originalFileName = fileName;
        this.pdf = pdf;
        this.pages = pages;
    }

    public static DocumentSource from(byte[] fileBytes, String fileName, PdfPageExtractor pdfExtractor) {
        if (isPdf(fileName, fileBytes)) {
            List<byte[]> rawPages = pdfExtractor.extractPages(fileBytes);
            List<DocumentImage> pages = new java.util.ArrayList<>();
            for (int i = 0; i < rawPages.size(); i++) {
                pages.add(new DocumentImage(rawPages.get(i), ImageFormat.JPEG,
                        fileName + "#page" + i));
            }
            return new DocumentSource(fileName, true, pages);
        } else {
            ImageFormat format = detectFormat(fileName);
            return new DocumentSource(fileName, false,
                    List.of(new DocumentImage(fileBytes, format, fileName)));
        }
    }

    public int getPageCount() {
        return pages.size();
    }

    private static boolean isPdf(String fileName, byte[] bytes) {
        if (fileName != null && fileName.toLowerCase().endsWith(".pdf")) return true;
        return bytes.length >= 4
                && bytes[0] == 0x25 && bytes[1] == 0x50
                && bytes[2] == 0x44 && bytes[3] == 0x46;
    }

    private static ImageFormat detectFormat(String fileName) {
        if (fileName == null) return ImageFormat.JPEG;
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) return ImageFormat.PNG;
        if (lower.endsWith(".tiff") || lower.endsWith(".tif")) return ImageFormat.TIFF;
        if (lower.endsWith(".bmp")) return ImageFormat.BMP;
        return ImageFormat.JPEG;
    }
}