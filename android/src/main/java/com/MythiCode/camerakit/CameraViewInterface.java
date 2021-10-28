package com.MythiCode.camerakit;

import android.widget.FrameLayout;

import io.flutter.plugin.common.MethodChannel;

public interface CameraViewInterface {

    void initCamera(FrameLayout frameLayout, boolean hasBarcodeReader, char flashMode, boolean isFillScale, int barcodeMode, int cameraSelector);
    void setCameraVisible(boolean isCameraVisible);
    void changeFlashMode(char captureFlashMode);
    void takePicture(String path, final MethodChannel.Result result);
    void pauseCamera();
    void resumeCamera();
    void startRecording(String path, final MethodChannel.Result result);
    void stopRecording();
    void dispose();
    void setVideoMode();

}
