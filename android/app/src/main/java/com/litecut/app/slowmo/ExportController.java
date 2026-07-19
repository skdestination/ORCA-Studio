package com.litecut.app.slowmo;

import android.content.Context;
import org.opencv.core.Mat;
import org.opencv.core.CvType;

public class ExportController {
    private OpticalFlowEngine opticalFlowEngine;
    private FrameInterpolator interpolator;
    private Context context;
    
    public ExportController(Context context) {
        this.context = context;
        this.interpolator = new FrameInterpolator();
    }
    
    public void initialize(String quality, int width, int height) {
        if ("high".equalsIgnoreCase(quality)) {
            opticalFlowEngine = new RAFTOpticalFlowEngine();
        } else {
            opticalFlowEngine = new DISOpticalFlowEngine();
        }
        opticalFlowEngine.initialize(context, width, height);
        interpolator.initialize(width, height);
    }
    
    public OpticalFlowEngine getOpticalFlowEngine() {
        return opticalFlowEngine;
    }

    public FrameInterpolator getInterpolator() {
        return interpolator;
    }

    public void release() {
        if (opticalFlowEngine != null) {
            opticalFlowEngine.release();
            opticalFlowEngine = null;
        }
        if (interpolator != null) {
            interpolator.release();
            interpolator = null;
        }
    }
}
