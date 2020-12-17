package com.MythiCode.camerakit;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
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

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import io.flutter.plugin.common.MethodChannel;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class CameraView2_V2 implements CameraViewInterface {


    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;

    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();

    private static final SparseIntArray INTERNAL_FACINGS = new SparseIntArray();

    static {
        INTERNAL_FACINGS.put(CameraConstants.FACING_BACK, CameraCharacteristics.LENS_FACING_BACK);
        INTERNAL_FACINGS.put(CameraConstants.FACING_FRONT, CameraCharacteristics.LENS_FACING_FRONT);
    }

    private static final int REQUEST_CAMERA_PERMISSION = 1;

    private static final String FRAGMENT_DIALOG = "dialog";

    private static final String[] VIDEO_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
    };

    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "Camera2Fragment";

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


    private static final int MSG_CAPTURE_PICTURE_WHEN_FOCUS_TIMEOUT = 100;

    private final Activity activity;
    private final FlutterMethodListener flutterMethodListener;
    private FrameLayout frameLayout;
    private boolean hasBarcodeReader;
    private char previewFlashMode;
    private BarcodeScannerOptions options;
    private BarcodeScanner scanner;
    private Point displaySize;
    private AutoFitTextureView textureView;
    private FocusView focusView;
    private boolean isCameraVisible = true;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private final SizeMap mPreviewSizes = new SizeMap();


    private int mState = STATE_PREVIEW;


    private int mFacing = CameraConstants.FACING_BACK;

    private AspectRatio mAspectRatio = CameraConstants.DEFAULT_ASPECT_RATIO;

    private boolean mFacingSupported = true;
    private CameraCharacteristics mCameraCharacteristics;
    private Integer mSensorOrientation;
    private android.util.Size mPreviewSize;
    private ImageReader mImageReader;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private String mNextPictureAbsolutePath;
    private boolean mAutoFocusSupported;
    private boolean mFlashSupported;
    private String mCameraId;
    private Rect mCropRegion;
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CameraCaptureSession mPreviewSession;
    private boolean mAutoFocus = true;
    private MeteringRectangle[] mAFRegions;
    private MeteringRectangle[] mAERegions;
    private CaptureRequest mPreviewRequest;
    private MethodChannel.Result resultMethodChannelTakePicture;


    public CameraView2_V2(Activity activity, FlutterMethodListener flutterMethodListener) {
//        FirebaseApp.initializeApp(activity);
        this.activity = activity;
        this.flutterMethodListener = flutterMethodListener;
    }

    @Override
    public void initCamera(FrameLayout linearLayout, boolean hasBarcodeReader, char flashMode, boolean isFillScale, int barcodeMode, int cameraSelector) {
        this.frameLayout = linearLayout;
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
            linearLayout.setLayoutParams(new FrameLayout.LayoutParams(
                    displaySize.x,
                    displaySize.y));

        textureView = new AutoFitTextureView(activity);
        textureView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        start();

//        focusView = new FocusView(activity);
//        focusView.setLayoutParams(new FrameLayout.LayoutParams(
//                100,
//                100));
        linearLayout.addView(textureView);


    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper()) {
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
    }

    private final TextureView.SurfaceTextureListener surfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            if (texture != null) {
                textureView.setSurfaceTextureListener(null);

            }
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };

    private void configureTransform(int viewWidth, int viewHeight) {

        if (null == textureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        textureView.setTransform(matrix);
    }

    public void start() {
        startBackgroundThread();

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    public void stop() {
        closeCamera();
        stopBackgroundThread();
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mPreviewSession) {
                mPreviewSession.close();
                mPreviewSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
//            if (null != mMediaRecorder) {
//                mMediaRecorder.release();
//                mMediaRecorder = null;
//            }
            if (null != mImageReader) {
                mImageReader.close();
                BarcodeDetector.setImageReader(null);
                mImageReader = null;
            }
            try {
                if (scanner != null) {
                    scanner.close();
                    scanner = null;
                }

            } catch (Exception e) {
                System.out.println("Error to closing detector: " + e.getMessage());
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }


    private void openCamera(int width, int height) {

        setupCameraOutputs(width, height);
        configureTransform(width, height);
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(CameraConstants.OPEN_CAMERA_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
//            mMediaRecorder = new MediaRecorder();
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    void updateFlash(CaptureRequest.Builder requestBuilder) {
        if (!mFlashSupported) {
            return;
        }
        switch (previewFlashMode) {
            case 'F':
                requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON);
                requestBuilder.set(CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_OFF);
                break;
            case 'O':
                if(hasBarcodeReader) {
                    requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON);
                    requestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                }
                else {
                    requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                    requestBuilder.set(CaptureRequest.FLASH_MODE,
                            CaptureRequest.FLASH_MODE_OFF);
                }
                break;
            case 'N':
                requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON);
                requestBuilder.set(CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_TORCH);
                break;
            case 'A':
                requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                requestBuilder.set(CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_OFF);
                break;
            case 'M':
                requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE);
                requestBuilder.set(CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_OFF);
                break;
        }
    }

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);
            if(hasBarcodeReader)
                mPreviewRequestBuilder.addTarget(mImageReader.getSurface());

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mPreviewSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
//                            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
//                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                updateAutoFocus();
                                // Flash is automatically enabled when necessary.
                                updateFlash(mPreviewRequestBuilder);

                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mPreviewSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
//                            showToast("Create preview configure failed");
                        }
                    }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            if (null != activity) {
//                showToast("Camera is error: " + error);
//                activity.finish();
            }
        }

    };

    private void removeCaptureMessage() {
        if (mBackgroundHandler != null) {
            mBackgroundHandler.removeMessages(MSG_CAPTURE_PICTURE_WHEN_FOCUS_TIMEOUT);
        }
    }


    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from DEFAULT_ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (DEFAULT_ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    private void captureStillPicture() {
        try {
            removeCaptureMessage();
            if (null == activity || null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // Use the same AE and AF modes as the preview.
//            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
//                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
//            updateAutoFocus();
            updateFlash(captureBuilder);

            // Orientation
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    unlockFocus();
                }
            };

            mPreviewSession.stopRepeating();
            mPreviewSession.capture(captureBuilder.build(), CaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            mPreviewSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mPreviewSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);

            updateAutoFocus();
            updateFlash(mPreviewRequestBuilder);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            mPreviewSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            //  Log.i(TAG, "CaptureCallback mState: " + mState);
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    Log.i(TAG, "STATE_WAITING_LOCK afState: " + afState);
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
            if (hasBarcodeReader) {
                if (previewFlashMode == 'A') {
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState != null) {
                        if (aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED) {
                            changeFlashMode('O');
//                            updateFlash(mPreviewRequestBuilder);
                        }
                    }
                }
            } else {
                process(result);
            }
        }

    };

    private void lockFocus() {
        try {
            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK;
            mPreviewSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setCameraVisible(boolean isCameraVisible) {
        if (isCameraVisible != this.isCameraVisible) {
            this.isCameraVisible = isCameraVisible;
            if (isCameraVisible) resumeCamera();
            else pauseCamera();
        }
    }

    @Override
    public void changeFlashMode(char newPreviewFlashMode) {
        if (previewFlashMode == newPreviewFlashMode) {
            return;
        }
        char saved = previewFlashMode;
        previewFlashMode = newPreviewFlashMode;
        if (mPreviewRequestBuilder != null) {
            updateFlash(mPreviewRequestBuilder);
            if (mPreviewSession != null) {
                try {
                    mPreviewSession.setRepeatingRequest(mPreviewRequestBuilder.build(),
                            mCaptureCallback, mBackgroundHandler);
                } catch (CameraAccessException e) {
                    previewFlashMode = saved; // Revert
                }
            }
        }
    }

    private void capturePictureWhenFocusTimeout() {
        if (mBackgroundHandler != null) {
            mBackgroundHandler.sendEmptyMessageDelayed(MSG_CAPTURE_PICTURE_WHEN_FOCUS_TIMEOUT,
                    CameraConstants.AUTO_FOCUS_TIMEOUT_MS);
        }
    }

    @Override
    public void takePicture(MethodChannel.Result result) {
        resultMethodChannelTakePicture = result;
        if (mAutoFocus && mAutoFocusSupported) {
            Log.i(TAG, "takePicture lockFocus");
            capturePictureWhenFocusTimeout(); //Sometimes, camera do not focus in some devices.
            lockFocus();
        } else {
            Log.i(TAG, "takePicture captureStill");
            captureStillPicture();
        }
    }

    @Override
    public void pauseCamera() {
        stop();
    }

    @Override
    public void resumeCamera() {
        if(isCameraVisible) {
            start();
            if (scanner == null && hasBarcodeReader) {
                scanner = BarcodeScanning.getClient(options);
            }
        }
    }

    @Override
    public void dispose() {

    }

    private String getPictureFilePath(Context context) {
        final File dir = new File(activity.getCacheDir(), "pic.jpg");
        return dir == null ? "" : dir.toString();
    }

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            final Image image = reader.acquireLatestImage();
            if (hasBarcodeReader) {
                BarcodeDetector.detectImage(mImageReader, scanner, image, flutterMethodListener, mSensorOrientation);
            } else {
                mNextPictureAbsolutePath = getPictureFilePath(activity);
                mBackgroundHandler.post(new ImageSaver(flutterMethodListener, resultMethodChannelTakePicture, image, new File(mNextPictureAbsolutePath)));
                Log.i(TAG, "Picture saved: " + mNextPictureAbsolutePath);
            }
        }

    };


    android.util.Size chooseOptimalSize(android.util.Size[] choices, int textureViewWidth,
                                        int textureViewHeight, int maxWidth, int maxHeight) {
        mPreviewSizes.clear();
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<android.util.Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<android.util.Size> notBigEnough = new ArrayList<>();
        int w = mAspectRatio.getX();
        int h = mAspectRatio.getY();
        for (android.util.Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight) {
                mPreviewSizes.add(new com.MythiCode.camerakit.Size(option.getWidth(), option.getHeight()));
                if (option.getHeight() == option.getWidth() * h / w) {
                    if (option.getWidth() >= textureViewWidth &&
                            option.getHeight() >= textureViewHeight) {
                        bigEnough.add(option);
                    } else {
                        notBigEnough.add(option);
                    }
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
            mAspectRatio = AspectRatio.of(4, 3);
            SortedSet<com.MythiCode.camerakit.Size> sortedSet = mPreviewSizes.sizes(mAspectRatio);
            if (sortedSet != null) {
                com.MythiCode.camerakit.Size lastSize = sortedSet.last();
                return new Size(lastSize.getWidth(), lastSize.getHeight());
            }
            mAspectRatio = AspectRatio.of(choices[0].getWidth(), choices[0].getHeight());
            return choices[0];
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setupCameraOutputs(int width, int height) {

        int internalFacing = INTERNAL_FACINGS.get(mFacing);
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIds = manager.getCameraIdList();
            mFacingSupported = cameraIds.length > 1;
            for (String cameraId : cameraIds) {
                mCameraCharacteristics
                        = manager.getCameraCharacteristics(cameraId);

                Integer facing = mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing == null || facing != internalFacing) {
                    continue;
                }

                StreamConfigurationMap map = mCameraCharacteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                //noinspection ConstantConditions
                mSensorOrientation = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                }

                Point displaySize = new Point();
                activity.getWindowManager().getDefaultDisplay().getRealSize(displaySize);
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

                // Danger, W.R.! Attempting to use too large a preview size could exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight);

//                mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));

                // For still image captures, we use the largest available size.

                if(hasBarcodeReader) {
                    mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(),
                            ImageFormat.YUV_420_888, 2);
                    BarcodeDetector.setImageReader(mImageReader);

                } else {
//                    android.util.Size largest = choosePictureSize(map.getOutputSizes(ImageFormat.JPEG));
                    mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(),
                            ImageFormat.JPEG, /*maxImages*/2);
                }
                mImageReader.setOnImageAvailableListener(
                        mOnImageAvailableListener, mBackgroundHandler);

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                int orientation = activity.getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView.setAspectRatio(
                            mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    textureView.setAspectRatio(
                            mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                checkAutoFocusSupported();
                checkFlashSupported();

                mCropRegion = AutoFocusHelper.cropRegionForZoom(mCameraCharacteristics,
                        CameraConstants.ZOOM_REGION_DEFAULT);

                mCameraId = cameraId;
                Log.i(TAG, "CameraId: " + mCameraId + " ,isFlashSupported: " + mFlashSupported);
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
//            ErrorDialog.newInstance(getString(R.string.camera_error))
//                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
    }

    private void checkAutoFocusSupported() {
        int[] modes = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
        mAutoFocusSupported = !(modes == null || modes.length == 0 ||
                (modes.length == 1 && modes[0] == CameraCharacteristics.CONTROL_AF_MODE_OFF));
    }


    private void checkFlashSupported() {
        Boolean available = mCameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        mFlashSupported = available == null ? false : available;
    }

    android.util.Size choosePictureSize(android.util.Size[] choices) {
        List<android.util.Size> pictureSizes = Arrays.asList(choices);
        Collections.sort(pictureSizes, new CompareSizesByArea());
        int maxIndex = pictureSizes.size() - 1;
        for (int i = maxIndex; i >= 0; i--) {
            if (pictureSizes.get(i).getWidth() == pictureSizes.get(i).getHeight() *
                    mAspectRatio.getX() / mAspectRatio.getY()) {
                return pictureSizes.get(i);
            }
        }
        return pictureSizes.get(maxIndex);
    }


    private static class CompareSizesByArea implements Comparator<android.util.Size> {

        @Override
        public int compare(android.util.Size lhs, android.util.Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    void updateAutoFocus() {
        if (mAutoFocus) {
            if (!mAutoFocusSupported) {
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_OFF);
            } else {
                if (false /*mIsRecordingVideo*/) {
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                } else {
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                }
            }
        } else {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_OFF);
        }
//        mAFRegions = AutoFocusHelper.getZeroWeightRegion();
//        mAERegions = AutoFocusHelper.getZeroWeightRegion();
//        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, mAFRegions);
//        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, mAERegions);
    }


    private static class ImageSaver implements Runnable {

        private FlutterMethodListener flutterMethodListener;
        private MethodChannel.Result resultMethodChannelTakePicture;
        private final Image mImage;
        private final File mFile;

        ImageSaver(FlutterMethodListener flutterMethodListener, MethodChannel.Result resultMethodChannelTakePicture, Image image, File file) {
            this.flutterMethodListener = flutterMethodListener;
            this.resultMethodChannelTakePicture = resultMethodChannelTakePicture;
            mImage = image;
            mFile = file;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
                flutterMethodListener.onTakePictureFailed(resultMethodChannelTakePicture, "-101", "Capture Failed");
            } finally {
                mImage.close();
                flutterMethodListener.onTakePicture(resultMethodChannelTakePicture, mFile + "");
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }
}
