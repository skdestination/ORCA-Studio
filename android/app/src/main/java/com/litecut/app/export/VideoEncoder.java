package com.litecut.app.export;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.view.Surface;
import java.io.IOException;
import java.nio.ByteBuffer;

public class VideoEncoder {
    private MediaCodec mEncoder;
    private MediaMuxer mMuxer;
    private Surface mInputSurface;
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private int mVideoTrackIndex = -1;
    private int mAudioTrackIndex = -1;
    private boolean mMuxerStarted = false;

    public void setup(String outputPath, int width, int height, int fps) throws IOException {
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 4000000); // 4Mbps
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        mEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mInputSurface = mEncoder.createInputSurface();
        mEncoder.start();
        
        mMuxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    public void addAudioTrack(MediaFormat format) {
        if (!mMuxerStarted) {
            mAudioTrackIndex = mMuxer.addTrack(format);
        }
    }

    public void writeAudioSample(ByteBuffer buffer, MediaCodec.BufferInfo info) {
        if (mMuxerStarted && mAudioTrackIndex != -1) {
            mMuxer.writeSampleData(mAudioTrackIndex, buffer, info);
        }
    }

    public void drainEncoder(boolean endOfStream) {
        if (endOfStream) {
            mEncoder.signalEndOfInputStream();
        }
        
        while (true) {
            int timeoutUs = endOfStream ? 10000 : 0;
            int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, timeoutUs);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) {
                    break;
                }
                continue;
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!mMuxerStarted) {
                    mVideoTrackIndex = mMuxer.addTrack(mEncoder.getOutputFormat());
                    mMuxer.start();
                    mMuxerStarted = true;
                }
            } else if (encoderStatus >= 0) {
                ByteBuffer encodedData = mEncoder.getOutputBuffer(encoderStatus);
                if (mMuxerStarted) {
                    mMuxer.writeSampleData(mVideoTrackIndex, encodedData, mBufferInfo);
                }
                mEncoder.releaseOutputBuffer(encoderStatus, false);
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }
            }
        }
    }

    public Surface getInputSurface() {
        return mInputSurface;
    }

    public void release() {
        if (mEncoder != null) {
            try {
                mEncoder.stop();
            } catch (Exception e) {
                // ignore
            }
            try {
                mEncoder.release();
            } catch (Exception e) {
                // ignore
            }
            mEncoder = null;
        }
        if (mMuxer != null) {
            if (mMuxerStarted) {
                try {
                    mMuxer.stop();
                } catch (Exception e) {
                    // ignore
                }
            }
            try {
                mMuxer.release();
            } catch (Exception e) {
                // ignore
            }
            mMuxer = null;
        }
    }
}
