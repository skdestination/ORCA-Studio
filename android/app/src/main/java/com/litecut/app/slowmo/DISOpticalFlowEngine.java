package com.litecut.app.slowmo;

import android.content.Context;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.DISOpticalFlow;

public class DISOpticalFlowEngine implements OpticalFlowEngine {
    private DISOpticalFlow disFlow;
    private Mat gray1;
    private Mat gray2;

    @Override
    public void initialize(Context context, int width, int height) {
        disFlow = DISOpticalFlow.create(DISOpticalFlow.PRESET_FAST);
        gray1 = new Mat();
        gray2 = new Mat();
    }

    @Override
    public void computeFlow(Mat frame1, Mat frame2, Mat flowMat) {
        Imgproc.cvtColor(frame1, gray1, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(frame2, gray2, Imgproc.COLOR_BGR2GRAY);
        disFlow.calc(gray1, gray2, flowMat);
    }

    @Override
    public void release() {
        if (gray1 != null) {
            gray1.release();
            gray1 = null;
        }
        if (gray2 != null) {
            gray2.release();
            gray2 = null;
        }
        // DISOpticalFlow doesn't have an explicit release in Java, handled by GC/native
    }
}
