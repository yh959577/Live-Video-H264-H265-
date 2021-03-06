package com.example.livelib.Push.Encoder;

import android.hardware.camera2.CameraCaptureSession;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;


import com.example.livelib.Push.PusherImp;
import com.example.livelib.Push.Queue.QueueManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Created by UPC on 2018/1/7.
 */

public class EncoderImp implements Encoder {
    private static final String TAG = "EncoderImp";
    private Thread mEncodeThread;
    private String mEncodeType = null;
    private Size mEncodeSize = null;
    private MediaCodec mMediaCodec = null;
    private MediaFormat mMediaFormat = null;
    private volatile boolean isRunning;
   // private FileOutputStream fileOutputStream;
    private byte[] configureByte;  //the configure info before the key frame
    private int TIMEOUT_USEC = 12000;
    private int frame_rate = 15;
    private byte[] yuv420;
    private Runnable mEncoderRunnable;
    private Handler mHandler;
    private CameraCaptureSession.StateCallback mCameraCaptureSessionStateCallback;

    public EncoderImp() {
//        try {
//            fileOutputStream = new FileOutputStream(new File(Environment.getExternalStorageDirectory().getPath()
//                    + "/testH264.h264"));
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        }

        mEncoderRunnable = () -> {
            isRunning = true;
            byte[] input = null;
            long pts = 0;
            long generateIndex = 0;
            yuv420 = new byte[mEncodeSize.getHeight() * mEncodeSize.getWidth() * 3 / 2];
            while (isRunning) {
//                if (QueueManager.getYUVQueueSize() > 0) {
//                    input = QueueManager.takeDataFromYUVQueue();
//                    swapYV12toNV12(input, yuv420, mEncodeSize.getWidth(), mEncodeSize.getHeight());
//
//                }
                try {
                    input = QueueManager.takeDataFromYUVQueue();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
                swapYV12toNV12(input, yuv420, mEncodeSize.getWidth(), mEncodeSize.getHeight());
                if (yuv420 != null) {
                    long startMs = System.currentTimeMillis();
                    int inputBufferIndex = mMediaCodec.dequeueInputBuffer(-1);
                    if (inputBufferIndex >= 0) {
                        pts = computePresentationTime(generateIndex);
                        ByteBuffer inputBuffer = mMediaCodec.getInputBuffer(inputBufferIndex);
                        inputBuffer.clear();
                        inputBuffer.put(yuv420);
                        mMediaCodec.queueInputBuffer(inputBufferIndex, 0, yuv420.length, pts, 0);
                        generateIndex += 1;
                    }
                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    int outPutBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
                    while (outPutBufferIndex >= 0) {
                        ByteBuffer outputBuffer = mMediaCodec.getOutputBuffer(outPutBufferIndex);
                        byte[] outData = new byte[bufferInfo.size];
                        outputBuffer.get(outData);
                        if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                            configureByte = new byte[bufferInfo.size];
                            configureByte = outData;
                            Log.i(TAG, "EncoderImp: configByteContent==="+Arrays.toString(configureByte));
                        } else if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                            byte[] keyFrame = new byte[bufferInfo.size + configureByte.length];
                            System.arraycopy(configureByte, 0, keyFrame, 0, configureByte.length);
                            System.arraycopy(outData, 0, keyFrame, configureByte.length, outData.length);
                            Log.i(TAG, "EncoderImp: keyFrameLen====" + keyFrame.length);
                            Log.i(TAG, "EncoderImp: size====" + QueueManager.getFrameQueueSize());
                            Log.i(TAG, "EncoderImp: keyFrameContent==="+ Arrays.toString(keyFrame));
                            QueueManager.addDataToFrameQueue(keyFrame);
                        } else {

                            Log.i(TAG, "EncoderImp: outDataLen====" + outData.length);
                            Log.i(TAG, "EncoderImp: size====" + QueueManager.getFrameQueueSize());
                            Log.i(TAG, "EncoderImp: frameContent==="+Arrays.toString(outData));
                            QueueManager.addDataToFrameQueue(outData);

                        }
                        mMediaCodec.releaseOutputBuffer(outPutBufferIndex, false);
                        outPutBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
                    }
                } else {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            mMediaCodec.stop();
            mMediaCodec.release();
        };

        //  mEncodeThread = new Thread(mEncoderRunnable);
        initialHandler();
    }

    private void initialHandler() {
        HandlerThread threadHandler = new HandlerThread("CAMERA2");
        threadHandler.start();
        mHandler = new Handler(threadHandler.getLooper());
    }

    @Override
    public void initial() {
        initialMediaCodec();
    }

    @Override
    public void startEncoder() {
        mMediaCodec.start();
        mEncodeThread = new Thread(mEncoderRunnable);
        mEncodeThread.start();

    }

    @Override
    public void stopEncoder() {
        isRunning = false;
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setPushSize(Size pushSize) {
        mEncodeSize = pushSize;
    }

    @Override
    public void setPushType(String pushType) {
        mEncodeType = pushType;
    }

    @Override
    public Size getPushSize() {
        return mEncodeSize;
    }

    @Override
    public String getPushType() {
        return mEncodeType;
    }

    @Override
    public void onDestroy() {

    }

    private long computePresentationTime(long frameIndex) {
        return  frameIndex * 1000000 / frame_rate;
    }

    private void swapYV12toI420(byte[] yv12bytes, byte[] i420bytes, int width, int height) {
        System.arraycopy(yv12bytes, 0, i420bytes, 0, width * height);
        System.arraycopy(yv12bytes, width * height + width * height / 4, i420bytes, width * height, width * height / 4);
        System.arraycopy(yv12bytes, width * height, i420bytes, width * height + width * height / 4, width * height / 4);
    }

    //this is work on my phone,it's probably cause the color problem on different device
    //switch YV12
    private void swapYV12toNV12(byte[] yv12bytes, byte[] nv12bytes, int width, int height) {
        int nLenY = width * height;
        int nLenU = nLenY / 4;

        System.arraycopy(yv12bytes, 0, nv12bytes, 0, width * height);
        for (int i = 0; i < nLenU; i++) {
            nv12bytes[nLenY + 2 * i + 1] = yv12bytes[nLenY + i + nLenU];//u    odd number is u
            nv12bytes[nLenY + 2 * i] = yv12bytes[nLenY + i];//y             even number is y
        }
    }


    private void initialMediaCodec() {
        if (mEncodeType == null)
            mEncodeType = MediaFormat.MIMETYPE_VIDEO_AVC;
        if (mEncodeSize == null)
            mEncodeSize = PusherImp.supportSize[0];
        mMediaFormat = MediaFormat.createVideoFormat(mEncodeType, mEncodeSize.getWidth(), mEncodeSize.getHeight());
        mMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        mMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 600*1024);
        mMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frame_rate);
        mMediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        try {
            mMediaCodec = MediaCodec.createEncoderByType(mEncodeType);
        } catch (IOException e) {
            //ToastUtil.toast();
            e.printStackTrace();
            return;
        }
        mMediaCodec.configure(mMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        //   mMediaCodec.start();
    }



}
