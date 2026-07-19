package com.litecut.app.slowmo;

import android.content.Context;
import org.opencv.core.Mat;

public interface OpticalFlowEngine {
    void initialize(Context context, int width, int height);
    void computeFlow(Mat frame1, Mat frame2, Mat flowMat);
    void release();
}
