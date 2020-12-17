package com.MythiCode.camerakit;

import android.widget.FrameLayout;

import io.flutter.plugin.common.MethodChannel;

public interface CameraViewInterface {

    void initCamera(FrameLayout frameLayout, boolean hasBarcodeReader, char flashMode, boolean isFillScale, int barcodeMode, int cameraSelector);
    void setCameraVisible(boolean isCameraVisible);
    void changeFlashMode(char captureFlashMode);
    void takePicture(final MethodChannel.Result result);
    void pauseCamera();
    void resumeCamera();
    void dispose();

}
