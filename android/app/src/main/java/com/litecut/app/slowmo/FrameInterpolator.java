package com.litecut.app.slowmo;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

public class FrameInterpolator {
    private Mat mapAx;
    private Mat mapAy;
    private Mat mapBx;
    private Mat mapBy;
    private Mat warpedA;
    private Mat warpedB;
    
    private int width;
    private int height;
    private float[] mapAxData;
    private float[] mapAyData;
    private float[] mapBxData;
    private float[] mapByData;
    private float[] flowData;

    public void initialize(int width, int height) {
        this.width = width;
        this.height = height;
        mapAx = new Mat(height, width, CvType.CV_32FC1);
        mapAy = new Mat(height, width, CvType.CV_32FC1);
        mapBx = new Mat(height, width, CvType.CV_32FC1);
        mapBy = new Mat(height, width, CvType.CV_32FC1);
        warpedA = new Mat(height, width, CvType.CV_8UC3);
        warpedB = new Mat(height, width, CvType.CV_8UC3);

        mapAxData = new float[width * height];
        mapAyData = new float[width * height];
        mapBxData = new float[width * height];
        mapByData = new float[width * height];
        flowData = new float[width * height * 2];
    }

    public void interpolate(Mat prevFrame, Mat currFrame, Mat flowMat, float t, Mat outputFrame) {
        // Prepare warp maps
        flowMat.get(0, 0, flowData);

        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                int idx = r * width + c;
                float u = flowData[idx * 2];
                float v = flowData[idx * 2 + 1];

                // Frame A backward mapping (warp map A): src_x = current_x - t * vector_x
                mapAxData[idx] = c - t * u;
                mapAyData[idx] = r - t * v;

                // Frame B backward mapping (warp map B): src_x = current_x + (1-t) * vector_x
                mapBxData[idx] = c + (1.0f - t) * u;
                mapByData[idx] = r + (1.0f - t) * v;
            }
        }

        mapAx.put(0, 0, mapAxData);
        mapAy.put(0, 0, mapAyData);
        mapBx.put(0, 0, mapBxData);
        mapBy.put(0, 0, mapByData);

        // Backward Warping via remap
        Imgproc.remap(prevFrame, warpedA, mapAx, mapAy, Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE);
        Imgproc.remap(currFrame, warpedB, mapBx, mapBy, Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE);

        // Blend warped frames based on t
        Core.addWeighted(warpedA, 1.0 - t, warpedB, t, 0.0, outputFrame);
    }

    public void release() {
        if (mapAx != null) mapAx.release();
        if (mapAy != null) mapAy.release();
        if (mapBx != null) mapBx.release();
        if (mapBy != null) mapBy.release();
        if (warpedA != null) warpedA.release();
        if (warpedB != null) warpedB.release();
    }
}
