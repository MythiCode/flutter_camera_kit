package com.MythiCode.camerakit;

import io.flutter.plugin.common.MethodChannel;

public interface FlutterMethodListener {

    void onBarcodeRead(String barcode);

    void onTakePicture(MethodChannel.Result result, String filePath);

    void onTakePictureFailed(MethodChannel.Result result, String errorCode, String errorMessage);

    void onVideoRecord(MethodChannel.Result result, String filePath);

    void onVideoRecordFailed(MethodChannel.Result result, String message);
}
