package com.litecut.app.export;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaExtractor;
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

        // Sanitize paths by stripping the file:// prefix for native MediaCodec use
        final String finalInputPath = inputPath.startsWith("file://") ? inputPath.substring(7) : inputPath;
        final String finalOutputPath = outputPath.startsWith("file://") ? outputPath.substring(7) : outputPath;

        new Thread(() -> {
            EglCore eglCore = null;
            VideoEncoder encoder = null;
            VideoDecoder decoder = null;
            MediaExtractor audioExtractor = null;
            TextureRender renderer = null;
            try {
                eglCore = new EglCore();
                encoder = new VideoEncoder();
                encoder.setup(finalOutputPath, width, height, fps);
                
                EGLSurface encoderSurface = eglCore.createWindowSurface(encoder.getInputSurface());
                eglCore.makeCurrent(encoderSurface);
                
                renderer = new TextureRender();
                renderer.surfaceCreated();
                
                int[] textures = new int[1];
                GLES20.glGenTextures(1, textures, 0);
                int error = GLES20.glGetError();
                if (error != GLES20.GL_NO_ERROR) throw new RuntimeException("glGenTextures error: " + error);
                
                int textureId = textures[0];
                if (textureId == 0) throw new RuntimeException("failed to generate texture");
                
                SurfaceTexture st = new SurfaceTexture(textureId);
                Surface decoderSurface = new Surface(st);
                
                decoder = new VideoDecoder();
                decoder.setup(finalInputPath, decoderSurface);
                
                float[] stMatrix = new float[16];
                
                // Get video duration to compute accurate progress updates
                long videoDurationUs = 0;
                int trackCount = decoder.getExtractor().getTrackCount();
                for (int i = 0; i < trackCount; i++) {
                    MediaFormat format = decoder.getExtractor().getTrackFormat(i);
                    if (format.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                        if (format.containsKey(MediaFormat.KEY_DURATION)) {
                            videoDurationUs = format.getLong(MediaFormat.KEY_DURATION);
                        }
                        break;
                    }
                }

                // Create a completely separate extractor for audio to avoid track conflicts in the main decoder
                int audioTrackIndex = -1;
                MediaExtractor tempExtractor = new MediaExtractor();
                tempExtractor.setDataSource(finalInputPath);
                for (int i = 0; i < tempExtractor.getTrackCount(); i++) {
                    MediaFormat format = tempExtractor.getTrackFormat(i);
                    if (format.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                        audioExtractor = tempExtractor;
                        audioTrackIndex = i;
                        audioExtractor.selectTrack(i);
                        encoder.addAudioTrack(format);
                        break;
                    }
                }
                if (audioExtractor == null) {
                    tempExtractor.release();
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
                    
                    // Process Audio sequentially without disturbing the video decoder
                    if (audioExtractor != null && audioTrackIndex != -1) {
                        while (true) {
                            int sampleSize = audioExtractor.readSampleData(audioBuffer, 0);
                            if (sampleSize < 0) {
                                break;
                            }
                            long sampleTime = audioExtractor.getSampleTime();
                            
                            if (encoder.isMuxerStarted()) {
                                audioBufferInfo.set(0, sampleSize, sampleTime, audioExtractor.getSampleFlags());
                                encoder.writeAudioSample(audioBuffer, audioBufferInfo);
                                audioExtractor.advance();
                            } else {
                                // Break and wait for the video encoder to output format and start the muxer
                                break;
                            }
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
                        if (framesProcessed % 15 == 0) {
                            long sampleTimeUs = decoder.getExtractor().getSampleTime();
                            if (videoDurationUs > 0 && sampleTimeUs > 0) {
                                double progress = (double) sampleTimeUs / (double) videoDurationUs;
                                if (progress > 1.0) progress = 1.0;
                                JSObject progressData = new JSObject();
                                progressData.put("progress", progress);
                                notifyListeners("exportProgress", progressData);
                            }
                        }
                    }
                }

                // Copy any remaining audio samples that are left at the end of the file
                if (audioExtractor != null && audioTrackIndex != -1) {
                    while (true) {
                        int sampleSize = audioExtractor.readSampleData(audioBuffer, 0);
                        if (sampleSize < 0) {
                            break;
                        }
                        long sampleTime = audioExtractor.getSampleTime();
                        audioBufferInfo.set(0, sampleSize, sampleTime, audioExtractor.getSampleFlags());
                        encoder.writeAudioSample(audioBuffer, audioBufferInfo);
                        audioExtractor.advance();
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
                if (audioExtractor != null) {
                    try {
                        audioExtractor.release();
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
