package org.sparklingduo.infrastructure.image;

import lombok.extern.slf4j.Slf4j;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.*;
import org.opencv.features2d.BFMatcher;
import org.opencv.features2d.ORB;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.sparklingduo.domain.port.DocumentAligner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component("openCvDocumentAligner")
@Slf4j
public class OpenCvDocumentAligner implements DocumentAligner {

    static {
        nu.pattern.OpenCV.loadLocally();
    }

    private static final int MIN_GOOD_MATCHES = 12;

    private static final float LOWE_RATIO = 0.72f;

    private static final int ORB_MAX_FEATURES = 2000;

    @Override
    public byte[] align(byte[] incoming, byte[] reference) {
        Mat incomingMat = decode(incoming);
        Mat referenceMat = decode(reference);

        try {
            return doAlign(incomingMat, referenceMat, reference);
        } finally {
            incomingMat.release();
            referenceMat.release();
        }
    }


    private byte[] doAlign(Mat incoming, Mat reference, byte[] referenceOriginalBytes) {
        Mat incomingGray = toGray(incoming);
        Mat referenceGray = toGray(reference);

        ORB orb = ORB.create(ORB_MAX_FEATURES);

        MatOfKeyPoint kpIncoming = new MatOfKeyPoint();
        MatOfKeyPoint kpReference = new MatOfKeyPoint();
        Mat descIncoming = new Mat();
        Mat descReference = new Mat();

        orb.detectAndCompute(incomingGray, new Mat(), kpIncoming, descIncoming);
        orb.detectAndCompute(referenceGray, new Mat(), kpReference, descReference);

        incomingGray.release();
        referenceGray.release();

        if (descIncoming.empty() || descReference.empty()) {
            log.warn("ORB не нашёл дескрипторов, выравнивание пропущено");
            return encode(incoming);
        }

        BFMatcher matcher = BFMatcher.create(Core.NORM_HAMMING);

        List<MatOfDMatch> knnMatches = new ArrayList<>();
        matcher.knnMatch(descIncoming, descReference, knnMatches, 2);

        descIncoming.release();
        descReference.release();

        List<DMatch> goodMatches = new ArrayList<>();
        for (MatOfDMatch pair : knnMatches) {
            DMatch[] arr = pair.toArray();
            if (arr.length >= 2 && arr[0].distance < LOWE_RATIO * arr[1].distance) {
                goodMatches.add(arr[0]);
            }
        }

        log.debug("ORB: всего совпадений={}, хороших={}", knnMatches.size(), goodMatches.size());

        if (goodMatches.size() < MIN_GOOD_MATCHES) {
            log.warn("Недостаточно хороших совпадений ({}/{}), выравнивание пропущено",
                    goodMatches.size(), MIN_GOOD_MATCHES);
            kpIncoming.release();
            kpReference.release();
            return encode(incoming);
        }

        KeyPoint[] kpInArr = kpIncoming.toArray();
        KeyPoint[] kpRefArr = kpReference.toArray();

        List<Point> srcPoints = new ArrayList<>();
        List<Point> dstPoints = new ArrayList<>();

        for (DMatch m : goodMatches) {
            srcPoints.add(kpInArr[m.queryIdx].pt);
            dstPoints.add(kpRefArr[m.trainIdx].pt);
        }

        kpIncoming.release();
        kpReference.release();

        MatOfPoint2f src2f = toMatOfPoint2f(srcPoints);
        MatOfPoint2f dst2f = toMatOfPoint2f(dstPoints);

        Mat homography = Calib3d.findHomography(src2f, dst2f, Calib3d.RANSAC, 5.0);

        src2f.release();
        dst2f.release();

        if (homography.empty()) {
            log.warn("findHomography вернул пустую матрицу, выравнивание пропущено");
            return encode(incoming);
        }

        Mat aligned = new Mat();
        Size referenceSize = new Size(reference.width(), reference.height());
        Imgproc.warpPerspective(incoming, aligned, homography, referenceSize,
                Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, new Scalar(255, 255, 255));

        homography.release();

        byte[] result = encode(aligned);
        aligned.release();

        log.info("Выравнивание выполнено успешно ({} совпадений)", goodMatches.size());
        return result;
    }

    private Mat decode(byte[] bytes) {
        Mat mat = Imgcodecs.imdecode(new MatOfByte(bytes), Imgcodecs.IMREAD_COLOR);
        if (mat.empty()) {
            throw new IllegalArgumentException("Не удалось декодировать изображение");
        }
        return mat;
    }

    private byte[] encode(Mat mat) {
        MatOfByte buffer = new MatOfByte();
        Imgcodecs.imencode(".jpg", mat, buffer);
        return buffer.toArray();
    }

    private Mat toGray(Mat color) {
        Mat gray = new Mat();
        if (color.channels() == 1) {
            return color.clone();
        }
        Imgproc.cvtColor(color, gray, Imgproc.COLOR_BGR2GRAY);
        return gray;
    }

    private MatOfPoint2f toMatOfPoint2f(List<Point> points) {
        MatOfPoint2f mat = new MatOfPoint2f();
        mat.fromList(points);
        return mat;
    }
}