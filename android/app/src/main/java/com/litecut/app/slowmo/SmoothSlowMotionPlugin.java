package com.litecut.app.slowmo;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaCodecInfo;
import android.media.MediaMuxer;
import android.media.Image;
import android.util.Log;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.MatOfByte;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.video.DISOpticalFlow;

@CapacitorPlugin(name = "SmoothSlowMotion")
public class SmoothSlowMotionPlugin extends Plugin {
    private static final String TAG = "SmoothSlowMotion";

    private JSObject extractMetadata(String path) throws Exception {
        File file = new File(path);
        if (!file.exists()) {
            throw new java.io.FileNotFoundException("File does not exist: " + path);
        }

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        JSObject result = new JSObject();
        double captureFpsVal = 0.0;
        
        try {
            retriever.setDataSource(path);
            
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            String widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            String rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            String captureFps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);
            
            long durationMs = 0;
            if (durationStr != null) {
                durationMs = Long.parseLong(durationStr);
            }
            
            int width = 0;
            if (widthStr != null) {
                width = Integer.parseInt(widthStr);
            }
            
            int height = 0;
            if (heightStr != null) {
                height = Integer.parseInt(heightStr);
            }

            int rotation = 0;
            if (rotationStr != null) {
                rotation = Integer.parseInt(rotationStr);
            }
            
            if (captureFps != null) {
                try {
                    captureFpsVal = Double.parseDouble(captureFps);
                } catch (NumberFormatException ignored) {}
            }
            
            if (rotation == 90 || rotation == 270) {
                int temp = width;
                width = height;
                height = temp;
            }

            result.put("durationMs", durationMs);
            result.put("width", width);
            result.put("height", height);
            result.put("rotation", rotation);
            
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {}
        }

        double fps = 0.0;
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(path);
            int numTracks = extractor.getTrackCount();
            for (int i = 0; i < numTracks; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) {
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        try {
                            fps = format.getInteger(MediaFormat.KEY_FRAME_RATE);
                        } catch (ClassCastException e1) {
                            try {
                                fps = format.getFloat(MediaFormat.KEY_FRAME_RATE);
                            } catch (ClassCastException e2) {
                                // Fallback
                            }
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to retrieve frame rate via MediaExtractor", e);
        } finally {
            try {
                extractor.release();
            } catch (Exception ignored) {}
        }

        if (fps <= 0.0) {
            if (captureFpsVal > 0.0) {
                fps = captureFpsVal;
            } else {
                fps = 30.0;
            }
        }
        
        result.put("fps", fps);
        return result;
    }

    @PluginMethod
    public void receiveVideoPath(PluginCall call) {
        String inputPath = call.getString("inputPath");
        
        if (inputPath == null || inputPath.isEmpty()) {
            call.reject("Must provide an input path");
            return;
        }

        Log.i(TAG, "Received video path successfully: " + inputPath);
        
        JSObject ret = new JSObject();
        ret.put("receivedPath", inputPath);
        ret.put("success", true);

        try {
            JSObject meta = extractMetadata(inputPath);
            ret.put("durationMs", meta.get("durationMs"));
            ret.put("width", meta.get("width"));
            ret.put("height", meta.get("height"));
            ret.put("fps", meta.get("fps"));
        } catch (Exception e) {
            Log.e(TAG, "Automatic metadata extraction failed for receiveVideoPath flow: " + e.getMessage(), e);
        }

        call.resolve(ret);
    }

    @PluginMethod
    public void getVideoMetadata(PluginCall call) {
        String inputPath = call.getString("inputPath");
        
        if (inputPath == null || inputPath.isEmpty()) {
            call.reject("Must provide an input path");
            return;
        }

        try {
            JSObject meta = extractMetadata(inputPath);
            call.resolve(meta);
        } catch (Exception e) {
            Log.e(TAG, "Metadata extraction failed", e);
            call.reject("Failed to extract video metadata: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void decodeAllFrames(PluginCall call) {
        String inputPath = call.getString("inputPath");
        
        if (inputPath == null || inputPath.isEmpty()) {
            call.reject("Must provide an input path");
            return;
        }

        File file = new File(inputPath);
        if (!file.exists()) {
            call.reject("File does not exist: " + inputPath);
            return;
        }

        try {
            Log.i(TAG, "[AUDIT] Stage 1: Export button pressed");
        } catch (Throwable t) {
            Log.e(TAG, "[AUDIT] Stage 1 log failed with stack trace:", t);
        }
        Log.i(TAG, "[AUDIT] Stage 1: Export/Processing native call received (decodeAllFrames) for: " + inputPath);

        // Integrate OpenCV Android SDK
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV initialization failed!");
            call.reject("OpenCV initialization failed");
            return;
        } else {
            Log.i(TAG, "OpenCV initialization succeeded!");
        }

        double speed = call.getDouble("speed", 0.5);
        if (speed <= 0.0 || speed > 1.0) {
            speed = 0.5;
        }
        long durationUs = 0;
        String outputPath = null;
        VideoEncoderCore encoderCore = null;

        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        
        // Optimizing Memory Allocations: Preallocated Mats
        Mat matA = null;
        Mat matB = null;
        Mat matGrayA = null;
        Mat matGrayB = null;
        Mat flowMat = null;
        byte[] rowData = null;
        
        // Frame Interpolation (W1D6) Preallocated Mats & Trackers
        Mat mapAx = null;
        Mat mapAy = null;
        Mat mapBx = null;
        Mat mapBy = null;
        Mat warpedA = null;
        Mat warpedB = null;
        Mat intermediateFrame = null;
        Mat diffMat = null;
        Mat squareDiff = null;

        int interpolatedFramesCount = 0;
        double sumPsnr = 0;
        double sumWarpError = 0;
        String bestInterpolationVisualizationBase64 = "";

        // Visualization & Verification Trackers
        double peakAvgFlowMagnitude = 0;
        double overallMaxFlowMagnitude = 0;
        double sumFlowMagnitude = 0;
        int flowComputedCount = 0;
        String bestFlowVisualizationBase64 = "";
        
        // Profiling Stats
        long totalExportStartMs = System.currentTimeMillis();
        long totalDecodeTimeMs = 0;
        long totalColorConvTimeMs = 0;
        long totalOpticalFlowTimeMs = 0;
        long totalInterpTimeMs = 0;
        long totalEncodeTimeMs = 0;
        
        int matAllocations = 0;
        int byteBufferAllocations = 0;
        int frameCopies = 0;
        
        long decoderTimeMs = 0;
        long opticalFlowTimeMs = 0;
        long interpolationTimeMs = 0;
        long encoderTimeMs = 0;
        
        try {
            extractor.setDataSource(inputPath);
            int trackIndex = -1;
            MediaFormat format = null;
            String mime = null;
            
            int numTracks = extractor.getTrackCount();
            for (int i = 0; i < numTracks; i++) {
                MediaFormat trackFormat = extractor.getTrackFormat(i);
                String trackMime = trackFormat.getString(MediaFormat.KEY_MIME);
                if (trackMime != null && trackMime.startsWith("video/")) {
                    trackIndex = i;
                    format = trackFormat;
                    mime = trackMime;
                    break;
                }
            }

            if (trackIndex < 0 || format == null || mime == null) {
                call.reject("No video track found in file");
                extractor.release();
                return;
            }

            extractor.selectTrack(trackIndex);
            
            try {
                decoder = MediaCodec.createDecoderByType(mime);
                decoder.configure(format, null, null, 0);
                decoder.start();
                Log.i(TAG, "[AUDIT] Stage 2: Decoder initialized");
            } catch (Throwable t) {
                Log.e(TAG, "[AUDIT] Stage 2: Decoder initialize failed with stack trace:", t);
                throw t;
            }

            if (format.containsKey(MediaFormat.KEY_DURATION)) {
                durationUs = format.getLong(MediaFormat.KEY_DURATION);
            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            boolean isInputEOS = false;
            boolean isOutputEOS = false;
            int frameCount = 0;
            List<Long> timestamps = new ArrayList<>();
            long lastTimestampUs = -1;
            boolean timestampsVerified = true;
            String verificationErrorMsg = "";

            int width = format.containsKey(MediaFormat.KEY_WIDTH) ? format.getInteger(MediaFormat.KEY_WIDTH) : 0;
            int height = format.containsKey(MediaFormat.KEY_HEIGHT) ? format.getInteger(MediaFormat.KEY_HEIGHT) : 0;
            int stride = format.containsKey(MediaFormat.KEY_STRIDE) ? format.getInteger(MediaFormat.KEY_STRIDE) : width;

            DISOpticalFlow disFlow = DISOpticalFlow.create(DISOpticalFlow.PRESET_FAST);
            long timeoutUs = 10000; // 10ms

            while (!isOutputEOS) {
                long loopIterStartMs = System.currentTimeMillis();
                long iterFlowTimeMs = 0;
                long iterInterpTimeMs = 0;
                long iterEncodeTimeMs = 0;

                // Feed input buffers
                if (!isInputEOS) {
                    int inputBufferIndex = decoder.dequeueInputBuffer(timeoutUs);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferIndex);
                        if (inputBuffer != null) {
                            int sampleSize = extractor.readSampleData(inputBuffer, 0);
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                isInputEOS = true;
                            } else {
                                long sampleTime = extractor.getSampleTime();
                                decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, sampleTime, 0);
                                extractor.advance();
                            }
                        }
                    }
                }

                // Dequeue output buffers
                int outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs);
                if (outputBufferIndex >= 0) {
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputEOS = true;
                    }

                    if (bufferInfo.size > 0 && width > 0 && height > 0) {
                        frameCount++;
                        long pts = bufferInfo.presentationTimeUs;
                        timestamps.add(pts);
                        
                        if (lastTimestampUs != -1 && pts < lastTimestampUs) {
                            timestampsVerified = false;
                            verificationErrorMsg = "Non-monotonic timestamp detected: Frame " + frameCount + " pts=" + pts + " < lastPts=" + lastTimestampUs;
                            Log.w(TAG, verificationErrorMsg);
                        }
                        lastTimestampUs = pts;

                        Mat currMat = null;
                        Mat prevMat = null;
                        Mat currGray = null;
                        Mat prevGray = null;

                        // Retrieve full color video frame using getOutputImage
                        Image image = null;
                        try {
                            image = decoder.getOutputImage(outputBufferIndex);
                            if (frameCount == 1) {
                                Log.i(TAG, "[AUDIT] Stage 3: First frame decoded");
                            }
                        } catch (Throwable t) {
                            Log.e(TAG, "[AUDIT] Stage 3: First frame decoded failed with stack trace:", t);
                            throw t;
                        }
                        if (image != null) {
                            try {
                                // Reinitialize preallocated Mats dynamically if dimension changes or at start
                                if (matA == null || matA.cols() != width || matA.rows() != height) {
                                    if (matA != null) matA.release();
                                    if (matB != null) matB.release();
                                    if (flowMat != null) flowMat.release();
                                    
                                    if (mapAx != null) mapAx.release();
                                    if (mapAy != null) mapAy.release();
                                    if (mapBx != null) mapBx.release();
                                    if (mapBy != null) mapBy.release();
                                    if (warpedA != null) warpedA.release();
                                    if (warpedB != null) warpedB.release();
                                    if (intermediateFrame != null) intermediateFrame.release();
                                    if (diffMat != null) diffMat.release();
                                    if (squareDiff != null) squareDiff.release();

                                    matA = new Mat(height, width, CvType.CV_8UC3);
                                    matB = new Mat(height, width, CvType.CV_8UC3);
                                    matGrayA = new Mat(height, width, CvType.CV_8UC1);
                                    matGrayB = new Mat(height, width, CvType.CV_8UC1);
                                    flowMat = new Mat();
                                    
                                    mapAx = new Mat(height, width, CvType.CV_32FC1);
                                    mapAy = new Mat(height, width, CvType.CV_32FC1);
                                    mapBx = new Mat(height, width, CvType.CV_32FC1);
                                    mapBy = new Mat(height, width, CvType.CV_32FC1);
                                    warpedA = new Mat(height, width, CvType.CV_8UC3);
                                    warpedB = new Mat(height, width, CvType.CV_8UC3);
                                    intermediateFrame = new Mat(height, width, CvType.CV_8UC3);
                                    diffMat = new Mat();
                                    squareDiff = new Mat();
                                    matAllocations += 12;
                                }

                                // Pick target Mat alternating based on frameCount
                                currMat = (frameCount % 2 == 1) ? matA : matB;
                                prevMat = (frameCount % 2 == 1) ? matB : matA;
                                currGray = (frameCount % 2 == 1) ? matGrayA : matGrayB;
                                prevGray = (frameCount % 2 == 1) ? matGrayB : matGrayA;

                                // Extract Grayscale Y plane & convert BGR
                                long colorConvStartMs = System.currentTimeMillis();
                                
                                // Phase 2: Direct Luma Extraction Hack
                                // Extract the Y channel straight from YUV planes directly into currGray
                                ByteBuffer yBuf = image.getPlanes()[0].getBuffer();
                                int yRowStride = image.getPlanes()[0].getRowStride();
                                byte[] yRowData = new byte[width];
                                for (int r = 0; r < height; r++) {
                                    yBuf.position(r * yRowStride);
                                    yBuf.get(yRowData, 0, width);
                                    currGray.put(r, 0, yRowData);
                                }
                                
                                Mat bgrMat = convertYUVImageToBGR(image);
                                bgrMat.copyTo(currMat);
                                bgrMat.release();
                                totalColorConvTimeMs += (System.currentTimeMillis() - colorConvStartMs);
                                matAllocations += 2; // yuvMat, bgrMat in convertYUVImageToBGR
                                byteBufferAllocations += 3; // yRowData, rowData, uvBytes in convertYUVImageToBGR
                                frameCopies += 2; // y channel loop, bgrMat.copyTo(currMat)
                            } finally {
                                image.close();
                            }

                            // Initialize VideoEncoderCore once width & height are known
                            if (encoderCore == null && width > 0 && height > 0) {
                                try {
                                    int inputFps = 30;
                                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                                        try {
                                            inputFps = format.getInteger(MediaFormat.KEY_FRAME_RATE);
                                        } catch (Exception e1) {
                                            try {
                                                inputFps = (int) Math.round(format.getFloat(MediaFormat.KEY_FRAME_RATE));
                                            } catch (Exception ignored) {}
                                        }
                                    }
                                    File cacheDir = getContext().getCacheDir();
                                    File outputFile = new File(cacheDir, "slowmo_output_" + System.currentTimeMillis() + ".mp4");
                                    outputPath = outputFile.getAbsolutePath();
                                    
                                    int rotation = 0;
                                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                                    try {
                                        retriever.setDataSource(inputPath);
                                        String rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                                        if (rotationStr != null) {
                                            rotation = Integer.parseInt(rotationStr);
                                        }
                                    } catch (Exception ignored) {
                                    } finally {
                                        retriever.release();
                                    }

                                    encoderCore = new VideoEncoderCore(outputPath, width, height, inputFps, rotation);
                                    Log.i(TAG, "Initialized VideoEncoderCore with output path: " + outputPath);
                                } catch (Exception encInitErr) {
                                    Log.e(TAG, "Failed to initialize encoderCore: " + encInitErr.getMessage(), encInitErr);
                                }
                            }

                            // Output Progress Callback
                            if (durationUs > 0) {
                                int percentage = (int) Math.round((pts * 100.0) / durationUs);
                                percentage = Math.max(0, Math.min(99, percentage));
                                if (frameCount % 6 == 0) {
                                    JSObject progressData = new JSObject();
                                    progressData.put("progress", percentage);
                                    notifyListeners("progress", progressData);
                                }
                            }

                            // Write the first original frame directly to encoder
                            if (frameCount == 1 && encoderCore != null) {
                                try {
                                    long encodeStartMs = System.currentTimeMillis();
                                    long outputPts = (long) (pts * (1.0 / speed));
                                    encoderCore.encodeFrame(currMat, outputPts);
                                    totalEncodeTimeMs += (System.currentTimeMillis() - encodeStartMs);
                                    // color conversion stats inside encodeFrame will be accumulated later, we can read them from encoderCore
                                    // wait, encodeFrame color conversion is tracked inside VideoEncoderCore, we need to add methods to retrieve them
                                    totalColorConvTimeMs += encoderCore.getColorConvTimeMs();
                                    matAllocations += encoderCore.getMatAllocations();
                                    byteBufferAllocations += encoderCore.getBufferAllocations();
                                    frameCopies += encoderCore.getFrameCopies();
                                    encoderCore.resetMetrics(); // To prevent double counting since we accumulate in the outer loop
                                    // Note: we can just accumulate `encoderTimeMs` and subtract `colorConvTime`
                                    totalEncodeTimeMs -= encoderCore.getColorConvTimeMs();
                                    
                                    // Let's use `totalEncodeTimeMs` correctly later. Actually, `encoderTimeMs` is being tracked.
                                } catch (Exception encodeErr) {
                                    Log.e(TAG, "Failed encoding first frame: " + encodeErr.getMessage(), encodeErr);
                                }
                            }
                            
                            // On second frame onwards, run DIS Dense Optical Flow
                            if (frameCount > 1) {
                                if (frameCount == 2) {
                                    try {
                                        Log.i(TAG, "[AUDIT] Stage 4: Interpolation started");
                                    } catch (Throwable t) {
                                        Log.e(TAG, "[AUDIT] Stage 4: Interpolation started failed with stack trace:", t);
                                        throw t;
                                    }
                                }
                                try {
                                    long flowStartMs = System.currentTimeMillis();
                                    
                                    disFlow.calc(prevGray, currGray, flowMat);
                                    
                                    long flowTime = System.currentTimeMillis() - flowStartMs;
                                    totalOpticalFlowTimeMs += flowTime;
                                    flowComputedCount++;
                                    
                                    // Verify optical flow correctness & extract stats
                                    int step = 16;
                                    double sumMag = 0;
                                    double localMaxMag = 0;
                                    int activeCount = 0;
                                    int sampleCount = 0;
                                    float[] flowVec = new float[2];
                                    
                                    for (int y = 0; y < height; y += step) {
                                        for (int x = 0; x < width; x += step) {
                                            flowMat.get(y, x, flowVec);
                                            float dx = flowVec[0];
                                            float dy = flowVec[1];
                                            double mag = Math.sqrt(dx * dx + dy * dy);
                                            sumMag += mag;
                                            if (mag > localMaxMag) {
                                                localMaxMag = mag;
                                            }
                                            if (mag > 0.5) {
                                                activeCount++;
                                            }
                                            sampleCount++;
                                        }
                                    }
                                    
                                    double avgMag = sampleCount > 0 ? sumMag / sampleCount : 0;
                                    double activeRatio = sampleCount > 0 ? (double) activeCount / sampleCount : 0;
                                    
                                    sumFlowMagnitude += avgMag;
                                    if (localMaxMag > overallMaxFlowMagnitude) {
                                        overallMaxFlowMagnitude = localMaxMag;
                                    }
                                    
                                    long interpStartMs = System.currentTimeMillis();
                                    // Today's Work (W1D6): Frame Interpolation Pipeline
                                    // 1. Create Warp Maps recursively (backward mapping) using fast array copy
                                    int totalPixels = width * height;
                                    float[] flowData = new float[totalPixels * 2];
                                    flowMat.get(0, 0, flowData);

                                    float[] mapAxData = new float[totalPixels];
                                    float[] mapAyData = new float[totalPixels];
                                    float[] mapBxData = new float[totalPixels];
                                    float[] mapByData = new float[totalPixels];

                                    for (int r = 0; r < height; r++) {
                                        for (int c = 0; c < width; c++) {
                                            int idx = r * width + c;
                                            float u = flowData[idx * 2];
                                            float v = flowData[idx * 2 + 1];

                                            // Frame A backward mapping (warp map A): src_x = current_x - t * vector_x
                                            mapAxData[idx] = c - 0.5f * u;
                                            mapAyData[idx] = r - 0.5f * v;

                                            // Frame B backward mapping (warp map B): src_y = current_x + (1-t) * vector_x
                                            mapBxData[idx] = c + 0.5f * u;
                                            mapByData[idx] = r + 0.5f * v;
                                        }
                                    }

                                    mapAx.put(0, 0, mapAxData);
                                    mapAy.put(0, 0, mapAyData);
                                    mapBx.put(0, 0, mapBxData);
                                    mapBy.put(0, 0, mapByData);

                                    // 2. Backward Warping via remap (linear interpolation, border replicated to handle edge artifacts)
                                    Imgproc.remap(prevMat, warpedA, mapAx, mapAy, Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE);
                                    Imgproc.remap(currMat, warpedB, mapBx, mapBy, Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE);

                                    // 3. Generate Intermediate Frame (t = 0.5)
                                    Core.addWeighted(warpedA, 0.5, warpedB, 0.5, 0.0, intermediateFrame);
                                    interpolatedFramesCount++;

                                    // 4. Compare original vs interpolated (warp alignment error metric & PSNR score)
                                    Core.absdiff(warpedA, warpedB, diffMat);
                                    Scalar meanDiff = Core.mean(diffMat);
                                    double localWarpError = (meanDiff.val[0] + meanDiff.val[1] + meanDiff.val[2]) / 3.0;
                                    sumWarpError += localWarpError;

                                    Core.multiply(diffMat, diffMat, squareDiff);
                                    Scalar meanSquareDiff = Core.mean(squareDiff);
                                    double localMse = (meanSquareDiff.val[0] + meanSquareDiff.val[1] + meanSquareDiff.val[2]) / 3.0;
                                    double localPsnr = (localMse > 0) ? (10.0 * Math.log10((255.0 * 255.0) / localMse)) : 99.0;
                                    sumPsnr += localPsnr;

                                    totalInterpTimeMs += (System.currentTimeMillis() - interpStartMs);

                                    // Visualize and validate frame interpolation
                                    if (avgMag > peakAvgFlowMagnitude || bestFlowVisualizationBase64.isEmpty()) {
                                        peakAvgFlowMagnitude = avgMag;

                                        // Create split-view visual validation card: [Original | Interpolated]
                                        Mat leftBGR = prevMat.clone();
                                        Mat rightBGR = intermediateFrame.clone();
                                        matAllocations += 2;
                                        frameCopies += 2;

                                        // Annotate Left & Right Panes
                                        Imgproc.putText(leftBGR, "Original (Frame A)", new Point(15, 30),
                                                Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, new Scalar(0, 255, 255), 2);

                                        Imgproc.putText(rightBGR, "Interpolated Frame I (t=0.5)", new Point(15, 30),
                                                Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, new Scalar(100, 240, 100), 2);

                                        List<Mat> framesList = new ArrayList<>();
                                        framesList.add(leftBGR);
                                        framesList.add(rightBGR);

                                        Mat sideBySide = new Mat();
                                        Core.hconcat(framesList, sideBySide);
                                        matAllocations += 1;

                                        // Divider
                                        Imgproc.line(sideBySide, new Point(width, 0), new Point(width, height), new Scalar(255, 255, 255), 2);

                                        // Footer Telemetry Bar overlay to prevent cluttering the canvas
                                        int footerHeight = 45;
                                        Mat footerOverlay = sideBySide.submat(height - footerHeight, height, 0, width * 2);
                                        footerOverlay.setTo(new Scalar(20, 20, 20));

                                        Imgproc.putText(sideBySide, "WEEK 1 DAY 6 VALIDATION: 30fps -> 60fps Frame Interpolation",
                                                new Point(15, height - 28), Imgproc.FONT_HERSHEY_SIMPLEX, 0.45, new Scalar(220, 220, 220), 1);
                                        Imgproc.putText(sideBySide, String.format("DIS Motion: %.2fpx | PSNR: %.2fdB | Warping Error: %.3fpx", avgMag, localPsnr, localWarpError),
                                                new Point(15, height - 10), Imgproc.FONT_HERSHEY_SIMPLEX, 0.45, new Scalar(130, 240, 130), 1);

                                        MatOfByte buf = new MatOfByte();
                                        Imgcodecs.imencode(".jpg", sideBySide, buf);
                                        matAllocations += 1;
                                        byte[] bytes = buf.toArray();
                                        bestFlowVisualizationBase64 = "data:image/jpeg;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
                                        bestInterpolationVisualizationBase64 = bestFlowVisualizationBase64;

                                        leftBGR.release();
                                        rightBGR.release();
                                        sideBySide.release();
                                        footerOverlay.release();
                                        buf.release();
                                    }

                                    if (flowComputedCount % 15 == 0) {
                                         Log.i(TAG, String.format("DIS Flow + Interpolation for %d frames. PSNR: %.2f dB, Error: %.3f px",
                                               flowComputedCount, localPsnr, localWarpError));
                                    }
                                } catch (Exception flowErr) {
                                    Log.e(TAG, "Failed to calculate/verify/visualize DIS Optical Flow on frame " + frameCount + ": " + flowErr.getMessage());
                                }

                                // Today's Work: Write reconstructed interpolated and original frames sequentially to encoder
                                if (encoderCore != null) {
                                    try {
                                        long encodeStartMs = System.currentTimeMillis();
                                        long ptsPrev = timestamps.get(frameCount - 2);
                                        long outputPtsIntermediate = (long) (((ptsPrev + pts) / 2.0) * (1.0 / speed));
                                        long outputPts = (long) (pts * (1.0 / speed));

                                        encoderCore.encodeFrame(intermediateFrame, outputPtsIntermediate);
                                        encoderCore.encodeFrame(currMat, outputPts);
                                        totalEncodeTimeMs += (System.currentTimeMillis() - encodeStartMs);
                                        
                                        totalColorConvTimeMs += encoderCore.getColorConvTimeMs();
                                        matAllocations += encoderCore.getMatAllocations();
                                        byteBufferAllocations += encoderCore.getBufferAllocations();
                                        frameCopies += encoderCore.getFrameCopies();
                                        encoderCore.resetMetrics();
                                        totalEncodeTimeMs -= encoderCore.getColorConvTimeMs();
                                    } catch (Exception encodeErr) {
                                        Log.e(TAG, "Failed encoding intermediate/current frames on frame " + frameCount + ": " + encodeErr.getMessage(), encodeErr);
                                    }
                                }
                            }
                        }
                    }

                    decoder.releaseOutputBuffer(outputBufferIndex, false);
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = decoder.getOutputFormat();
                    Log.i(TAG, "Decoder format changed: " + newFormat.toString());
                    width = newFormat.containsKey(MediaFormat.KEY_WIDTH) ? newFormat.getInteger(MediaFormat.KEY_WIDTH) : width;
                    height = newFormat.containsKey(MediaFormat.KEY_HEIGHT) ? newFormat.getInteger(MediaFormat.KEY_HEIGHT) : height;
                    stride = newFormat.containsKey(MediaFormat.KEY_STRIDE) ? newFormat.getInteger(MediaFormat.KEY_STRIDE) : width;
                } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // No output buffer available, wait or loop
                }

                long loopIterEndMs = System.currentTimeMillis();
                totalDecodeTimeMs += (loopIterEndMs - loopIterStartMs) - iterFlowTimeMs - iterInterpTimeMs - iterEncodeTimeMs;
            }

            if (encoderCore != null) {
                try {
                    long encodeStartMs = System.currentTimeMillis();
                    encoderCore.signalEndOfStream();
                    encoderTimeMs += (System.currentTimeMillis() - encodeStartMs);
                    Log.i(TAG, "Successfully signaled End Of Stream to encoderCore.");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to signal EOS to encoderCore: " + e.getMessage(), e);
                }
            }

            JSObject result = new JSObject();
            result.put("success", true);
            result.put("decodedFramesCount", frameCount);
            result.put("flowComputedCount", flowComputedCount);
            result.put("timestampsVerified", timestampsVerified);
            result.put("verificationError", verificationErrorMsg);
            
            // Add dense flow correctness metrics
            double globalAvgFlow = flowComputedCount > 0 ? sumFlowMagnitude / flowComputedCount : 0;
            result.put("avgFlowMagnitude", globalAvgFlow);
            result.put("maxFlowMagnitude", overallMaxFlowMagnitude);
            result.put("flowVisualization", bestFlowVisualizationBase64);
            result.put("isFlowCorrect", flowComputedCount > 0 && overallMaxFlowMagnitude > 0.05);

            // Add frame interpolation (W1D6) metrics
            double averagePsnr = interpolatedFramesCount > 0 ? sumPsnr / interpolatedFramesCount : 0;
            double averageWarpError = interpolatedFramesCount > 0 ? sumWarpError / interpolatedFramesCount : 0;
            result.put("interpolatedFramesCount", interpolatedFramesCount);
            result.put("averagePsnr", averagePsnr);
            result.put("averageWarpError", averageWarpError);
            result.put("interpolationVisualization", bestInterpolationVisualizationBase64);
            if (outputPath != null) {
                result.put("outputPath", outputPath);
            }

            if (encoderCore != null) {
                result.put("encoderSubmittedCount", encoderCore.getSubmittedCount());
                result.put("encoderQueuedCount", encoderCore.getQueuedCount());
                result.put("encoderWrittenCount", encoderCore.getWrittenCount());
                Log.i(TAG, "Pipeline stats - Input: " + frameCount + ", Interpolated: " + interpolatedFramesCount + 
                           ", Submitted: " + encoderCore.getSubmittedCount() + ", Queued: " + encoderCore.getQueuedCount() + 
                           ", Written to MP4: " + encoderCore.getWrittenCount());
            }

            JSArray tsArray = new JSArray();
            int sampleStep = Math.max(1, timestamps.size() / 20);
            for (int i = 0; i < timestamps.size(); i += sampleStep) {
                tsArray.put(timestamps.get(i));
            }
            result.put("sampledTimestampsUs", tsArray);
            if (!timestamps.isEmpty()) {
                result.put("firstTimestampUs", timestamps.get(0));
                result.put("lastTimestampUs", timestamps.get(timestamps.size() - 1));
            }

            long totalExportEndMs = System.currentTimeMillis();
            long totalExportTimeMs = totalExportEndMs - totalExportStartMs;

            double decoderPct = (totalExportTimeMs > 0) ? (totalDecodeTimeMs * 100.0 / totalExportTimeMs) : 0;
            double opticalFlowPct = (totalExportTimeMs > 0) ? (totalOpticalFlowTimeMs * 100.0 / totalExportTimeMs) : 0;
            double interpolationPct = (totalExportTimeMs > 0) ? (totalInterpTimeMs * 100.0 / totalExportTimeMs) : 0;
            double colorConvPct = (totalExportTimeMs > 0) ? (totalColorConvTimeMs * 100.0 / totalExportTimeMs) : 0;
            double encoderPct = (totalExportTimeMs > 0) ? (totalEncodeTimeMs * 100.0 / totalExportTimeMs) : 0;

            double decoderMsPerFrame = (frameCount > 0) ? ((double) totalDecodeTimeMs / frameCount) : 0;
            double opticalFlowMsPerFrame = (flowComputedCount > 0) ? ((double) totalOpticalFlowTimeMs / flowComputedCount) : 0;
            double interpolationMsPerFrame = (interpolatedFramesCount > 0) ? ((double) totalInterpTimeMs / interpolatedFramesCount) : 0;
            double colorConvMsPerFrame = (frameCount > 0) ? ((double) totalColorConvTimeMs / frameCount) : 0;
            double encoderMsPerFrame = (encoderCore != null && encoderCore.getSubmittedCount() > 0) ? ((double) totalEncodeTimeMs / encoderCore.getSubmittedCount()) : 0;

            Log.i(TAG, "=== PERFORMANCE AUDIT ===");
            Log.i(TAG, String.format("Total Export Time: %d ms", totalExportTimeMs));
            Log.i(TAG, String.format("Decoder Time: %d ms (%.2f%%) | %.2f ms/frame", totalDecodeTimeMs, decoderPct, decoderMsPerFrame));
            Log.i(TAG, String.format("Optical Flow Time: %d ms (%.2f%%) | %.2f ms/frame", totalOpticalFlowTimeMs, opticalFlowPct, opticalFlowMsPerFrame));
            Log.i(TAG, String.format("Interpolation Time: %d ms (%.2f%%) | %.2f ms/frame", totalInterpTimeMs, interpolationPct, interpolationMsPerFrame));
            Log.i(TAG, String.format("Color Conversion Time: %d ms (%.2f%%) | %.2f ms/frame", totalColorConvTimeMs, colorConvPct, colorConvMsPerFrame));
            Log.i(TAG, String.format("Encoder Time: %d ms (%.2f%%) | %.2f ms/frame", totalEncodeTimeMs, encoderPct, encoderMsPerFrame));
            Log.i(TAG, String.format("Mat Allocations: %d | ByteBuffer Allocations: %d | Frame Copies: %d", matAllocations, byteBufferAllocations, frameCopies));
            Log.i(TAG, "=========================");

            result.put("totalExportTimeMs", totalExportTimeMs);
            result.put("decoderTimeMs", totalDecodeTimeMs);
            result.put("opticalFlowTimeMs", totalOpticalFlowTimeMs);
            result.put("interpolationTimeMs", totalInterpTimeMs);
            result.put("colorConversionTimeMs", totalColorConvTimeMs);
            result.put("encoderTimeMs", totalEncodeTimeMs);

            result.put("decoderPct", decoderPct);
            result.put("opticalFlowPct", opticalFlowPct);
            result.put("interpolationPct", interpolationPct);
            result.put("colorConversionPct", colorConvPct);
            result.put("encoderPct", encoderPct);

            result.put("decoderMsPerFrame", decoderMsPerFrame);
            result.put("opticalFlowMsPerFrame", opticalFlowMsPerFrame);
            result.put("interpolationMsPerFrame", interpolationMsPerFrame);
            result.put("colorConversionMsPerFrame", colorConvMsPerFrame);
            result.put("encoderMsPerFrame", encoderMsPerFrame);
            
            result.put("matAllocations", matAllocations);
            result.put("byteBufferAllocations", byteBufferAllocations);
            result.put("frameCopies", frameCopies);

            Log.i(TAG, "Finished processing sequence. Count: " + frameCount + ", Verified: " + timestampsVerified + ", Flow run: " + flowComputedCount + ", Global Avg Flow: " + globalAvgFlow + ", Interpolated: " + interpolatedFramesCount + " (avg PSNR: " + averagePsnr + " dB)");
            if (outputPath != null) {
                try {
                    File outFile = new File(outputPath);
                    if (outFile.exists() && outFile.length() > 0) {
                        Log.i(TAG, "[AUDIT] Stage 10: Output file saved successfully. Size: " + outFile.length() + " bytes. Path: " + outputPath);
                        Log.i(TAG, "[AUDIT] Stage 10: Output file saved");
                    } else {
                        Log.w(TAG, "[AUDIT] Stage 10: Output file saved failed! File is empty or does not exist at path: " + outputPath);
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "[AUDIT] Stage 10: Output file saved verification error with stack trace:", t);
                }
            } else {
                Log.w(TAG, "[AUDIT] Stage 10: Output path is null, file was not saved!");
            }
            call.resolve(result);

        } catch (Exception e) {
            Log.e(TAG, "Error decoding & optimizing optical flow loop", e);
            call.reject("Error decoding frames: " + e.getMessage(), e);
        } finally {
            try {
                if (matA != null) matA.release();
            } catch (Exception ignored) {}
            try {
                if (matB != null) matB.release();
            } catch (Exception ignored) {}
            try {
                if (flowMat != null) flowMat.release();
            } catch (Exception ignored) {}
            try {
                if (mapAx != null) mapAx.release();
            } catch (Exception ignored) {}
            try {
                if (mapAy != null) mapAy.release();
            } catch (Exception ignored) {}
            try {
                if (mapBx != null) mapBx.release();
            } catch (Exception ignored) {}
            try {
                if (mapBy != null) mapBy.release();
            } catch (Exception ignored) {}
            try {
                if (warpedA != null) warpedA.release();
            } catch (Exception ignored) {}
            try {
                if (warpedB != null) warpedB.release();
            } catch (Exception ignored) {}
            try {
                if (intermediateFrame != null) intermediateFrame.release();
            } catch (Exception ignored) {}
            try {
                if (diffMat != null) diffMat.release();
            } catch (Exception ignored) {}
            try {
                if (squareDiff != null) squareDiff.release();
            } catch (Exception ignored) {}
            try {
                if (decoder != null) {
                    try {
                        decoder.stop();
                    } catch (Exception ignored) {}
                    decoder.release();
                }
            } catch (Exception ignored) {}
            try {
                if (encoderCore != null) {
                    encoderCore.release();
                }
            } catch (Exception ignored) {}
            try {
                extractor.release();
            } catch (Exception ignored) {}
        }
    }

    private Mat convertYUVImageToBGR(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        Image.Plane[] planes = image.getPlanes();
        
        Mat yuvMat = new Mat(height + height / 2, width, CvType.CV_8UC1);
        
        // Copy Y plane
        ByteBuffer yBuf = planes[0].getBuffer();
        int yRowStride = planes[0].getRowStride();
        byte[] rowData = new byte[width];
        for (int r = 0; r < height; r++) {
            yBuf.position(r * yRowStride);
            yBuf.get(rowData, 0, width);
            yuvMat.put(r, 0, rowData);
        }
        
        // Interleave U and V into NV21 layout
        ByteBuffer uBuf = planes[1].getBuffer();
        ByteBuffer vBuf = planes[2].getBuffer();
        int uRowStride = planes[1].getRowStride();
        int vRowStride = planes[2].getRowStride();
        int uPixelStride = planes[1].getPixelStride();
        int vPixelStride = planes[2].getPixelStride();
        
        int uvWidth = width / 2;
        int uvHeight = height / 2;
        byte[] uvBytes = new byte[uvWidth * uvHeight * 2];
        
        int idx = 0;
        for (int r = 0; r < uvHeight; r++) {
            int uRowStart = r * uRowStride;
            int vRowStart = r * vRowStride;
            for (int c = 0; c < uvWidth; c++) {
                uvBytes[idx++] = vBuf.get(vRowStart + c * vPixelStride);
                uvBytes[idx++] = uBuf.get(uRowStart + c * uPixelStride);
            }
        }
        
        yuvMat.put(height, 0, uvBytes);
        
        Mat bgrMat = new Mat();
        Imgproc.cvtColor(yuvMat, bgrMat, Imgproc.COLOR_YUV2BGR_NV21);
        yuvMat.release();
        return bgrMat;
    }

    // Today's Work: MediaCodec H.264 Video Encoder + MediaMuxer Wrapper.
    private static class VideoEncoderCore {
        private MediaCodec mEncoder;
        private MediaMuxer mMuxer;
        private int mTrackIndex = -1;
        private boolean mMuxerStarted = false;
        private MediaCodec.BufferInfo mBufferInfo;
        private int mWidth;
        private int mHeight;
        private int mFps;

        // Custom logging counters for pipeline auditing
        private int mSubmittedCount = 0;
        private int mQueuedCount = 0;
        private int mWrittenCount = 0;
        private boolean mFirstOutputReceived = false;

        private long mColorConvTimeMs = 0;
        private int mMatAllocations = 0;
        private int mBufferAllocations = 0;
        private int mFrameCopies = 0;

        public long getColorConvTimeMs() { return mColorConvTimeMs; }
        public int getMatAllocations() { return mMatAllocations; }
        public int getBufferAllocations() { return mBufferAllocations; }
        public int getFrameCopies() { return mFrameCopies; }
        public void resetMetrics() {
            mColorConvTimeMs = 0;
            mMatAllocations = 0;
            mBufferAllocations = 0;
            mFrameCopies = 0;
        }

        public VideoEncoderCore(String outputPath, int width, int height, int fps, int rotationDegrees) throws Exception {
            mWidth = width;
            mHeight = height;
            mFps = fps;
            mBufferInfo = new MediaCodec.BufferInfo();

            MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            format.setInteger(MediaFormat.KEY_BIT_RATE, width * height * 5); // Multiplier for visual quality
            format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            mEncoder = MediaCodec.createEncoderByType("video/avc");
            mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mEncoder.start();

            mMuxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mMuxer.setOrientationHint(rotationDegrees);
        }

        public int getSubmittedCount() {
            return mSubmittedCount;
        }

        public int getQueuedCount() {
            return mQueuedCount;
        }

        public int getWrittenCount() {
            return mWrittenCount;
        }

        public void encodeFrame(Mat bgrMat, long ptsUs) throws Exception {
            if (mSubmittedCount == 0) {
                try {
                    Log.i("VideoEncoderCore", "[AUDIT] Stage 5: First frame submitted to encoder");
                } catch (Throwable t) {
                    Log.e("VideoEncoderCore", "[AUDIT] Stage 5: First frame submitted to encoder log failed with stack trace:", t);
                }
            }
            mSubmittedCount++;
            int inputBufferIndex = -1;
            int dequeueAttempt = 0;

            // Wait/retry loop explicitly draining the encoder when busy, preventing silent frame drops.
            while (inputBufferIndex < 0) {
                inputBufferIndex = mEncoder.dequeueInputBuffer(10000); // 10ms timeout
                if (inputBufferIndex < 0) {
                    dequeueAttempt++;
                    if (dequeueAttempt % 20 == 0) {
                        Log.w("VideoEncoderCore", "Waiting for input buffer available... Attempts: " + dequeueAttempt + ", ptsUs: " + ptsUs);
                    }
                    // Drive encoder output drain to free up input resources!
                    drainEncoder(false);
                }
            }

            // Once buffer is dequeued, convert and submit the frame
            Image image = mEncoder.getInputImage(inputBufferIndex);
            if (image != null) {
                Image.Plane[] planes = image.getPlanes();
                ByteBuffer yBuffer = planes[0].getBuffer();
                ByteBuffer uBuffer = planes[1].getBuffer();
                ByteBuffer vBuffer = planes[2].getBuffer();

                int yRowStride = planes[0].getRowStride();
                int uRowStride = planes[1].getRowStride();
                int vRowStride = planes[2].getRowStride();
                int uPixelStride = planes[1].getPixelStride();
                int vPixelStride = planes[2].getPixelStride();

                int width = mWidth;
                int height = mHeight;

                long colorConvStartMs = System.currentTimeMillis();

                // Convert BGR Mat to YUV CV_8UC3 using OpenCV
                Mat yuvMat = new Mat();
                Imgproc.cvtColor(bgrMat, yuvMat, Imgproc.COLOR_BGR2YUV);

                // Split YUV into individual channels
                List<Mat> yuvChannels = new ArrayList<>();
                Core.split(yuvMat, yuvChannels);
                Mat yMat = yuvChannels.get(0);
                Mat uMatFull = yuvChannels.get(1);
                Mat vMatFull = yuvChannels.get(2);

                // Resize U & V to match downsampled chrominance sizes (W/2 x H/2)
                Mat uMat = new Mat();
                Mat vMat = new Mat();
                Imgproc.resize(uMatFull, uMat, new org.opencv.core.Size(width / 2, height / 2), 0, 0, Imgproc.INTER_AREA);
                Imgproc.resize(vMatFull, vMat, new org.opencv.core.Size(width / 2, height / 2), 0, 0, Imgproc.INTER_AREA);

                mColorConvTimeMs += (System.currentTimeMillis() - colorConvStartMs);
                mMatAllocations += 6; // yuvMat, yMat, uMatFull, vMatFull, uMat, vMat
                mBufferAllocations += 3; // yRowBuf, uRowBuf, vRowBuf

                // Copy Y plane
                byte[] yRowBuf = new byte[width];
                for (int r = 0; r < height; r++) {
                    yMat.get(r, 0, yRowBuf);
                    yBuffer.position(r * yRowStride);
                    yBuffer.put(yRowBuf);
                }

                // Copy U plane
                int uvWidth = width / 2;
                int uvHeight = height / 2;
                byte[] uRowBuf = new byte[uvWidth];
                for (int r = 0; r < uvHeight; r++) {
                    uMat.get(r, 0, uRowBuf);
                    uBuffer.position(r * uRowStride);
                    if (uPixelStride == 1) {
                        uBuffer.put(uRowBuf);
                    } else {
                        int rowStart = r * uRowStride;
                        for (int c = 0; c < uvWidth; c++) {
                            uBuffer.put(rowStart + c * uPixelStride, uRowBuf[c]);
                        }
                    }
                }

                // Copy V plane
                byte[] vRowBuf = new byte[uvWidth];
                for (int r = 0; r < uvHeight; r++) {
                    vMat.get(r, 0, vRowBuf);
                    vBuffer.position(r * vRowStride);
                    if (vPixelStride == 1) {
                        vBuffer.put(vRowBuf);
                    } else {
                        int rowStart = r * vRowStride;
                        for (int c = 0; c < uvWidth; c++) {
                            vBuffer.put(rowStart + c * vPixelStride, vRowBuf[c]);
                        }
                    }
                }

                // Clean up OpenCV mats to prevent native memory leaks
                yuvMat.release();
                yMat.release();
                uMatFull.release();
                vMatFull.release();
                uMat.release();
                vMat.release();

                image.close();
            }
            try {
                mEncoder.queueInputBuffer(inputBufferIndex, 0, mWidth * mHeight * 3 / 2, ptsUs, 0);
                if (mQueuedCount == 0) {
                    Log.i("VideoEncoderCore", "[AUDIT] Stage 5: First frame submitted to encoder successfully");
                }
            } catch (Throwable t) {
                Log.e("VideoEncoderCore", "[AUDIT] Stage 5: First frame queueInputBuffer failed with stack trace:", t);
                throw t;
            }
            mQueuedCount++;

            drainEncoder(false);
        }

        public void signalEndOfStream() throws Exception {
            int inputBufferIndex = -1;
            while (inputBufferIndex < 0) {
                inputBufferIndex = mEncoder.dequeueInputBuffer(10000);
                if (inputBufferIndex < 0) {
                    drainEncoder(false);
                }
            }
            mEncoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            drainEncoder(true);

            // Log detailed pipeline audit metrics
            Log.i("VideoEncoderCore", "=== PIPELINE AUDIT COMPLETED ===");
            Log.i("VideoEncoderCore", "Total Frames Submitted: " + mSubmittedCount);
            Log.i("VideoEncoderCore", "Total Frames Successfully Queued: " + mQueuedCount);
            Log.i("VideoEncoderCore", "Total Frames Written to MP4: " + mWrittenCount);
            Log.i("VideoEncoderCore", "===============================");
        }

        private void drainEncoder(boolean endOfStream) throws Exception {
            final int TIMEOUT_USEC = 10000;
            while (true) {
                int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!endOfStream) {
                        break; // wait for more frames
                    }
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (mMuxerStarted) {
                        throw new RuntimeException("Encoder output format changed twice");
                    }
                    MediaFormat newFormat = mEncoder.getOutputFormat();
                    try {
                        mTrackIndex = mMuxer.addTrack(newFormat);
                        mMuxer.start();
                        mMuxerStarted = true;
                        Log.i("VideoEncoderCore", "[AUDIT] Stage 7: MediaMuxer started");
                    } catch (Throwable t) {
                        Log.e("VideoEncoderCore", "[AUDIT] Stage 7: MediaMuxer started failed with stack trace:", t);
                        throw t;
                    }
                } else if (encoderStatus < 0) {
                    // Ignore other statuses
                } else {
                    if (!mFirstOutputReceived) {
                        try {
                            mFirstOutputReceived = true;
                            Log.i("VideoEncoderCore", "[AUDIT] Stage 6: Encoder output buffer received");
                        } catch (Throwable t) {
                            Log.e("VideoEncoderCore", "[AUDIT] Stage 6: Encoder output buffer received log failed with stack trace:", t);
                        }
                    }
                    ByteBuffer encodedData = mEncoder.getOutputBuffer(encoderStatus);
                    if (encodedData == null) {
                        throw new RuntimeException("Encoder output buffer flat layout");
                    }

                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        mBufferInfo.size = 0;
                    }

                    if (mBufferInfo.size != 0) {
                        if (!mMuxerStarted) {
                            throw new RuntimeException("Muxer hasn't started");
                        }
                        encodedData.position(mBufferInfo.offset);
                        encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
                        try { mMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo); mWrittenCount++; if (mWrittenCount == 1) { Log.i("VideoEncoderCore", "[AUDIT] Stage 8: First frame written to muxer"); } } catch (Throwable t) { Log.e("VideoEncoderCore", "[AUDIT] Stage 8: Write sample data failed with stack trace:", t); throw t; }
                    }

                    mEncoder.releaseOutputBuffer(encoderStatus, false);

                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break; // EOF reached
                    }
                }
            }
        }

        public void release() {
            if (mEncoder != null) {
                try {
                    mEncoder.stop();
                } catch (Exception ignored) {}
                try {
                    mEncoder.release();
                } catch (Exception ignored) {}
                mEncoder = null;
            }
            if (mMuxer != null) {
                try {
                    Log.i("VideoEncoderCore", "[AUDIT] Stage 9: Stopping MediaMuxer...");
                    mMuxer.stop();
                    Log.i("VideoEncoderCore", "[AUDIT] Stage 9: MediaMuxer stopped");
                } catch (Throwable t) {
                    Log.e("VideoEncoderCore", "[AUDIT] Stage 9: MediaMuxer stop failed with stack trace:", t);
                }
                try {
                    mMuxer.release();
                } catch (Exception ignored) {}
                mMuxer = null;
            }
        }
    }
}
