package com.litecut.app.export;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.view.Surface;
import java.io.IOException;
import java.nio.ByteBuffer;

public class VideoDecoder {
    private MediaExtractor mExtractor;
    private MediaCodec mDecoder;
    private boolean mIsEOF = false;
    private boolean mInputEOS = false;
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();

    public void setup(String inputPath, Surface surface) throws IOException {
        mExtractor = new MediaExtractor();
        mExtractor.setDataSource(inputPath);
        
        int trackIndex = -1;
        for (int i = 0; i < mExtractor.getTrackCount(); i++) {
            MediaFormat format = mExtractor.getTrackFormat(i);
            if (format.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                trackIndex = i;
                break;
            }
        }
        
        if (trackIndex == -1) throw new IOException("No video track");
        
        mExtractor.selectTrack(trackIndex);
        MediaFormat format = mExtractor.getTrackFormat(trackIndex);
        mDecoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
        mDecoder.configure(format, surface, null, 0);
        mDecoder.start();
    }

    public MediaExtractor getExtractor() {
        return mExtractor;
    }

    public int decodeFrame() {
        if (mIsEOF) return -1;

        if (!mInputEOS) {
            int inputBufIndex = mDecoder.dequeueInputBuffer(10000);
            if (inputBufIndex >= 0) {
                ByteBuffer inputBuf = mDecoder.getInputBuffer(inputBufIndex);
                int sampleSize = mExtractor.readSampleData(inputBuf, 0);
                if (sampleSize < 0) {
                    mDecoder.queueInputBuffer(inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    mInputEOS = true;
                } else {
                    mDecoder.queueInputBuffer(inputBufIndex, 0, sampleSize, mExtractor.getSampleTime(), 0);
                    mExtractor.advance();
                }
            }
        }

        int outputBufIndex = mDecoder.dequeueOutputBuffer(mBufferInfo, 10000);
        if (outputBufIndex >= 0) {
            boolean render = mBufferInfo.size != 0;
            mDecoder.releaseOutputBuffer(outputBufIndex, render);
            if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                mIsEOF = true;
            }
            return render ? 1 : 0;
        } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            // Format changed, ignore for now
            return 0;
        } else if (outputBufIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            return 0; 
        }
        return 0;
    }

    public void release() {
        if (mDecoder != null) {
            try {
                mDecoder.stop();
            } catch (Exception e) {
                // ignore
            }
            try {
                mDecoder.release();
            } catch (Exception e) {
                // ignore
            }
            mDecoder = null;
        }
        if (mExtractor != null) {
            try {
                mExtractor.release();
            } catch (Exception e) {
                // ignore
            }
            mExtractor = null;
        }
    }
}
