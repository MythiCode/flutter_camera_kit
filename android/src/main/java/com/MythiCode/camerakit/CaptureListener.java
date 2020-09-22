package com.MythiCode.camerakit;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.File;

import io.flutter.plugin.common.MethodChannel;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class CaptureListener extends CameraCaptureSession.CaptureCallback  {

    private CameraView2 cameraView;
    private final FlutterMethodListener flutterMethodListener;
    private final MethodChannel.Result methodChannelResult;
    private final File file;

    public CaptureListener(CameraView2 cameraView, FlutterMethodListener flutterMethodListener, MethodChannel.Result result, File file) {
        this.cameraView = cameraView;
        this.flutterMethodListener = flutterMethodListener;
        this.methodChannelResult = result;
        this.file = file;
    }

    @Override
    public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
        super.onCaptureCompleted(session, request, result);
        cameraView.createCameraPreview();
    }

    @Override
    public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
        super.onCaptureFailed(session, request, failure);
        flutterMethodListener.onTakePictureFailed(methodChannelResult, "-11", "Error in capture. Reason: " +  failure.getReason());
    }
}
