package com.MythiCode.camerakit;

import android.widget.LinearLayout;

import io.flutter.plugin.common.MethodChannel;

public interface CameraViewInterface {

    void initCamera(LinearLayout linearLayout, boolean hasBarcodeReader, char flashMode, boolean isFillScale, int barcodeMode);
    void setCameraVisible(boolean isCameraVisible);
    void changeFlashMode(char captureFlashMode);
    void takePicture(final MethodChannel.Result result);
    void pauseCamera();
    void resumeCamera();
    void dispose();

}
