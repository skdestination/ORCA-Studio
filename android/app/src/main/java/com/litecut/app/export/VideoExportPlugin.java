package com.litecut.app.export;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.view.Surface;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.nio.ByteBuffer;

@CapacitorPlugin(name = "VideoExport")
public class VideoExportPlugin extends Plugin {

    @PluginMethod
    public void exportVideo(PluginCall call) {
        String inputPath = call.getString("inputPath");
        String outputPath = call.getString("outputPath");
        int width = call.getInt("width", 1920);
        int height = call.getInt("height", 1080);
        int fps = call.getInt("fps", 30);

        if (inputPath == null || outputPath == null) {
            call.reject("Input or output path missing");
            return;
        }

        new Thread(() -> {
            EglCore eglCore = null;
            VideoEncoder encoder = null;
            VideoDecoder decoder = null;
            try {
                eglCore = new EglCore();
                encoder = new VideoEncoder();
                encoder.setup(outputPath, width, height, fps);
                
                EGLSurface encoderSurface = eglCore.createWindowSurface(encoder.getInputSurface());
                
                int[] textures = new int[1];
                GLES20.glGenTextures(1, textures, 0);
                int textureId = textures[0];
                
                SurfaceTexture st = new SurfaceTexture(textureId);
                Surface decoderSurface = new Surface(st);
                TextureRender renderer = new TextureRender();
                
                decoder = new VideoDecoder();
                decoder.setup(inputPath, decoderSurface);
                
                float[] stMatrix = new float[16];
                
                // Identify Audio Track
                int audioTrackIndex = -1;
                for (int i = 0; i < decoder.getExtractor().getTrackCount(); i++) {
                    MediaFormat format = decoder.getExtractor().getTrackFormat(i);
                    if (format.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                        audioTrackIndex = i;
                        encoder.addAudioTrack(format);
                        decoder.getExtractor().selectTrack(i);
                        break;
                    }
                }
                
                final Object frameSyncObject = new Object();
                final boolean[] frameAvailable = new boolean[1];
                st.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                    @Override
                    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                        synchronized (frameSyncObject) {
                            frameAvailable[0] = true;
                            frameSyncObject.notifyAll();
                        }
                    }
                });

                boolean running = true;
                long framesProcessed = 0;
                MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();
                ByteBuffer audioBuffer = ByteBuffer.allocate(1024 * 1024);
                
                while (running) {
                    int decodeStatus = decoder.decodeFrame();
                    if (decodeStatus == -1) {
                        running = false;
                        break;
                    }
                    
                    // Process Audio
                    if (audioTrackIndex != -1) {
                        int sampleSize = decoder.getExtractor().readSampleData(audioBuffer, 0);
                        if (sampleSize > 0) {
                            audioBufferInfo.set(0, sampleSize, decoder.getExtractor().getSampleTime(), decoder.getExtractor().getSampleFlags());
                            encoder.writeAudioSample(audioBuffer, audioBufferInfo);
                            decoder.getExtractor().advance();
                        }
                    }
                    
                    if (decodeStatus == 1) {
                        synchronized (frameSyncObject) {
                            while (!frameAvailable[0]) {
                                try {
                                    frameSyncObject.wait(2500);
                                    if (!frameAvailable[0]) {
                                        throw new RuntimeException("SurfaceTexture frame wait timed out");
                                    }
                                } catch (InterruptedException ie) {
                                    throw new RuntimeException(ie);
                                }
                            }
                            frameAvailable[0] = false;
                        }

                        st.updateTexImage();
                        st.getTransformMatrix(stMatrix);
                        
                        eglCore.makeCurrent(encoderSurface);
                        renderer.drawFrame(textureId, stMatrix);
                        
                        // Set the presentation timestamp to preserve the decoder timeline
                        long timestampNs = st.getTimestamp();
                        eglCore.setPresentationTime(encoderSurface, timestampNs);
                        
                        eglCore.swapBuffers(encoderSurface);
                        
                        encoder.drainEncoder(false);
                        
                        framesProcessed++;
                        if (framesProcessed % 30 == 0) {
                            JSObject progressData = new JSObject();
                            progressData.put("progress", (double)framesProcessed / 300.0);
                            notifyListeners("exportProgress", progressData);
                        }
                    }
                }
                
                encoder.drainEncoder(true);
                call.resolve(new JSObject().put("status", "completed"));
            } catch (Exception e) {
                call.reject("Export failed: " + e.getMessage());
            } finally {
                if (encoder != null) {
                    try {
                        encoder.release();
                    } catch (Exception e) {
                        // ignore
                    }
                }
                if (decoder != null) {
                    try {
                        decoder.release();
                    } catch (Exception e) {
                        // ignore
                    }
                }
                if (eglCore != null) {
                    try {
                        eglCore.release();
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
        }).start();
    }
}
