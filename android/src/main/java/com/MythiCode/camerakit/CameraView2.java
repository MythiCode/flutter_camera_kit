package com.MythiCode.camerakit;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import io.flutter.plugin.common.MethodChannel;

import static android.content.ContentValues.TAG;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class CameraView2 implements CameraViewInterface, ImageReader.OnImageAvailableListener {

    private static final int MSG_CAPTURE_PICTURE_WHEN_FOCUS_TIMEOUT = 100;

    private BarcodeScannerOptions options;
    private int mState = STATE_PREVIEW;
    private Activity activity;
    private Semaphore cameraOpenCloseLock = new Semaphore(1);
    private String cameraId;
    private CameraDevice cameraDevice;
    private AutoFitTextureView textureView;
    private CaptureRequest.Builder captureRequestBuilder;
    private CameraCaptureSession cameraCaptureSessions;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private ImageReader imageReader;
    private HandlerThread backgroundThread2;
    private Handler backgroundHandler2;
    private FrameLayout frameLayout;
    private CameraCharacteristics characteristics;
    private StreamConfigurationMap map;
    private Integer sensorOrientation;
    private Size previewSize;

    private boolean flashSupported;
    private Surface surface;


    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_LOCK = 1;

    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * Camera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;


    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private static SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }


    private CameraCaptureSession.CaptureCallback captureCallbackBarcodeReader
            = new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            if (previewFlashMode == 'A') {
                Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                if (aeState != null) {
                    if (currentPreviewFlashMode != 'O' && aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        setFlashMode(captureRequestBuilder, 'O');
                        setRepeatingRequestAfterSetFlash();
                    }
//                    } else if (currentPreviewFlashMode == 'O' && aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
//                        setFlashMode(captureRequestBuilder, 'F');
//                        setRepeatingRequestAfterSetFlash();
//                    }
                }
            }

        }

    };


    private CameraCaptureSession.CaptureCallback captureCallbackTakePicture
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };


    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            System.out.println("CameraView Lif Cycle Available " + CameraView2.this.toString());
            isDestroy = false;
            if (isCameraVisible) openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform you image captured size according to the surface width and height

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            System.out.println("CameraView Lif Cycle Destroy " + CameraView2.this.toString());
            isDestroy = true;
            if (!isCameraVisible) {
                surface.release();
                textureView.setSurfaceTextureListener(null);
            }
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };


    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraOpenCloseLock.release();
            //This is called when the camera is open
            Log.e(TAG, "onOpened");
            cameraDevice = camera;
            if (!isDestroy) createCameraPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraOpenCloseLock.release();
            cameraDevice.close();
            cameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            cameraOpenCloseLock.release();
            cameraDevice.close();
            cameraDevice = null;
        }
    };
    private FlutterMethodListener flutterMethodListener;
    private boolean hasBarcodeReader;
    private char previewFlashMode;
    private char currentPreviewFlashMode;
    private int firebaseOrientation;
    private boolean isCameraVisible = true;
    private boolean isDestroy;
    private ImageReader readerCapture;
    private MethodChannel.Result resultMethodChannel;
    private CaptureRequest mainPreviewRequest;
    private TakePictureImageListener takePictureImageListener;
    private Point displaySize;
    private boolean isReadyForTakingPicture = false;
    private BarcodeScanner scanner;
    private int cameraSelector;
    private String imageSavePath;


    public CameraView2(Activity activity, FlutterMethodListener flutterMethodListener) {
//        FirebaseApp.initializeApp(activity);
        this.activity = activity;
        this.flutterMethodListener = flutterMethodListener;
    }

    public void initCamera(FrameLayout frameLayout, boolean hasBarcodeReader, char flashMode, boolean isFillScale, int barcodeMode, int cameraSelector) {
        this.cameraSelector = cameraSelector;
        startBackgroundThread();
        this.frameLayout = frameLayout;
        this.hasBarcodeReader = hasBarcodeReader;
        this.previewFlashMode = flashMode;

        if (hasBarcodeReader) {

            options = new BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(
                            barcodeMode
                    )
                    .build();
            scanner = BarcodeScanning.getClient(options);
        }
        displaySize = new Point();
        activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
        if (isFillScale == true) //fill
            this.frameLayout.setLayoutParams(new FrameLayout.LayoutParams(
                    displaySize.x,
                    displaySize.y));

        textureView = new AutoFitTextureView(activity);
        textureView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        textureView.setSurfaceTextureListener(textureListener);
        this.frameLayout.addView(textureView);
    }


    private int getOrientation() {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        return (ORIENTATIONS.get(rotation) + sensorOrientation + 270) % 360;
    }

    private void setUpCameraOutputs(int width, int height) {
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                characteristics = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                flashSupported = available == null ? false : available;
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    if (cameraSelector == 0)
                        continue;
                } else {
                    if (cameraSelector == 1)
                        continue;
                }
                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }
                // For still image captures, we use the largest available size.
                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByArea());


                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                //noinspection ConstantConditions
                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (sensorOrientation == 90 || sensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (sensorOrientation == 0 || sensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                }


                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, largest);

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                int orientation = activity.getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView.setAspectRatio(
                            previewSize.getWidth(), previewSize.getHeight());

                } else {
                    textureView.setAspectRatio(
                            previewSize.getHeight(), previewSize.getWidth());
                }

                this.cameraId = cameraId;

                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            e.printStackTrace();
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {

        if (null == textureView || null == previewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / previewSize.getHeight(),
                    (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        textureView.setTransform(matrix);
    }

    private void openCamera() {

        setUpCameraOutputs(textureView.getWidth(), textureView.getHeight());
        configureTransform(textureView.getWidth(), textureView.getHeight());

        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG, "is camera open");
        try {

            map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            // Add permission for camera and let user grant the permission
            try {
                if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                    //throw new RuntimeException("Time out waiting to lock camera opening.");
                }
                manager.openCamera(cameraId, stateCallback, null);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.e(TAG, "openCamera X");
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("Camera Background");
        backgroundThread.start();
        backgroundThread2 = new HandlerThread("Camera Background2");
        backgroundThread2.start();
        backgroundHandler = new Handler(backgroundThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case MSG_CAPTURE_PICTURE_WHEN_FOCUS_TIMEOUT:
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                        break;
                    default:
                        break;
                }

            }
        };
        backgroundHandler2 = new Handler(backgroundThread2.getLooper());
    }

    private void stopBackgroundThread() {
        try {
            if (backgroundThread != null) {
                backgroundThread.quitSafely();
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            }
            if (backgroundThread2 != null) {
                backgroundThread2.quitSafely();
                backgroundThread2.join();
                backgroundThread2 = null;
                backgroundHandler2 = null;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void resumeCamera() {

        startBackgroundThread();
        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
//        textureView.setVisibility(View.VISIBLE);
        if (isCameraVisible) {
            if (textureView != null) {
                if (textureView.isAvailable()) {
                    isDestroy = false;
                    openCamera();
                } else {
                    textureView.setSurfaceTextureListener(textureListener);
                }
            }

            if (scanner == null && hasBarcodeReader) {
                scanner = BarcodeScanning.getClient(options);
            }
        }
    }

    public void pauseCamera() {
        System.out.println("CameraView Lif Cycle pause: " + this.toString());

        closeCamera();
        stopBackgroundThread();
    }

    private void closeCamera() {
        try {
            cameraOpenCloseLock.acquire();
        } catch (InterruptedException e) {
            System.out.println("Interrupted while trying to lock camera closing: " + e.getMessage());
        } finally {
            cameraOpenCloseLock.release();
        }

        if (null != cameraCaptureSessions) {
            cameraCaptureSessions.close();
            cameraCaptureSessions = null;
        }
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            BarcodeDetector.setImageReader(null);
            imageReader = null;
        }
        if (null != readerCapture) {
            readerCapture.close();
            readerCapture = null;
        }
        try {
            if (scanner != null) {
                scanner.close();
                scanner = null;
            }

        } catch (Exception e) {
            System.out.println("Error to closing detector: " + e.getMessage());
        }
    }

    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
//        int w = aspectRatio.getWidth();
//        int h = aspectRatio.getHeight();

        int w = 16;
        int h = 9;
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    public void changeFlashMode(char previewFlashMode) {
        this.previewFlashMode = previewFlashMode;
        setFlashMode(captureRequestBuilder, previewFlashMode);
        setRepeatingRequestAfterSetFlash();
    }

    private void setRepeatingRequestAfterSetFlash() {
        try {
            if (hasBarcodeReader)
                cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), captureCallbackBarcodeReader, backgroundHandler);
            else
                cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), captureCallbackTakePicture, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    public void setCameraVisible(boolean isCameraVisible) {
        if (isCameraVisible != this.isCameraVisible) {
            this.isCameraVisible = isCameraVisible;
            if (isCameraVisible) resumeCamera();
            else pauseCamera();
        }
    }


    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    private boolean checkAutoFocusSupported() {
        int[] modes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
        return !(modes == null || modes.length == 0 ||
                (modes.length == 1 && modes[0] == CameraCharacteristics.CONTROL_AF_MODE_OFF));
    }

    private void setFlashMode(CaptureRequest.Builder previewRequestBuilder, char flashMode) {
        currentPreviewFlashMode = flashMode;
        if (flashSupported) {

            switch (flashMode) {
                case 'A':
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                    previewRequestBuilder.set(CaptureRequest.FLASH_MODE,
                            CaptureRequest.FLASH_MODE_OFF);
                    break;
                case 'O':
                    if (!hasBarcodeReader) {
                        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                        previewRequestBuilder.set(CaptureRequest.FLASH_MODE,
                                CaptureRequest.FLASH_MODE_OFF);
                    } else {
                        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                CaptureRequest.CONTROL_AE_MODE_ON);
                        previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                    }
                    break;
                case 'F':
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                    previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
            }
        }
    }

    private void setRepeatingRequest() {
        try {
            mainPreviewRequest = captureRequestBuilder.build();
            if (hasBarcodeReader)
                cameraCaptureSessions.setRepeatingRequest(mainPreviewRequest, captureCallbackBarcodeReader, backgroundHandler);
            else {
                cameraCaptureSessions.setRepeatingRequest(mainPreviewRequest, captureCallbackTakePicture, backgroundHandler);
                isReadyForTakingPicture = true;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            surface = new Surface(texture);
            if (hasBarcodeReader) {

                captureRequestBuilder = cameraDevice
                        .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            } else {
                captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            }
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            setFlashMode(captureRequestBuilder, previewFlashMode);
            captureRequestBuilder.addTarget(surface);
            initialImageReader();


            cameraDevice.createCaptureSession(getSurfaceList(), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    if (null == cameraDevice) {
                        Log.e(TAG, "updatePreview error, return");
                    }
                    setRepeatingRequest();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(activity, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private List<Surface> getSurfaceList() {
        readerCapture = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.JPEG, 1);
        readerCapture.setOnImageAvailableListener(takePictureImageListener, backgroundHandler);
        if (hasBarcodeReader)
            return Arrays.asList(surface, imageReader.getSurface());
        else return Arrays.asList(surface, readerCapture.getSurface());
    }

//    private int getFirebaseOrientation() {
//        int rotationCompensation = getOrientation();
//
//        // Return the corresponding FirebaseVisionImageMetadata rotation value.
//        int result;
//        switch (rotationCompensation) {
//            case 0:
//                result = FirebaseVisionImageMetadata.ROTATION_0;
//                break;
//            case 90:
//                result = FirebaseVisionImageMetadata.ROTATION_90;
//                break;
//            case 180:
//                result = FirebaseVisionImageMetadata.ROTATION_180;
//                break;
//            case 270:
//                result = FirebaseVisionImageMetadata.ROTATION_270;
//                break;
//            default:
//                result = FirebaseVisionImageMetadata.ROTATION_0;
//                Log.e(TAG, "Bad rotation value: " + rotationCompensation);
//        }
//        return result;
//    }

    private void initialImageReader() {
        if (hasBarcodeReader) {
//            Size m = Collections.max(
//                    Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888)),
//                    new CompareSizesByArea());
            firebaseOrientation = getJpegOrientation();
            imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(),
                    ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(
                    this, backgroundHandler2);
            captureRequestBuilder.addTarget(imageReader.getSurface());
            BarcodeDetector.setImageReader(imageReader);

        }
    }


    public void dispose() {
        closeCamera();
        stopBackgroundThread();
        frameLayout.removeAllViews();
        frameLayout.buildLayer();
        frameLayout = null;
        textureView = null;
    }

    private static byte[] convertImageToByte(Image image) {
        byte[] nv21;
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        nv21 = new byte[ySize + uSize + vSize];

        //U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        return nv21;
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        final Image image = reader.acquireLatestImage();

        if (image != null) {
            try {
                BarcodeDetector.detectImage(imageReader, scanner, image, flutterMethodListener, firebaseOrientation);
            } catch (IllegalStateException e) {

            } catch (OutOfMemoryError e) {
                System.gc();
                //Sometimes out of memory error occurred, ignore it
            } finally {

            }

        }
    }

    private int getJpegOrientation() {
        int deviceOrientation = activity.getWindowManager().getDefaultDisplay().getRotation();
        if (deviceOrientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN)
            return 0;
        int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

        // Round device orientation to a multiple of 90
        deviceOrientation = (deviceOrientation + 45) / 90 * 90;

        // Reverse device orientation for front-facing cameras
        boolean facingFront = characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT;
        if (facingFront) deviceOrientation = -deviceOrientation;

        // Calculate desired JPEG orientation relative to camera orientation to make
        // the image upright relative to the device orientation
        int jpegOrientation = (sensorOrientation + deviceOrientation + 360) % 360;

        return jpegOrientation;
    }


    public void takePicture(String path, final MethodChannel.Result resultMethodChannel) {
        imageSavePath = path;
        if (isReadyForTakingPicture) {
            this.resultMethodChannel = resultMethodChannel;
            if (checkAutoFocusSupported()) {
                capturePictureWhenFocusTimeout();
                lockFocus();
            } else {
                captureStillPicture();
            }
        }
    }

    private void capturePictureWhenFocusTimeout() {
        if (backgroundHandler != null) {
            backgroundHandler.sendEmptyMessageDelayed(MSG_CAPTURE_PICTURE_WHEN_FOCUS_TIMEOUT,
                    800);
        }
    }

    private void lockFocus() {
        try {
            // This is how to tell the camera to lock focus.

            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);

            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK;
            cameraCaptureSessions.capture(captureRequestBuilder.build(), captureCallbackTakePicture,
                    backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #captureCallbackTakePicture} from {@link #lockFocus()}.
     */
    private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            cameraCaptureSessions.capture(captureRequestBuilder.build(), captureCallbackTakePicture,
                    backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    CameraCaptureSession.CaptureCallback CaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
            super.onCaptureSequenceAborted(session, sequenceId);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
//            showToast("onCaptureCompleted: ");
//                    Log.d(TAG, mFile.toString());
            unlockFocus();
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            flutterMethodListener.onTakePictureFailed(resultMethodChannel, "-101", "Capture Failed");
        }
    };

    private File getPictureFile() {
        if (imageSavePath.equals("")) {
            return new File(activity.getCacheDir(), "pic.jpg");
        } else return new File(imageSavePath);
    }


    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #captureCallbackTakePicture} from both {@link #lockFocus()}.
     */
    private void captureStillPicture() {
        try {
            removeCaptureMessage();
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);


            takePictureImageListener = new TakePictureImageListener(flutterMethodListener, resultMethodChannel, getPictureFile());
            readerCapture.setOnImageAvailableListener(takePictureImageListener, backgroundHandler);
            captureBuilder.addTarget(readerCapture.getSurface());

            // Use the same AE and AF modes as the preview.
//            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
//                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
//            if(captureFlashMode == 'A'){
//                if(aeState != null) {
//                    if(aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED)
//                        captureFlashMode = 'O';
//                }
//            }
            setFlashMode(captureBuilder, previewFlashMode);
//            if(aeState == null)
//                setFlashMode(captureBuilder, captureFlashMode);
//            else if(aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED){
//                setFlashMode(captureBuilder, 'O');
//            }


            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getJpegOrientation());


            cameraCaptureSessions.stopRepeating();
//            cameraCaptureSessions.abortCaptures();
            cameraCaptureSessions.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void removeCaptureMessage() {
        if (backgroundHandler != null) {
            backgroundHandler.removeMessages(MSG_CAPTURE_PICTURE_WHEN_FOCUS_TIMEOUT);
        }
    }

    private void showToast(final String s) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity, s, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void unlockFocus() {
        try {
            cameraCaptureSessions.abortCaptures();
            mState = STATE_PREVIEW;
            // Reset the auto-focus trigger
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            cameraCaptureSessions.capture(captureRequestBuilder.build(), captureCallbackTakePicture,
                    backgroundHandler);

            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);


            cameraCaptureSessions.setRepeatingRequest(mainPreviewRequest, captureCallbackTakePicture,
                    backgroundHandler);

            setFlashMode(captureRequestBuilder, previewFlashMode);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
