package com.litecut.app.slowmo;

import android.content.Context;
import android.util.Log;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

public class RAFTOpticalFlowEngine implements OpticalFlowEngine {
    private static final String TAG = "RAFTOpticalFlowEngine";
    private static final String MODEL_FILE_NAME = "raft_model.onnx";

    private OrtEnvironment ortEnv;
    private OrtSession ortSession;
    private boolean isInitialized = false;

    @Override
    public void initialize(Context context, int width, int height) {

        if (isInitialized) {
            Log.d(TAG, "RAFTOpticalFlowEngine is already initialized.");
            return;
        }

        try {
            ortEnv = OrtEnvironment.getEnvironment();
            
            // Read the model from assets
            byte[] modelBytes = loadModelFromAssets(context, MODEL_FILE_NAME);
            if (modelBytes == null) {
                Log.e(TAG, "Failed to load RAFT model from assets.");
                return;
            }

            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            ortSession = ortEnv.createSession(modelBytes, options);

            isInitialized = true;
            Log.d(TAG, "RAFTOpticalFlowEngine initialized successfully.");
        } catch (OrtException e) {
            Log.e(TAG, "ONNX Runtime Exception during initialization", e);
            release();
        } catch (Exception e) {
            Log.e(TAG, "Exception during RAFTOpticalFlowEngine initialization", e);
            release();
        }
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public void release() {
        if (ortSession != null) {
            try {
                ortSession.close();
            } catch (OrtException e) {
                Log.e(TAG, "Error closing ORT Session", e);
            }
            ortSession = null;
        }

        if (ortEnv != null) {
            ortEnv.close();
            ortEnv = null;
        }

        isInitialized = false;
        Log.d(TAG, "RAFTOpticalFlowEngine released.");
    }

    private byte[] loadModelFromAssets(Context context, String fileName) {
        try (InputStream inputStream = context.getAssets().open(fileName);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

            int nRead;
            byte[] data = new byte[16384];

            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }

            return buffer.toByteArray();
        } catch (IOException e) {
            Log.e(TAG, "IOException while reading model from assets", e);
            return null;
        }
    }

        @Override
    public void computeFlow(Mat frame1, Mat frame2, Mat flowMat) {
        if (!isInitialized || ortSession == null || ortEnv == null) {
            throw new IllegalStateException("RAFTOpticalFlowEngine is not initialized.");
        }

        long startTime = System.currentTimeMillis();
        long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        Mat rgb1 = new Mat();
        Mat rgb2 = new Mat();
        
        // Ensure inputs are RGB
        Imgproc.cvtColor(frame1, rgb1, Imgproc.COLOR_BGR2RGB);
        Imgproc.cvtColor(frame2, rgb2, Imgproc.COLOR_BGR2RGB);

        int height = rgb1.rows();
        int width = rgb1.cols();

        // RAFT usually requires input dimensions to be divisible by 8
        int padHeight = (height % 8 != 0) ? height + (8 - height % 8) : height;
        int padWidth = (width % 8 != 0) ? width + (8 - width % 8) : width;

        Mat resized1 = new Mat();
        Mat resized2 = new Mat();

        if (height != padHeight || width != padWidth) {
            Imgproc.resize(rgb1, resized1, new Size(padWidth, padHeight));
            Imgproc.resize(rgb2, resized2, new Size(padWidth, padHeight));
        } else {
            resized1 = rgb1;
            resized2 = rgb2;
        }

        FloatBuffer tensorBuffer1 = matToFloatBuffer(resized1);
        FloatBuffer tensorBuffer2 = matToFloatBuffer(resized2);

        long[] shape = new long[]{1, 3, padHeight, padWidth};

        OnnxTensor tensor1 = null;
        OnnxTensor tensor2 = null;
        OrtSession.Result result = null;

        try {
            tensor1 = OnnxTensor.createTensor(ortEnv, tensorBuffer1, shape);
            tensor2 = OnnxTensor.createTensor(ortEnv, tensorBuffer2, shape);

            Set<String> inputNames = ortSession.getInputNames();
            Iterator<String> iterator = inputNames.iterator();
            String name1 = iterator.hasNext() ? iterator.next() : "input1";
            String name2 = iterator.hasNext() ? iterator.next() : "input2";

            Map<String, OnnxTensor> inputs = new HashMap<>();
            if (name2.compareTo(name1) < 0) {
                String temp = name1;
                name1 = name2;
                name2 = temp;
            }
            
            inputs.put(name1, tensor1);
            inputs.put(name2, tensor2);

            result = ortSession.run(inputs);
            
            OnnxTensor outputTensor = (OnnxTensor) result.get(0);
            float[][][][] outputFlow = (float[][][][]) outputTensor.getValue();
            
            // Output flow shape is usually [1, 2, padHeight, padWidth]
            // We need to convert it to a CV_32FC2 Mat of size [height, width]
            // where channels are (dx, dy).
            
            float[] flowData = new float[width * height * 2];
            for (int r = 0; r < height; r++) {
                for (int c = 0; c < width; c++) {
                    int idx = r * width + c;
                    flowData[idx * 2] = outputFlow[0][0][r][c]; // dx
                    flowData[idx * 2 + 1] = outputFlow[0][1][r][c]; // dy
                }
            }
            
            flowMat.create(height, width, CvType.CV_32FC2);
            flowMat.put(0, 0, flowData);

        } catch (OrtException e) {
            Log.e(TAG, "ONNX Runtime Exception during computeFlow", e);
        } finally {
            if (tensor1 != null) {
                tensor1.close();
            }
            if (tensor2 != null) {
                tensor2.close();
            }
            if (result != null) {
                result.close();
            }
            
            rgb1.release();
            rgb2.release();
            if (height != padHeight || width != padWidth) {
                resized1.release();
                resized2.release();
            }
        }
    }

    private FloatBuffer matToFloatBuffer(Mat mat) {
        int rows = mat.rows();
        int cols = mat.cols();
        int channels = mat.channels();
        
        float[] floatData = new float[rows * cols * channels];
        byte[] byteData = new byte[rows * cols * channels];
        mat.get(0, 0, byteData);
        
        int planeSize = rows * cols;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int matIdx = (r * cols + c) * channels;
                int rVal = byteData[matIdx] & 0xFF;
                int gVal = byteData[matIdx + 1] & 0xFF;
                int bVal = byteData[matIdx + 2] & 0xFF;
                
                int destIdx = r * cols + c;
                floatData[destIdx] = (float) rVal;
                floatData[planeSize + destIdx] = (float) gVal;
                floatData[2 * planeSize + destIdx] = (float) bVal;
            }
        }
        
        FloatBuffer buffer = FloatBuffer.wrap(floatData);
        buffer.rewind();
        return buffer;
    }
}
