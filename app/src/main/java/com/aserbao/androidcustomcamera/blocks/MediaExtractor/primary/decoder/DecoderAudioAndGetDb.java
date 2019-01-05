package com.aserbao.androidcustomcamera.blocks.MediaExtractor.primary.decoder;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 功能: 解码获取音频帧分贝大小
 * @author aserbao
 * @date : On 2019/1/4 4:05 PM
 * @email: 1142803753@qq.com
 * @project:AndroidCamera
 * @package:com.aserbao.androidcustomcamera.blocks.MediaExtractor.primary
 * @Copyright: 个人版权所有
 */
public class DecoderAudioAndGetDb {
    private static final String TAG = "DecoderAudio";
    public DecoderAudioAndGetDb() {
    }

    private DecoderAACThread mDecoderAACThread;
    private byte[] mPcmData;
    
    public void start(String inputAudioPath,String mimeType,DbCallBackListener dbCallBackListener){
        mDbCallBackListener  = dbCallBackListener;
        if (mDecoderAACThread == null) {
            mDecoderAACThread = new DecoderAACThread(inputAudioPath,mimeType);
            mDecoderAACThread.setRunning(true);
            try {
                mDecoderAACThread.start();
            } catch (Exception e) {
                Log.w(TAG, "decode already start");
            }
        }

    }

    public void stop() {
        if (mDecoderAACThread != null) {
            mDecoderAACThread.setRunning(false);
            mDecoderAACThread = null;
        }
    }

    public class DecoderAACThread extends Thread{
        String MIME_TYPE = "audio/mp4a-latm";
        int KEY_CHANNEL_COUNT = 2;
        int KEY_SAMPLE_RATE = 44100;
        int KEY_BIT_RATE = 64000;
        int KEY_AAC_PROFILE = MediaCodecInfo.CodecProfileLevel.AACObjectLC;
        int WAIT_TIME = 10000;
        int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        int CHANNEL_MODE = AudioFormat.CHANNEL_IN_STEREO;
        int BUFFFER_SIZE = 2048;

        private String mInputAudioPath;//音频路径
        private String mInputAudioMimeType;
        private MediaExtractor mMediaExtractor;
        private MediaCodec mMediaCodec;
        private AudioTrack mPcmPlayer;
        private MediaCodec.BufferInfo mBufferInfo;
        private boolean running;

        private void setRunning(boolean running) {
            this.running = running;
        }

        public DecoderAACThread(String inputAudioPath,String mimeType) {
            mInputAudioPath = inputAudioPath;
            mInputAudioMimeType = mimeType;
        }

        @Override
        public void run() {
            super.run();
            if (!prepare()) {
                running = false;
                Log.e(TAG, "音频解码器初始化失败");
                return;
            }
            decode();
            release();
        }


        public boolean prepare(){
            mBufferInfo = new MediaCodec.BufferInfo();
            mMediaExtractor = new MediaExtractor();
            mPcmPlayer = new AudioTrack(AudioManager.STREAM_MUSIC, KEY_SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AUDIO_FORMAT, BUFFFER_SIZE, AudioTrack.MODE_STREAM);
            mPcmPlayer.play();
            try {
                mMediaExtractor.setDataSource(mInputAudioPath);
                int audioIndex = -1;//音频通道
                int trackCount = mMediaExtractor.getTrackCount();//获取通道总数
                for (int i = 0; i < trackCount; i++) {
                    MediaFormat trackFormat = mMediaExtractor.getTrackFormat(i);
                    String string = trackFormat.getString(MediaFormat.KEY_MIME);
                    if (string.startsWith("audio/")) {
                        audioIndex = i;
                    }//获取音频通道
                }
                mMediaExtractor.selectTrack(audioIndex);//切换到音频通道
                MediaFormat mediaFormat = mMediaExtractor.getTrackFormat(audioIndex);
                mMediaCodec = MediaCodec.createDecoderByType(mInputAudioMimeType);
/*                mediaFormat.setString(MediaFormat.KEY_MIME, MIME_TYPE);
                mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, KEY_CHANNEL_COUNT);
                mediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, KEY_SAMPLE_RATE);
                mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, KEY_BIT_RATE);
                mediaFormat.setInteger(MediaFormat.KEY_IS_ADTS, 1);
                mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, KEY_AAC_PROFILE);
                ByteBuffer key（暂时不了解该参数的含义，但必须设置）
                byte[] data = new byte[]{(byte) 0x11, (byte) 0x90};
                ByteBuffer csd_0 = ByteBuffer.wrap(data);
                mediaFormat.setByteBuffer("csd-0", csd_0);*/
                mMediaCodec.configure(mediaFormat, null, null, 0);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            if (mMediaCodec == null) {
                Log.e(TAG, "create mediaDecode failed");
                return false;
            }
            mMediaCodec.start();
            return true;
        }


        private void decode() {
            while (running) {
                int inputIndex = mMediaCodec.dequeueInputBuffer(-1);
                if (inputIndex >= 0) {
                    ByteBuffer inputBuffer = mMediaCodec.getInputBuffer(inputIndex);
                    if (inputBuffer == null) {
                        return;
                    }
                    inputBuffer.clear();
                    int sampleSize = mMediaExtractor.readSampleData(inputBuffer, 0);
                    if (sampleSize < 0) {
                        mMediaCodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        running = false;
                    } else {
                        mMediaCodec.queueInputBuffer(inputIndex, 0, sampleSize, mMediaExtractor.getSampleTime(), 0);
                        mMediaExtractor.advance();
                    }
                }
                int outputIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, WAIT_TIME);
                ByteBuffer outputBuffer;
                if (outputIndex >= 0) {
                    // Simply ignore codec config buffers.
                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        Log.i(TAG, "audio encoder: codec config buffer");
                        mMediaCodec.releaseOutputBuffer(outputIndex, false);
                        continue;
                    }
                    if (mBufferInfo.size != 0) {
                        outputBuffer = mMediaCodec.getOutputBuffer(outputIndex);
                        if (mPcmData == null || mPcmData.length < mBufferInfo.size) {
                            mPcmData = new byte[mBufferInfo.size];
                        }
                        if (outputBuffer != null) {
                            outputBuffer.get(mPcmData, 0, mBufferInfo.size);
                            outputBuffer.clear();
                        }
                        float v = mMediaExtractor.getSampleTime() / (float) (1000 * 1000);

                        calc(mPcmData,KEY_SAMPLE_RATE);
//                        Log.e(TAG, "解析到的时间点为："+ v + "s     decode:  mPcmData.length  = " + mPcmData.length + " mBufferInfo "  + mBufferInfo.toString());
                        mPcmPlayer.write(mPcmData, 0, mBufferInfo.size);
                    }
                    mMediaCodec.releaseOutputBuffer(outputIndex, false);
                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.i(TAG, "saw output EOS.");
                    }
                }
            }
            mMediaExtractor.release();
        }

        /**
         * 释放资源
         */
        private void release() {
            if (mMediaCodec != null) {
                mMediaCodec.stop();
                mMediaCodec.release();
            }
            if (mPcmPlayer != null) {
                mPcmPlayer.stop();
                mPcmPlayer.release();
                mPcmPlayer = null;
            }
        }
    }

    public void calc(byte[] fft,int samplingRate){
        float[] magnitudes = new float[fft.length / 2];
        int max = 0;
        for (int i = 0; i < magnitudes.length; i++) {
            magnitudes[i] = (float) Math.hypot(fft[2 * i], fft[2 * i + 1]);
            if (magnitudes[max] < magnitudes[i]) {
                max = i;
            }
        }

        int  currentFrequency = max * samplingRate / fft.length;
        if (currentFrequency<0){
            return;
        }



        long v = 0;
        for (int i = 0; i < fft.length; i++) {
            v += Math.pow(fft[i], 2);
        }

        double volume = 10 * Math.log10(v / (double) fft.length);
        mDbCallBackListener.cuurentFrequenty(currentFrequency,volume);
        Log.e(TAG, "calc: currentFrequency = " + currentFrequency + "   volume =  " + volume );
    }
    private DbCallBackListener mDbCallBackListener;
    public interface  DbCallBackListener{
        void cuurentFrequenty(int cuurentFrequenty,double volume);
    }
}