package com.example.hy.liveexampleandroid.LiveSend;

import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Size;
import android.view.TextureView;

/**
 * Created by Hamik Young on 2017/12/29.
 */

public interface SendInteractor {
    void initialPusher(TextureView textureView, CameraManager cameraManager);
    void stopPush();
    void startPush(String pushAddress);
    void setPreviewSize(Size previewSize);
    void setPushSize(Size pushSize);
    void setPushType(String pushType);
    void switchCamera();
    void onDestroy();
}
