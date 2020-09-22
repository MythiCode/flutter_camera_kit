package com.MythiCode.camerakit;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.Camera;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;

import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetector;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions;

import java.io.IOException;
import java.util.List;

import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.platform.PlatformView;

import static android.graphics.PixelFormat.*;
import static com.MythiCode.camerakit.Orientation.getSupportedRotation;
import static com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode.FORMAT_ALL_FORMATS;

public class CameraView1 implements PlatformView, SurfaceHolder.Callback {

    private static final int MAX_SAMPLE_SIZE = 8;
    private static final float MAX_SCREEN_RATIO = 16 / 9f;

    private final FirebaseVisionBarcodeDetectorOptions options;
    private final FirebaseVisionBarcodeDetector detector;
    private  LinearLayout linearLayout;
    private Activity activity;
    private FlutterMethodListener flutterMethodListener;
    private SurfaceHolder mHolder;
    private Camera mCamera;
    private boolean hasBarcodeReader;
    private char previewFlashMode;
    private SurfaceView surfaceView;
    int currentRotation = 0;
    private Camera.Parameters parameters;

    public CameraView1(Activity activity, FlutterMethodListener flutterMethodListener){
        FirebaseApp.initializeApp(activity);
        this.activity = activity;
        this.flutterMethodListener = flutterMethodListener;
        options = new FirebaseVisionBarcodeDetectorOptions.Builder()
                .setBarcodeFormats(
                        FORMAT_ALL_FORMATS
                )
                .build();
        detector = FirebaseVision.getInstance().getVisionBarcodeDetector(options);
        if (linearLayout == null) {
            linearLayout = new LinearLayout(activity);
            linearLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT));
            linearLayout.setBackgroundColor(Color.parseColor("#000000"));
        }


    }

    public void initCamera(boolean hasTakePicture, boolean hasBarcodeReader, char flashMode) {
        this.hasBarcodeReader = hasBarcodeReader;
        this.previewFlashMode = flashMode;
        surfaceView = new SurfaceView(activity);
        surfaceView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        mHolder = surfaceView.getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        int height = convertDeviceHeightToSupportedAspectRatio(linearLayout.getWidth(), linearLayout.getHeight());
//        surfaceView.layout(0, 0, linearLayout.getWidth(), 1440);
        linearLayout.addView(surfaceView);
    }

    public static int convertDeviceHeightToSupportedAspectRatio(float actualWidth, float actualHeight) {
        return (int) (actualHeight / actualWidth > MAX_SCREEN_RATIO ? actualWidth * MAX_SCREEN_RATIO : actualHeight);
    }


    @Override
    public View getView() {
        return linearLayout;
    }

    @Override
    public void dispose() {

    }

    private static Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.15;
        double targetRatio = (double) h / w;
        if (sizes == null) return null;
        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - h) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - h);
            }
        }
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - h) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - h);
                }
            }
        }
        return optimalSize;
    }


    private void setCameraView() {

        connectHolder();
//        createOrientationListener();
    }
    private  void initCameraView() {
        if (mCamera != null) {
            releaseCamera();
        }
        try {
            mCamera = Camera.open();
            updateCameraSize();
            setCameraRotation(currentRotation, true);
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
//        setBarcodeScanner();
    }
    private void setCameraRotation(int rotation, boolean force) {
        if (mCamera == null) return;
        int supportedRotation = getSupportedRotation(rotation);
        if (supportedRotation == currentRotation && !force) return;
        currentRotation = supportedRotation;

//        if (cameraReleased.get()) return;
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setRotation(supportedRotation);
        parameters.setPictureFormat(JPEG);
        mCamera.setDisplayOrientation(Orientation.getDeviceOrientation(activity));
        mCamera.setParameters(parameters);
    }



    private void releaseCamera() {
        mCamera.setOneShotPreviewCallback(null);
        mCamera.release();
    }

    private  void connectHolder() {

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (mCamera == null) {
                    initCameraView();
                }


                linearLayout.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            mCamera.stopPreview();
                            mCamera.setPreviewDisplay(surfaceView.getHolder());
                            mCamera.startPreview();
                            if (hasBarcodeReader) {
//                                getOptimalPreviewSize().setOneShotPreviewCallback(null);
                            }
//                            cameraViews.peek().setSurfaceBgColor(Color.TRANSPARENT);
//                            cameraViews.peek().showFrame();
                        } catch (IOException | RuntimeException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }).start();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        setCameraView();

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        setCameraView();
    }

    private  void updateCameraSize() {
        try {


            WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            int y = convertDeviceHeightToSupportedAspectRatio(linearLayout.getWidth(), linearLayout.getHeight());
            if (mCamera == null) return;
            List<Camera.Size> supportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
            List<Camera.Size> supportedPictureSizes = mCamera.getParameters().getSupportedPictureSizes();
            Camera.Size optimalSize = getOptimalPreviewSize(supportedPreviewSizes, linearLayout.getWidth(), y);
            Camera.Size optimalPictureSize = getOptimalPreviewSize(supportedPictureSizes, linearLayout.getHeight(), y);
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            parameters.setPreviewSize(optimalSize.width, optimalSize.height);
            parameters.setPictureSize(optimalPictureSize.width, optimalPictureSize.height);
//            parameters.setFlashMode(flashMode);
            mCamera.setParameters(parameters);
        } catch (RuntimeException ignored) {
        }
    }


    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    public void resumeCamera() {

    }

    public void pauseCamera() {

    }

    public void takePicture(MethodChannel.Result result, char captureFlashMode) {

    }

    public void changeFlashMode(char captureFlashMode) {

    }

    public void setCameraVisible(boolean isCameraVisible) {

    }
}
