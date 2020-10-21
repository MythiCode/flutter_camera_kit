package com.MythiCode.camerakit;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import io.flutter.embedding.engine.dart.DartExecutor;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.platform.PlatformView;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class CameraKitFlutterView implements PlatformView, MethodChannel.MethodCallHandler, FlutterMethodListener {


    private static final int REQUEST_CAMERA_PERMISSION = 10001;
    private final MethodChannel channel;
    private final ActivityPluginBinding activityPluginBinding;
    private CameraView2 cameraView;

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull final MethodChannel.Result result) {
        if (call.method.equals("requestPermission")) {
            boolean hasVideoRecord = call.argument("hasVideoRecord");
            if (ActivityCompat.checkSelfPermission(activityPluginBinding.getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermission(result, hasVideoRecord);
                return;
            } else {
                if (hasVideoRecord && ActivityCompat.checkSelfPermission(activityPluginBinding.getActivity(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                    requestPermission(result, hasVideoRecord);
                else result.success(true);
            }
        } else if (call.method.equals("initCamera")) {
            boolean hasBarcodeReader = call.argument("hasBarcodeReader");
            char flashMode = call.argument("flashMode").toString().charAt(0);
            boolean isFillScale = call.argument("isFillScale");
            int barcodeMode = call.argument("barcodeMode");
            getCameraView().initCamera(hasBarcodeReader, flashMode, isFillScale, barcodeMode);
        } else if (call.method.equals("resumeCamera")) {
            getCameraView().resumeCamera();

        } else if (call.method.equals("pauseCamera")) {
            getCameraView().pauseCamera();
        } else if (call.method.equals("takePicture")) {
            getCameraView().takePicture(result);
        } else if (call.method.equals("changeFlashMode")) {
            char captureFlashMode = call.argument("flashMode").toString().charAt(0);
            getCameraView().changeFlashMode(captureFlashMode);
        } else if (call.method.equals("dispose")) {
            dispose();
        } else if (call.method.equals("setCameraVisible")) {
            boolean isCameraVisible = call.argument("isCameraVisible");
            getCameraView().setCameraVisible(isCameraVisible);
        } else if (call.method.equals("startVideoRecord")) {
            getCameraView().startVideoRecord(call.argument("filePath").toString());
        } else if (call.method.equals("stopVideoRecord")) {
            getCameraView().stopVideoRecord(result);
        } else {
            result.notImplemented();
        }
    }

    private void requestPermission(final MethodChannel.Result result, boolean hasVideoRecord) {
        ActivityCompat.requestPermissions(activityPluginBinding.getActivity()
                , !hasVideoRecord ? new String[]{Manifest.permission.CAMERA} : new String[]{Manifest.permission.CAMERA
                        , Manifest.permission.RECORD_AUDIO}, REQUEST_CAMERA_PERMISSION);
        activityPluginBinding.addRequestPermissionsResultListener(new PluginRegistry.RequestPermissionsResultListener() {
            @Override
            public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
                for (int i :
                        grantResults) {
                    if (i == PackageManager.PERMISSION_DENIED) {
                        try {
                            result.success(false);
                        } catch (Exception e) {

                        }
                        return false;
                    }
                }
                result.success(true);
                return false;
            }
        });
    }

    private CameraView2 getCameraView() {
        return cameraView;
    }

    public CameraKitFlutterView(ActivityPluginBinding activityPluginBinding, DartExecutor dartExecutor, int viewId) {
        this.channel = new MethodChannel(dartExecutor, "plugins/camera_kit_" + viewId);

        this.activityPluginBinding = activityPluginBinding;

        this.channel.setMethodCallHandler(this);
        if (getCameraView() == null) {
            cameraView = new CameraView2(activityPluginBinding.getActivity(), this);
        }
    }

    @Override
    public View getView() {
        return cameraView.getView();
    }

    @Override
    public void dispose() {
        if (cameraView != null) {
            cameraView.dispose();
        }
    }

    @Override
    public void onBarcodeRead(String barcode) {
        channel.invokeMethod("onBarcodeRead", barcode);
    }

    @Override
    public void onTakePicture(final MethodChannel.Result result, final String filePath) {
        activityPluginBinding.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                result.success(filePath);
            }
        });
    }

    @Override
    public void onTakePictureFailed(final MethodChannel.Result result, final String errorCode, final String errorMessage) {
        activityPluginBinding.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                result.error(errorCode, errorMessage, null);
            }
        });
    }
}
