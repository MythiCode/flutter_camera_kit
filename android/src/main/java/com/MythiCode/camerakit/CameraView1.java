package com.MythiCode.camerakit;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.hardware.Camera;
import android.media.ExifInterface;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;

import com.google.firebase.FirebaseApp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.platform.PlatformView;

import static android.graphics.PixelFormat.JPEG;
import static android.hardware.Camera.Parameters.FLASH_MODE_AUTO;
import static android.hardware.Camera.Parameters.FLASH_MODE_OFF;
import static android.hardware.Camera.Parameters.FLASH_MODE_ON;
import static com.MythiCode.camerakit.Orientation.getSupportedRotation;

public class CameraView1 implements SurfaceHolder.Callback, CameraViewInterface {

    private static final float MAX_SCREEN_RATIO = 16 / 9f;


    private Activity activity;
    private FlutterMethodListener flutterMethodListener;
    private SurfaceHolder mHolder;
    private Camera mCamera;
    private LinearLayout linearLayout;
    //    private boolean hasBarcodeReader;
    private char previewFlashMode;
    private SurfaceView surfaceView;
    int currentRotation = 0;
    private boolean isCameraVisible = true;

    public CameraView1(Activity activity, FlutterMethodListener flutterMethodListener) {
        FirebaseApp.initializeApp(activity);
        this.activity = activity;
        this.flutterMethodListener = flutterMethodListener;



    }

    public void initCamera(LinearLayout linearLayout, boolean hasBarcodeReader, char flashMode, boolean isFillScale, int barcodeMode) {
        this.linearLayout = linearLayout;
        this.previewFlashMode = flashMode;
        surfaceView = new SurfaceView(activity);
        surfaceView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        mHolder = surfaceView.getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        int height = convertDeviceHeightToSupportedAspectRatio(linearLayout.getWidth(), linearLayout.getHeight());
        linearLayout.addView(surfaceView);
    }

    public static int convertDeviceHeightToSupportedAspectRatio(float actualWidth, float actualHeight) {
        return (int) (actualHeight / actualWidth > MAX_SCREEN_RATIO ? actualWidth * MAX_SCREEN_RATIO : actualHeight);
    }




    private static Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
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

    private void initCameraView() {
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
        parameters.setFlashMode(getFlashMode());
        mCamera.setDisplayOrientation(Orientation.getDeviceOrientation(activity));
        mCamera.setParameters(parameters);
    }

    private String getFlashMode() {
        switch (previewFlashMode) {
            case 'A':
                return FLASH_MODE_AUTO;
            case 'O':
                return FLASH_MODE_ON;
            case 'F':
                return FLASH_MODE_OFF;
            default:
                return FLASH_MODE_OFF;
        }
    }


    private void releaseCamera() {
        mCamera.setOneShotPreviewCallback(null);
        mCamera.release();
    }

    private void connectHolder() {

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
//                            if (hasBarcodeReader) {
////                                getOptimalPreviewSize().setOneShotPreviewCallback(null);
//                            }
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
        // Set focus mode to continuous picture
        if(mCamera!= null) {

            mCamera.startPreview();
        }
    }

    private void updateCameraSize() {
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
//            Camera.Size optimalPictureSize = getOptimalPreviewSize(supportedPictureSizes, linearLayout.getWidth(), y);
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            parameters.setPreviewSize(optimalSize.width, optimalSize.height);
            parameters.setPictureSize(optimalSize.width, optimalSize.height);
//            parameters.setFlashMode(flashMode);
            mCamera.setParameters(parameters);
        } catch (RuntimeException ignored) {
        }
    }


    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }
    @Override
    public void resumeCamera() {
        connectHolder();
    }

    @Override
    public void dispose() {

    }

    public void pauseCamera() {
        releaseCamera();
        mCamera = null;
    }

    private static Bitmap rotate(Bitmap bitmap, int degree) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        Matrix mtx = new Matrix();
        //       mtx.postRotate(degree);
        mtx.setRotate(degree);

        return Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, true);
    }
    @Override
    public void takePicture(final MethodChannel.Result result) {

        mCamera.takePicture(null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                File pictureFile = new File(activity.getCacheDir(), "pic.jpg");
                try {

                    Bitmap realImage = BitmapFactory.decodeByteArray(data, 0, data.length);
                    FileOutputStream fos = new FileOutputStream(pictureFile);

                    realImage= rotate(realImage, Orientation.getDeviceOrientation(activity));
                    realImage.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                    fos.close();
                    flutterMethodListener.onTakePicture(result, pictureFile + "");
                } catch (FileNotFoundException e) {
                    flutterMethodListener.onTakePictureFailed(result, "-101", "File not found");
                    // Log.d(TAG, "File not found: " + e.getMessage());
                } catch (IOException e) {
                    flutterMethodListener.onTakePictureFailed(result, "-102", e.getMessage());
                    //Log.d(TAG, "Error accessing file: " + e.getMessage());
                }
            }
        });
    }
    @Override
    public void changeFlashMode(char captureFlashMode) {
        previewFlashMode = captureFlashMode;
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setFlashMode(getFlashMode());
        mCamera.setParameters(parameters);
    }
    @Override
    public void setCameraVisible(boolean isCameraVisible) {
        if (isCameraVisible != this.isCameraVisible) {
            if (isCameraVisible) resumeCamera();
            else pauseCamera();
            this.isCameraVisible = isCameraVisible;
        }
    }
}
