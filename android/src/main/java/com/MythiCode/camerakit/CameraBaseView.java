package com.MythiCode.camerakit;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.platform.PlatformView;

public class CameraBaseView implements PlatformView {


    private final Activity activity;
    private final FlutterMethodListener flutterMethodListener;
    private final FrameLayout linearLayout;
    private CameraViewInterface cameraViewInterface;

    public CameraBaseView(Activity activity, FlutterMethodListener flutterMethodListener) {
        this.activity = activity;
        this.flutterMethodListener = flutterMethodListener;
        linearLayout = new FrameLayout(activity);
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        linearLayout.setBackgroundColor(Color.parseColor("#000000"));

    }

    public void initCamera(boolean hasBarcodeReader, char flashMode, boolean isFillScale, int barcodeMode, int androidCameraMode, int cameraSelector) {
        if (hasBarcodeReader && androidCameraMode == 1) {
            throw new RuntimeException("You cannot use barcode reader for reading barcode in Camera API1");
        }
        switch (androidCameraMode) {
            case 3:
                cameraViewInterface = new CameraViewX(activity, flutterMethodListener);
                break;
            case 2:
                cameraViewInterface = new CameraView2(activity, flutterMethodListener);
                break;
            case 1:
                cameraViewInterface = new CameraView1(activity, flutterMethodListener);
                break;
        }


        cameraViewInterface.initCamera(linearLayout, hasBarcodeReader, flashMode, isFillScale, barcodeMode, cameraSelector);
    }

    public void setCameraVisible(boolean isCameraVisible) {
        if (cameraViewInterface != null)
            cameraViewInterface.setCameraVisible(isCameraVisible);
    }

    public void changeFlashMode(char captureFlashMode) {
        cameraViewInterface.changeFlashMode(captureFlashMode);
    }

    public void takePicture(String path, final MethodChannel.Result result) {
        cameraViewInterface.takePicture(path, result);
    }

    public void pauseCamera() {
        cameraViewInterface.pauseCamera();
    }

    public void resumeCamera() {
        cameraViewInterface.resumeCamera();
    }

    @Override
    public View getView() {
        return linearLayout;
    }

    @Override
    public void dispose() {
        if (cameraViewInterface != null)
            cameraViewInterface.dispose();
    }
}
