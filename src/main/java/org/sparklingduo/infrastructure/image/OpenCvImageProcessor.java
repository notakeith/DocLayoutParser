package org.sparklingduo.infrastructure.image;

import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.sparklingduo.domain.exception.ImageProcessingException;
import org.sparklingduo.domain.port.ImageProcessor;
import org.sparklingduo.domain.template.Box;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OpenCvImageProcessor implements ImageProcessor {

    static {
        // Инициализируем OpenCV один раз при загрузке класса
        nu.pattern.OpenCV.loadLocally();
    }

    @Override
    public byte[] prepare(byte[] imageContent) {
        return imageContent;
    }

    @Override
    public byte[] crop(byte[] imageContent, Box box) {
        Mat mat = Imgcodecs.imdecode(new MatOfByte(imageContent), Imgcodecs.IMREAD_UNCHANGED);
        if (mat.empty()) {
            throw new ImageProcessingException("Failed to decode image. Format might be corrupted.");
        }

        try {
            int x = Math.max(0, box.getX());
            int y = Math.max(0, box.getY());
            int w = Math.min(box.getWidth(), mat.width() - x);
            int h = Math.min(box.getHeight(), mat.height() - y);

            Rect rect = new Rect(x, y, w, h);
            Mat cropped = new Mat(mat, rect);

            MatOfByte buffer = new MatOfByte();
            Imgcodecs.imencode(".jpg", cropped, buffer);

            byte[] result = buffer.toArray();
            mat.release();
            cropped.release();
            return result;
        } catch (Exception e) {
            throw new ImageProcessingException("Error during cropping area: " + e.getMessage());
        } finally {
            mat.release();
        }
    }

    @Override
    public ImageSize getSize(byte[] imageContent) {
        Mat mat = Imgcodecs.imdecode(new MatOfByte(imageContent), Imgcodecs.IMREAD_UNCHANGED);
        if (mat.empty()) {
            throw new ImageProcessingException("Could not decode image");
        }
        ImageSize size = new ImageSize(mat.width(), mat.height());
        mat.release();
        return size;
    }
}