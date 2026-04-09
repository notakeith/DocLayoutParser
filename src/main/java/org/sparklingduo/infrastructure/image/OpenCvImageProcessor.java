package org.sparklingduo.infrastructure.image;

import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;
import org.sparklingduo.domain.port.ImageProcessor;
import org.sparklingduo.domain.template.Box;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OpenCvImageProcessor implements ImageProcessor {

    static {
        // Инициализируем OpenCV один раз при загрузке класса
        nu.pattern.OpenCV.loadShared();
    }

    @Override
    public byte[] prepare(byte[] imageContent) {
        // Пока просто возвращаем как есть.
        // В будущем здесь будет поиск углов листа и Warp Perspective.
        return imageContent;
    }

    @Override
    public byte[] crop(byte[] imageContent, Box box) {
        Mat mat = Imgcodecs.imdecode(new MatOfByte(imageContent), Imgcodecs.IMREAD_UNCHANGED);

        int x = Math.max(0, box.x());
        int y = Math.max(0, box.y());
        int w = Math.min(box.width(), mat.width() - x);
        int h = Math.min(box.height(), mat.height() - y);

        Rect rect = new Rect(x, y, w, h);
        Mat cropped = new Mat(mat, rect);

        MatOfByte buffer = new MatOfByte();
        Imgcodecs.imencode(".jpg", cropped, buffer);

        byte[] result = buffer.toArray();
        mat.release();
        cropped.release();
        return result;
    }

    @Override
    public ImageSize getSize(byte[] imageContent) {
        Mat mat = Imgcodecs.imdecode(new MatOfByte(imageContent), Imgcodecs.IMREAD_UNCHANGED);
        if (mat.empty()) throw new RuntimeException("Could not decode image");
        ImageSize size = new ImageSize(mat.width(), mat.height());
        mat.release();
        return size;
    }
}